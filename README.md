# 欧了提词器

基于 [Suflyor](https://github.com/olerast67/voice-teleprompter-android) 修改的 Android 中文提词器，支持中英离线语音跟读、悬浮提词、在线稿件同步，以及拍照和相册图片离线 OCR 扫描。

当前版本：**1.0**（versionCode 8）。需要 Android 10 及以上、ARM64 手机，应用和 APK 名称均为 **欧了提词器**。

## 中文版修改

- 中文界面和示例；语音识别只提供中文和英文。
- 使用 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 与内置 Zipformer 模型离线识别。中文按字符匹配朗读进度，定位当前字，支持停顿、跳读和重读。
- 中文逐字对齐参考 [Storm-Teleprompter-Plus](https://github.com/html5syt/Storm-Teleprompter-Plus) 的 MIT 实现并保留许可，详见 [中文跟读说明](docs/chinese-asr.md)。
- 在线稿件在启动时自动更新，也可点击「更新」手动刷新；缓存稿件可离线使用。地址在稿件库「⋮ → 在线链接设置」配置，输出格式见 [在线稿件 JSON 格式](docs/online-library-format.md)。
- 新建稿件增加「扫描文稿」：支持拍照、相册选图、旋转预览、离线识别、校对后保存。
- OCR 使用 [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) 4.9.0 / Tesseract 5.5.1，简体中文、繁体中文和英文模型随 APK 打包；首次使用无需联网下载模型，无需 Google Play 服务。
- 支持「仅悬浮窗」（默认）、相机、剪映、抖音和快手。
- 悬浮按钮在开始悬浮提词后显示；点击显示或隐藏提词窗，长按退出。设置中可关闭该按钮，退出应用会清理悬浮控件。
- 悬浮设置中的「调整大小」显示加大且向内缩进的调节控件，分别调宽、调高；横屏也支持拖动、调整宽高和位置。
- 支持左对齐、居中和右对齐。提词期间保持屏幕常亮，结束后恢复系统行为。

## 扫描文稿

1. 打开「稿件库 → ＋ → 扫描文稿」。
2. 选择「拍照扫描」或「从相册选择」，让页面文字清晰、光照均匀且尽量摆正。
3. 选择识别语言（默认简体中文），必要时点击「向右旋转 90°」，然后点击「识别文字」。
4. 校对识别结果、修改标题，点击「完成」保存到稿件库。

图片和识别内容均在本地处理，不会上传。拍照通过系统相机应用完成，相册通过系统文件选择器打开，不申请整个相册的读取权限。OCR 主要面向清晰的印刷文稿；模糊、反光、手写或复杂排版可能产生错字，请校对后使用。

## 其他功能

- 全屏提词、镜像显示、语音跟读、定时滚动和按声音滚动。
- 音量键、蓝牙遥控器和键盘控制，按键可配置。
- 导入 TXT、Markdown、DOCX、ODT、RTF、HTML；PDF 文本提取依赖系统版本，扫描 PDF 暂不支持。
- 稿件编辑、复制、分享，以及稿件库 ZIP 备份和恢复。
- 无障碍服务用于跨应用语音提词、悬浮窗和按键控制，不能读取屏幕内容。

## 获取 APK

进入本仓库 [Build APK 工作流](https://github.com/MeiQianDaRen/voice-teleprompter-android/actions/workflows/build-apk.yml)，打开最新成功运行，在 Artifacts 中下载「欧了提词器-APK-*」，解压安装「欧了提词器.apk」。压缩包同时提供 SHA256SUMS.txt。

工作流产物是调试签名 APK，应用 ID 为 com.olerast.suflyor.debug，可与正式版共存。覆盖安装需要签名一致；若提示签名冲突，先备份稿件，再处理旧安装。正式发布使用 [release 工作流](.github/workflows/release.yml) 和维护者配置的签名密钥。

首次语音提词需允许麦克风，跨应用提词需按应用指引配置无障碍或悬浮窗权限。Android 13 及以上从文件安装时，如无障碍开关受限，可在应用的系统设置中允许受限设置。

## 构建与验证

环境：JDK 17、Android SDK 36、Build Tools 36.0.0；使用 Gradle Wrapper：

```sh
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
```

Windows 使用 gradlew.bat。APK 位于 app/build/outputs/apk/debug/app-debug.apk。首次构建需要联网获取依赖和模型；运行时语音和扫描识别可完全离线。下载资产固定版本或提交并校验 SHA-256，配置位于 app/build.gradle.kts。

Build APK 会检查中英语音模型、三种 OCR 模型及 ARM64 OCR 引擎均已打包。CI 执行构建、单元测试和 lint，已有问题由现存 lint baseline 管理。

每次交付修改须递增 versionCode、升级 versionName 并更新 [CHANGELOG.md](CHANGELOG.md)，规则见 [AGENTS.md](AGENTS.md)。

## 隐私、来源与许可

语音与 OCR 不上传音频、图片或识别文本。在线稿件只向用户配置的地址发起请求。无广告、无账号、无分析跟踪，详见 [隐私说明](PRIVACY.md)。

原项目 Suflyor 由 [olerast67](https://github.com/olerast67) 开发；本仓库提供中文适配及上述修改。感谢 sherpa-onnx、icefall、ONNX Runtime、Storm-Teleprompter-Plus、Tesseract、Leptonica 和 AndroidX 等项目，详见 [第三方开源库说明](THIRD_PARTY_NOTICES.md)。

本项目沿用 [GNU GPL v3.0](LICENSE)。分发修改版本时需遵守原许可证并提供对应源码。上游历史更新保留在更新日志中。
