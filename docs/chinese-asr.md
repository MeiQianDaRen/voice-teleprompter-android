# 离线中文语音跟随

在“设置 → 语音跟随 → 默认语音识别语言”中选择“中文”，或在稿件详情中单独选择中文。选择“自动”时，会忽略标题和方括号备注，并根据正文中的汉字、拉丁字母和西里尔字母数量选择模型。

中文模型为 sherpa-onnx 的 WenetSpeech 14M 流式 Zipformer，使用 CPU 推理。编码器和连接器为 int8，解码器为 fp32，总计约 31 MB。模型在构建时下载并打包进 APK；语音识别不需要联网，网络权限用于已配置的在线稿件库。

## 从识别结果到滚动

- 模型输出汉字，不是拼音。稿件与识别结果都按单个汉字拆分：“大家好”和“大 家 好”得到相同的匹配单元。
- 标点不参与匹配；全角拉丁字母和数字进行兼容归一化。中文模型不能识别的外语词及数字形式的稿件标记按跳过规则处理，不把它们当作定位证据。未加入繁简转换或拼音同音替换。
- 文字显示、加粗、标题和方括号备注保留原有位置映射。中文短语模式可以在没有空格的句子中换行，保留原标点；备注不参与朗读匹配。
- 中文使用单句锚点和字符窗口对齐。正常跟随允许从锚点逐字推进；跳转需要连续字串和足够的匹配比例，减少常见单字造成的误跳。
- 中文 partial 替换前一个临时结果，final 提交锚点；英语保留历史合并流程。更新后的 token 索引驱动悬浮窗和全屏提词器的滚动与高亮。
- 中文按字计算速度和时长，不使用提前量；英语按词计算。中文界面统一显示“字词”，设置相同数值时，中文与英语的实际朗读节奏会有差异。
- 稿件热词取连续的 2–4 字片段，使用 sherpa-onnx 的 `cjkchar` 编码。中文不读取英语模型使用的 BPE 词表。

## 模型与校验

固定模型修订为 `204ad334e2e683fd295359930cc16fc0432a23ac`，下载任务为 `fetchSpeechAssets`。

| APK 内路径 | 上游文件 | SHA-256 |
| --- | --- | --- |
| `asr-zh/encoder.int8.onnx` | encoder-epoch-99-avg-1.int8.onnx | `1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1` |
| `asr-zh/decoder.onnx` | decoder-epoch-99-avg-1.onnx | `5ee0f03a2768ff1d5c83ef3a493243c7935d316cd41280037b14783a3467cc78` |
| `asr-zh/joiner.int8.onnx` | joiner-epoch-99-avg-1.int8.onnx | `a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169` |
| `asr-zh/tokens.txt` | tokens.txt | `8b294db9045d6e5f94647f4c1eec1af4da143a75053c399611444b378ff966ac` |

[官方模型用法](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html#csukuangfj-sherpa-onnx-streaming-zipformer-zh-14m-2023-02-23-chinese) · [固定版本模型文件](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/tree/204ad334e2e683fd295359930cc16fc0432a23ac) · [热词编码实现](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/utils.cc)

## 验证

`ChineseTrackerTest` 覆盖中文自动检测、无空格及带空格输出、字形归一化、显示位置映射、流式修订、停顿、重读、跳段、重复语句、热词和手动定位。中英语测试继续运行；历史俄语文本归一化和布局测试保留作回归检查，但不再提供俄语识别模型或选项。

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

可使用模型仓库自带的 `test_wavs/0.wav` 复核流式识别。参考文本为“对我做了介绍那么我想说的是大家如果对我的研究感兴趣”。普通解码和开启热词的解码都应能输出中文，随后由相同的逐字跟踪器定位到稿件末尾。Android 上的麦克风并发、功耗和实际朗读体验仍需在手机上验证。


## 按朗读进度定位当前字

参考 [Storm-Teleprompter-Plus 的字符对齐引擎](https://github.com/html5syt/Storm-Teleprompter-Plus/blob/f345ae2f79d7222ce444664c9ca0e8b89f7e79de/lib/services/asr/alignment_engine.dart)。中文直接消费 sherpa-onnx 的单句 partial/final：partial 从已确认锚点重算，final 提交锚点，修订中的短结果不会让游标回退。暂停恢复、重新开始和手动点字同步重置锚点。

过滤标点、舞台备注及中文模型不支持的外语和数字后，保留与原稿 token 和 UTF-16 偏移的映射。允许最多三个字的漏字、插入或替换误差，向前搜索最多 64 字；孤立常用字和低置信度结果不触发跳转。回读需要至少八个连续准确字，并限制在已读位置前 240 字内；相同的后续重复句优先继续向前。

中文不使用提前量，以实际识别位置标记下一个待读字，已读文字逐字淡化，并用 Android 实测排版行高滚动；全屏和悬浮窗共用这一逻辑。点击悬浮窗设置中的“调整大小”显示窗内 48dp 大把手，分别调整宽高，点“完成”收起；把手向内避开屏幕边缘手势，窗内移动把手在横竖屏均可移动位置；各方向分别保存大小与位置，不改变文字字号。设置支持左、中、右对齐。语音识别仅提供中文、英文，构建时清除旧俄语模型资产。

`ChineseAlignmentTest` 覆盖逐字定位、final 锚点、partial 修订、漏字错字、重复句、确认回读、标点备注及扩展汉字偏移、手动定位和重新开始。MIT 署名及完整许可见 `THIRD_PARTY_NOTICES.md`。


悬浮按钮仅在已开启悬浮提词且设置允许时显示：点击切换提词层显示/隐藏，隐藏时保留识别会话；长按退出悬浮提词并移除按钮。
