# 第三方开源库说明

欧了提词器基于 [Suflyor](https://github.com/olerast67/voice-teleprompter-android) 修改，沿用 [GNU GPL v3.0](LICENSE)。下列组件保留各自版权与许可证。

## APK 包含的组件

| 组件 | 用途 | 许可证 | 来源 |
|---|---|---|---|
| sherpa-onnx 1.13.8 | 本地流式语音识别 | Apache-2.0 | [固定版本源码](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8) |
| ONNX Runtime（静态链接于 sherpa AAR） | 运行语音神经网络 | MIT | [源码](https://github.com/microsoft/onnxruntime) |
| icefall 英语 Streaming Zipformer，LibriSpeech 2023-05-17 | 英语语音模型 | Apache-2.0 | [模型](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26)、[训练检查点](https://huggingface.co/Zengwei/icefall-asr-librispeech-streaming-zipformer-2023-05-17) |
| LibriSpeech BPE-500 词表（icefall） | 英语稿件词汇提示 | Apache-2.0 | [词表](https://huggingface.co/csukuangfj/icefall-asr-librispeech-conformer-ctc-jit-bpe-500-2021-11-09) |
| 中文 Streaming Zipformer 14M，WenetSpeech 2023-02-23 | 中文语音识别和字符词表 | Apache-2.0 | [固定提交模型](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/tree/204ad334e2e683fd295359930cc16fc0432a23ac) |
| RapidOCR 官方模型及处理流程 | 离线 OCR 检测、方向分类、透视校正和识别 | Apache-2.0 | [RapidOCR 固定源码](https://github.com/RapidAI/RapidOCR/tree/80b427d69bfc5b91a12913394875910296c6f315)、[模型 v3.10.0](https://www.modelscope.cn/models/RapidAI/RapidOCR/files?Revision=v3.10.0) |
| PaddleOCR PP-OCRv4 / PP-OCRv5 | 中文检测、方向分类、中英文字识别模型，来自 RapidAI 导出 | Apache-2.0 | [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) |
| ONNX Runtime Android 1.24.3 | OCR 推理；保留同版本 Java JNI 与完整 C 运行库，与语音运行库隔离 | MIT | [固定版本](https://github.com/microsoft/onnxruntime/tree/v1.24.3) |
| OpenCV 4.12.0 | 文本框提取、透视校正、图像预处理 | Apache-2.0 | [固定版本](https://github.com/opencv/opencv/tree/4.12.0) |
| AndroidX CameraX 1.5.3 | 内置扫描相机 | Apache-2.0 | [源码与文档](https://developer.android.com/jetpack/androidx/releases/camera) |
| AndroidX：Compose、Activity、Core | 界面、相机和文件选择器集成 | Apache-2.0 | [源码与文档](https://developer.android.com/jetpack/androidx) |
| Kotlin 标准库 | 运行库 | Apache-2.0 | [源码](https://github.com/JetBrains/kotlin) |
| kotlinx.coroutines | 异步识别和界面协程 | Apache-2.0 | [源码](https://github.com/Kotlin/kotlinx.coroutines) |
| Material Design 图标 | 部分矢量图标 | Apache-2.0 | [源码](https://github.com/google/material-design-icons) |

英语模型训练使用的 [LibriSpeech](https://www.openslr.org/12) 数据集作者为 V. Panayotov、G. Chen、D. Povey 和 S. Khudanpur，许可证 CC BY 4.0。

## 中文逐字跟读适配

中文语句锚点、字符窗口对齐和确认重读逻辑改编自 [Storm-Teleprompter-Plus 对齐引擎](https://github.com/html5syt/Storm-Teleprompter-Plus/blob/f345ae2f79d7222ce444664c9ca0e8b89f7e79de/lib/services/asr/alignment_engine.dart)，Copyright (c) 2026 Html5syt，MIT 许可。完整许可保存在 [licenses/Storm-Teleprompter-Plus-MIT.txt](licenses/Storm-Teleprompter-Plus-MIT.txt) 及 APK 的 assets/licenses/ 中。

## sherpa-onnx 原生库内的其他代码

预编译 libsherpa-onnx-jni.so 还包含应用未调用的语音合成、说话人分离等代码：

| 组件 | 许可证 | 来源 |
|---|---|---|
| espeak-ng（sherpa 分支） | GPL-3.0-or-later | [源码](https://github.com/k2-fsa/espeak-ng) |
| piper-phonemize | MIT | [源码](https://github.com/csukuangfj/piper-phonemize) |
| OpenFst | Apache-2.0 | [官网](https://www.openfst.org/) |
| kaldifst、kaldi-decoder、kaldi-native-fbank | Apache-2.0 | [k2-fsa](https://github.com/k2-fsa)、[kaldi-native-fbank](https://github.com/csukuangfj/kaldi-native-fbank) |
| simple-sentencepiece | Apache-2.0 | [源码](https://github.com/pkufool/simple-sentencepiece) |
| nlohmann/json | MIT | [源码](https://github.com/nlohmann/json) |

对应源码为 sherpa-onnx v1.13.8 及其 CMake 固定的依赖版本。如无法从以上来源取得对应源码，可在仓库提出请求。

## 打包与许可文本

语音和 OCR AAR、模型不直接提交 Git；fetchSpeechAssets 在构建时下载固定版本或提交的文件，校验 SHA-256 后打包进 APK。运行时不下载识别模型，OCR 不依赖 Google Play 服务，也不发送图片和识别结果。

RapidOCR、OpenCV 的 Apache-2.0 许可和 ONNX Runtime 的 MIT 许可随 APK 放在 assets/licenses/ 中。完整 UTF-8 识别字典在构建时从校验后的 ONNX 模型元数据提取，避免 JNI 字符串对增补字符的截断。许可文本：[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) · [MIT](https://opensource.org/license/mit) · [GPL-3.0](LICENSE)。

