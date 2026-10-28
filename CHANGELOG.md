# 更新日志

## 1.2 — 2026-10-28

- 修复扫描图片时 ONNX Runtime 原生库加载失败（OrtGetApiBase）导致应用闪退；语音改用官方静态链接包，OCR 使用完整配套的 Java JNI 和 C 运行库。
- 原生库加载异常改为显示识别失败提示并记录诊断日志，保留当前图片以便重试。
- CI 与 APK 工作流新增最终安装包的 ARM64 原生符号和依赖检查，防止再次混用运行库。
- versionName 升至 1.2，versionCode 递增至 10。

## 1.1 — 2026-10-28

- 修正官网域名为 https://www.oolele.com；关于页「中文版本」改为独立标题，版本号可点击打开 https://www.oolele.com/suflyor/update。
- 关于页源代码链接恢复上游仓库，模型说明新增文字识别模型列表。
- 应用语言入口移至高级设置；扫描识别语言移至「设置 → 扫描文稿」，提供中文、英文模型选择。
- 扫描入口直接打开内置相机，支持拍照或相册选图；图片选好后自动识别，原图、进度和可编辑结果在同页展示，可重拍、换图、旋转重识别并保存。
- 移除 Tesseract4Android，改用 RapidOCR 官方 PP-OCRv4 检测、方向分类和英文模型，以及 PP-OCRv5 中文模型，全部随 APK 打包；支持完整中文词典、透视校正和倒置页面校正。
- versionName 升至 1.1，versionCode 递增至 9。

## 1.0 — 2026-10-28

- 新桌面图标以小写「ole」为主体，搭配提词进度标记，保留琥珀色品牌背景，支持自适应和系统主题图标。
- 关于页网站地址改为 https://www.oolele.com。
- 增加软件名称说明：中文名称「欧了提词器」、英文名称「Suflyor」。
- 移除「反馈问题」和「支持项目」入口。
- 增加「中文版本」，从当前应用版本动态显示；关于页版本展示去除调试构建后缀。
- versionName 升至 1.0，versionCode 递增至 8。

## 0.7 — 2026-10-28

- 稿件库新建入口增加「扫描文稿」：拍照或相册选图，旋转预览，离线 OCR 识别并校对后保存。
- 内置 Tesseract 5.5.1 引擎和简体中文、繁体中文、英文模型，首次使用也无需下载模型，图片和识别文本不上传。
- 完整中文项目介绍与第三方开源库说明，补充中文适配、逐字跟读、在线稿件库和悬浮窗操作说明；应用内关于页加入中文版功能说明。
- 记录此前中文版本的功能：中英离线语音、中文逐字定位、在线稿件启动和手动更新、应用名称「欧了提词器」、悬浮按钮显示隐藏及长按退出、横屏宽高位置调节、三种文本对齐和提词时屏幕常亮。
- versionCode 升至 7，versionName 升至 0.7；AGENTS.md 规定后续每次交付修改同步升级版本并记录中文更新日志。

以下保留上游历史记录。

All notable changes to Suflyor. Versions follow `versionName` in `app/build.gradle.kts`; each release's notes are taken from its section here.

## 0.6 — 2026-10-21

- **Letter names in Russian scripts.** Abbreviations written with hyphens, like "Би-ту-би" or "Эй-Ай", are now boosted in the recognizer as one word, and the text follows whether the recognizer prints them glued ("битуби") or apart ("би ту би", "би тубе"). Before, a line like "Это мои Би-ту-би Эй-Ай СааС проекты" could leave the highlight several words behind until the next line. Ordinary hyphenated words ("кто-то", "из-за") are not affected.
- **Names heard with reduced vowels.** Unstressed о/а and е/и in long words count as close: a name written "Олераст" and heard as "алираст" now moves the text.
- **Bug reports without a GitHub account:** a form on olerast.com, linked from the README, the website and the issue chooser.

## 0.5 — 2026-09-29

- **Scrolling by sound.** A third scroll mode next to voice and timer: the text moves at the set speed while you talk and waits while you're quiet. It needs no speech model, so it works for a script in any language.
- **Full-screen prompter** (the rehearsal screen): text up to 80 sp, remote keys, and a mirror for teleprompter glass, all under the View button.
- **Scripts:** share a script's text, duplicate it, save every script to a zip of Markdown files and restore such a zip. The editor counts words and the length of the take as you type.
- **Errors are visible.** When the recognizer fails to load or the microphone stops, the window turns red and says why instead of showing "listening". A microphone that stops is reopened (three tries with growing pauses); if that fails, the text keeps moving at the set speed and the chosen mode stays for the next session.
- **Crash report:** a crash or a freeze leaves one report in the app's private folder, without recognized phrases, app names or script titles; the log screen offers to share or delete it. Errors in the log now include a few stack frames.
- **Memory and battery:** the speech model is freed 2.5 minutes after a session and when Android is short of memory; recognition no longer runs while paused, on the timer or when the microphone gives only silence.
- **Fixes:** turning "boost script words" on or off takes effect at once, not after a language change. Scripts are written atomically, so a full storage or a killed app can't leave half a file; an unreadable file is set aside as .broken instead of stopping the library from loading. The floating window uses a cut-out mode that exists on Android 10.
- **About:** links to the website, the source code, bug reports and donations, and the license.
- **Settings in their place.** Settings are now a short list of topics, each on its own page. What you change while filming is where you film: the Aa button in the floating window opens text size, lines, background and speed right over the camera; View on the full-screen prompter holds text size, mirror and speed; the script screen has the speech language and the speed; the ⋮ menu of the script list saves and restores backups.
- **Speech language per script.** Each script can have its own language; the one in Settings is the default for the rest.
- **Mirror** can flip left to right, upside down or both, to match the rig.

## 0.4 — 2026-09-26

First public version. Suflyor now works in English as well as Russian; everything from 0.3 below is included.

- **English interface.** The app follows the phone's language; on Android 13+ it can be set just for Suflyor. Russian stays as it was.
- **English speech**, on the phone like Russian:
  - a streaming Zipformer model trained on LibriSpeech, next to the Russian one;
  - the speech language is picked from the script's letters, or fixed in Settings; opening a script in the other language swaps the model;
  - English-aware following: contractions and curly apostrophes, numbers written as digits but said as words, abbreviations (Mr., Dr., e.g.), acronyms spelled out (A I, C E O), fillers (um, uh), and "the" is no longer taken for the start of "then".
- **Built-in sample script** in the phone's language, with its bold text fixed.
- **Log** lines are English in every language, so they can go straight into bug reports.
- **Accessibility:** the floating window's pause button announces Pause or Resume, and the text size buttons have labels.

## 0.3 — 2026-09-25

Not published; these changes ship in 0.4.

- **Floating prompter over any app** — Instagram, TikTok, the camera:
  - it follows your voice while that app records video;
  - the text starts right under the front camera;
  - lock mode lets touches pass through to the camera buttons;
  - for landscape recording the text turns towards the lens.
- **Voice following**, offline Russian speech recognition (sherpa-onnx):
  - searches the whole script, and rare words count more than common ones;
  - finds a neighbouring line after about two words, a far jump after three or four;
  - stays put in pauses and when you talk off script.
- **Quick Settings tile** starts the prompter over whatever app is open.
- **Remote controls**: volume keys, Bluetooth selfie remotes, rings, clickers and keyboards. Keys can be reassigned.
- **Script library**:
  - import of TXT (UTF-8, UTF-16, Windows-1251, KOI8-R), Markdown and Obsidian, DOCX, ODT, RTF, HTML and PDF;
  - share-to and open-with from other apps;
  - a simple editor with `**emphasis**`, `# headings` and `[notes that are not read]`.
- **Rehearsal** mode and timed scrolling with a 3-2-1 countdown.
- **Private by design**:
  - no `INTERNET` permission, analytics or ads;
  - the accessibility service works only during a session and can't read the screen;
  - scripts are excluded from cloud backup, and release builds don't copy the journal to the system log.
- **Hardened importers**: limits against zip bombs, XML entity tricks and oversized files; DOCX tracked changes and Strict OOXML; RTF font code pages.
- **Signed releases** built by GitHub Actions, with SHA-256 checksums and build provenance attestations.
