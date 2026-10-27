# 简体中文界面翻译对照

中文系统会自动使用中文界面。Android 13 及以上也可在“设置 → 关于 → 应用语言”中单独选择中文。

界面支持简体中文，离线语音跟随支持中文、英语和俄语。中文按汉字匹配和统计，速度单位为字/分钟；英语和俄语按词统计。设置中的“自动”会根据稿件正文选择语音模型，详见 [中文语音识别](chinese-asr.md)。已有稿件（包括已创建的示例）不会被自动翻译或覆盖。诊断日志内容及系统返回的错误详情保留原文。

下表包含全部 281 条可翻译字符串及 3 组数量文案；品牌名、文件格式等标记为不可翻译的资源沿用原值。数量文案按中文规则使用 `other`，动态占位符保持兼容。

## strings.xml

| 资源名 | 英文原文 | 中文译文 |
| --- | --- | --- |
| `app_name` | Suflyor | Suflyor 提词器 |
| `a11y_label` | Suflyor: floating window and voice | Suflyor：悬浮窗与语音 |
| `a11y_description` | Lets Suflyor hear your voice while another app records video, and show your text over the camera. Works only during a Suflyor session: it sees which app is on screen (for the log) and receives the volume, Bluetooth-remote and keyboard buttons set up in Settings. It doesn't read screen content. Nothing leaves your phone — the app has no internet access. | 让 Suflyor 在其他应用录制视频时仍能听到你的声音，并在相机画面上方显示提词。仅在 Suflyor 提词期间运行：读取前台应用名称（用于日志），并接收你在设置中指定的音量键、蓝牙遥控器和键盘按键。不读取屏幕内容。所有数据均留在手机上，应用无法访问互联网。 |

## strings_common.xml

| 资源名 | 英文原文 | 中文译文 |
| --- | --- | --- |
| `common_cd_back` | Back | 返回 |
| `common_cd_restart` | Back to start | 回到开头 |
| `common_cd_line_back` | Previous line | 上一行 |
| `common_cd_line_forward` | Next line | 下一行 |
| `common_cd_pause` | Pause | 暂停 |
| `common_cd_resume` | Resume | 继续 |
| `common_cd_scroll_mode` | Scroll mode: voice, speed or sound | 滚动模式：语音、定速或声音 |
| `common_cancel` | Cancel | 取消 |
| `common_delete` | Delete | 删除 |
| `common_untitled` | Untitled | 未命名 |
| `words_count` | one: %d word<br>other: %d words | %d 个字词 |
| `format_typed` | text | 文本 |
| `format_sample` | built-in | 内置 |
| `key_action_pause` | Pause / resume | 暂停 / 继续 |
| `key_action_back` | Previous line | 上一行 |
| `key_action_forward` | Next line | 下一行 |
| `camera_target_camera` | Camera | 相机 |
| `camera_target_window_only` | Window only | 仅悬浮窗 |
| `start_problem_no_host` | Turn on the Suflyor accessibility service or allow display over other apps | 请开启 Suflyor 无障碍服务，或允许显示在其他应用上层 |
| `start_problem_window_failed` | Couldn't show the Suflyor window. Check the Suflyor accessibility service. | 无法显示 Suflyor 悬浮窗，请检查 Suflyor 无障碍服务。 |
| `speech_lang_auto` | Auto | 自动 |

## strings_import.xml

| 资源名 | 英文原文 | 中文译文 |
| --- | --- | --- |
| `import_error_empty_file` | The file is empty | 文件为空 |
| `import_error_file_too_big` | The file is over %1$d MB — is it really a script? | 文件超过 %1$d MB，请确认是否为稿件。 |
| `import_error_text_file_too_big` | This text file is too big for a script | 文本文件过大，无法作为稿件导入 |
| `import_error_legacy_doc` | The old .doc format (Word 97–2003) isn't supported. Save the file as .docx or .pdf. | 不支持旧版 .doc 格式（Word 97–2003），请另存为 .docx 或 .pdf。 |
| `import_error_encrypted` | The document is password-protected. Remove the password and open it again. | 文档受密码保护，请移除密码后重新打开。 |
| `import_error_unknown_zip` | This is an archive, but not a DOCX or ODT file | 这是一个压缩包，但不是 DOCX 或 ODT 文件 |
| `import_error_no_text` | No text found in the file | 文件中未找到文本 |
| `import_error_not_text` | This isn't a text file | 这不是文本文件 |
| `import_error_pasted_too_long` | The text is too long for a script | 文本过长，无法作为稿件导入 |
| `import_error_text_too_long_count` | The text is too long: %1$dK characters. Suflyor takes up to %2$dK. | 文本过长：共 %1$d 千个字符，Suflyor 最多支持 %2$d 千个字符。 |
| `import_error_text_too_long_max` | The text is too long: Suflyor takes up to %1$dK characters. | 文本过长：Suflyor 最多支持 %1$d 千个字符。 |
| `import_error_not_rtf` | This isn't an RTF file | 这不是 RTF 文件 |
| `import_error_zip_too_many_files` | The archive contains too many files | 压缩包中的文件过多 |
| `import_error_zip_too_large` | The archive unpacks to too much data | 压缩包解压后的数据过大 |
| `import_error_unsafe` | The document is damaged or unsafe | 文档已损坏或存在安全风险 |
| `import_error_not_docx` | There's no word/document.xml inside — this isn't a Word document | 文件中没有 word/document.xml，不是 Word 文档 |
| `import_error_not_odt` | There's no content.xml inside — this isn't an ODT document | 文件中没有 content.xml，不是 ODT 文档 |
| `import_error_pdf_unsupported` | This Android version can't read PDF text yet. Open a DOCX or TXT file instead. | 此 Android 版本暂不支持读取 PDF 文本，请改用 DOCX 或 TXT 文件。 |
| `import_error_pdf_password` | The PDF is password-protected | PDF 受密码保护 |
| `import_error_pdf_scan` | This PDF has no text layer — it looks like a scan. Scan support is coming later. | 此 PDF 没有文本层，可能是扫描件，暂不支持导入。 |
| `import_error_open_failed` | Couldn't open the file | 无法打开文件 |
| `import_error_read_failed` | Couldn't read the file: %1$s | 无法读取文件：%1$s |
| `import_error_generic` | Import failed | 导入失败 |
| `import_warning_pdf_paragraphs` | Paragraphs were rebuilt from the PDF's lines — check how the text was split. | 已根据 PDF 中的文本行重新划分段落，请检查分段是否正确。 |
| `import_warning_docx_tables` | one: The document has a table: its cells are shown in order, as plain text.<br>other: The document has %1$d tables: their cells are shown in order, as plain text. | 文档中有 %1$d 个表格，单元格内容将按顺序显示为纯文本。 |
| `import_title_shared` | Shared text | 分享的文本 |
| `import_title_clipboard` | From clipboard | 来自剪贴板 |
| `import_opening` | Opening… | 正在打开… |
| `import_nothing` | Nothing to import | 没有可导入的内容 |
| `import_clipboard_empty` | There's no text in the clipboard | 剪贴板中没有文本 |
| `main_mic_blocked` | The microphone is blocked — turn it on in the app's permissions | 麦克风被禁用，请在应用权限中开启 |
| `main_open_app_failed` | Couldn't open %1$s | 无法打开 %1$s |
| `main_no_words` | There are no words in the text | 文本中没有可识别的单词 |
| `script_cd_edit` | Edit | 编辑 |
| `script_stats` | ≈ %1$s · %2$s · %3$s | ≈ %1$s · %2$s · %3$s |
| `script_setup_mic` | Allow the microphone so Suflyor can hear you | 请允许使用麦克风，让 Suflyor 听到你的声音 |
| `script_setup_a11y` | Turn on the Suflyor service in Accessibility — otherwise Suflyor can't hear you while recording | 请在无障碍设置中开启 Suflyor 服务，否则录制视频时无法听到你的声音 |
| `script_action_overlay` | Over the camera | 悬浮提词 |
| `script_action_rehearse` | Rehearse | 排练 |
| `script_delete_title` | Delete script? | 删除稿件？ |
| `script_delete_message` | “%1$s” will be removed from the library. The original file stays on your phone. | 将从稿件库中删除“%1$s”，手机上的原始文件会保留。 |
| `sample_title` | Sample | 示例 |
| `sample_script` | # Sample script<br><br>Hi! Today I'll show you how this teleprompter follows your voice and scrolls the text by itself, so you never have to chase it.<br><br>**Read a couple of lines**, look into the camera, then glance back at the text. If you slip and start a sentence over, the text jumps back to where that sentence begins.<br><br>[Pause, smile] And if you go quiet, the text simply waits for you.<br><br>Now try reading this paragraph over the camera, Instagram or TikTok — the prompter keeps listening to you even while you record. | # 示例稿件<br><br>你好！今天我来演示这款提词器如何跟随声音自动滚动文字，让你不必追着文字读。<br><br>**先读几行**，看看镜头，再看回文字。如果读错后重新读一句，文字会跳回这句话的开头。<br><br>[停顿，微笑] 如果你暂时不说话，文字也会停下来等你。<br><br>现在试着在相机、Instagram 或 TikTok 画面上方读这段话，录制时提词器也能继续听到你的声音。<br><br>[说明：目前语音跟随仅支持英语和俄语。此中文示例可用于定速或声音模式；测试语音跟随时，请导入英语或俄语稿件。] |

## strings_overlay.xml

| 资源名 | 英文原文 | 中文译文 |
| --- | --- | --- |
| `overlay_cd_lock` | Lock: taps go through to the camera | 锁定：点击可穿透到相机 |
| `overlay_cd_close` | Close | 关闭 |
| `overlay_cd_unlock` | Unlock window | 解锁悬浮窗 |
| `overlay_status_starting` | starting… | 启动中… |
| `overlay_status_stopped` | stopped | 已停止 |
| `overlay_status_paused` | paused | 已暂停 |
| `overlay_status_auto` | speed | 定速 |
| `overlay_status_mic_busy` | mic busy | 麦克风占用 |
| `overlay_status_cant_hear` | can't hear | 未听到声音 |
| `overlay_status_listening` | listening | 正在聆听 |
| `overlay_toast_closed_a11y_off` | Suflyor window closed: the accessibility service was turned off | Suflyor 悬浮窗已关闭：无障碍服务已关闭 |
| `tile_label` | Suflyor | Suflyor 提词器 |
| `tile_subtitle_listening` | Listening | 正在聆听 |
| `notification_channel_session` | Suflyor running | Suflyor 提词运行中 |
| `notification_session_title` | Suflyor is listening | Suflyor 正在聆听 |
| `notification_session_text` | The floating window is on | 悬浮窗已开启 |
| `notification_action_stop` | Stop | 停止 |
| `session_error_asr_failed` | Speech recognition didn't load: %1$s | 语音识别加载失败：%1$s |
| `session_error_rate_unsupported` | The microphone doesn't support %1$d Hz | 麦克风不支持 %1$d Hz 采样率 |
| `session_error_recorder_create` | Couldn't create AudioRecord: %1$s | 无法创建 AudioRecord：%1$s |
| `session_error_recorder_init` | AudioRecord didn't initialize (no microphone permission?) | AudioRecord 初始化失败，请检查麦克风权限 |
| `session_error_record_start` | Couldn't start recording: %1$s | 无法开始录音：%1$s |
| `session_error_mic_busy` | Android wouldn't start recording — another app is using the microphone | Android 无法开始录音：其他应用正在使用麦克风 |
| `session_error_mic_read` | Microphone read error: %1$d | 麦克风读取错误：%1$d |
| `key_name_volume_up` | Volume + | 音量 + |
| `key_name_volume_down` | Volume − | 音量 − |
| `key_name_space` | Space | 空格键 |
| `key_name_dpad_center` | D-pad center | 方向键中央键 |
| `key_name_next_track` | Next track | 下一曲 |
| `key_name_previous_track` | Previous track | 上一曲 |
| `key_name_headset` | Headset button | 耳机按键 |
| `key_name_fast_forward` | Fast forward | 快进 |
| `key_name_rewind` | Rewind | 快退 |
| `key_name_gamepad_a` | Gamepad A | 手柄 A 键 |
| `overlay_status_error` | error | 出错 |
| `overlay_status_sound` | by sound | 声音 |
| `overlay_cd_tune` | Text size, lines, background, speed | 字号、行数、背景、速度 |
| `overlay_tune_font` | Text | 字号 |
| `overlay_tune_lines` | Lines | 行数 |
| `overlay_tune_background` | Background | 背景 |
| `overlay_tune_speed` | Speed | 速度 |

## strings_ui.xml

| 资源名 | 英文原文 | 中文译文 |
| --- | --- | --- |
| `library_title` | Scripts | 稿件库 |
| `library_search_hint` | Search | 搜索 |
| `library_search_empty` | Nothing found | 未找到结果 |
| `library_cd_add` | Add script | 添加稿件 |
| `library_add_title` | New script | 新建稿件 |
| `library_add_file` | Open file | 打开文件 |
| `library_add_paste` | Paste from clipboard | 从剪贴板粘贴 |
| `library_add_paste_hint` | Text copied in any app | 在其他应用中复制的文本 |
| `library_add_write` | Write | 手动输入 |
| `library_add_write_hint` | Type or paste the text yourself | 自行输入或粘贴文本 |
| `library_setup_title` | Set up recording over the camera | 设置悬浮提词录制 |
| `library_setup_progress` | %1$d of %2$d done: needs the microphone and the Suflyor service | 已完成 %1$d / %2$d 项：需要麦克风权限和 Suflyor 服务 |
| `editor_title_new` | New script | 新建稿件 |
| `editor_title_edit` | Edit script | 编辑稿件 |
| `editor_action_done` | Done | 完成 |
| `editor_hint_title` | Title | 标题 |
| `editor_hint_text` | Paste or type your text. Start each paragraph on a new line. | 粘贴或输入文本，每个段落另起一行。 |
| `editor_syntax_help` | **bold** — emphasis · # heading · [note] — shown, not read aloud | **粗体** — 强调 · # 标题 · [备注] — 显示但不朗读 |
| `rehearsal_status_starting` | starting… | 启动中… |
| `rehearsal_status_idle` | not listening | 未在聆听 |
| `rehearsal_status_paused` | paused | 已暂停 |
| `rehearsal_status_auto` | speed · %1$d wpm | 定速 · %1$d 字词/分钟 |
| `rehearsal_status_mic_busy` | another app is using the mic | 其他应用正在使用麦克风 |
| `rehearsal_status_fallback` | can't hear you — scrolling on my own | 未听到你的声音，正在自动滚动 |
| `rehearsal_status_listening` | listening | 正在聆听 |
| `journal_title` | Log | 日志 |
| `journal_clip_label` | Suflyor log | Suflyor 日志 |
| `journal_toast_copied` | Log copied | 日志已复制 |
| `journal_cd_copy` | Copy | 复制 |
| `journal_share_title` | Send log | 发送日志 |
| `journal_cd_share` | Share | 分享 |
| `journal_cd_clear` | Clear | 清空 |
| `stepper_cd_decrease` | Decrease %1$s | 减小%1$s |
| `stepper_cd_increase` | Increase %1$s | 增大%1$s |
| `settings_title` | Settings | 设置 |
| `settings_action_allow` | Allow | 允许 |
| `settings_action_open` | Open | 打开 |
| `settings_section_readiness` | Ready to record over the camera | 悬浮提词录制准备 |
| `settings_mic_title` | Microphone | 麦克风 |
| `settings_mic_hint` | Best choice: “While using the app” | 建议选择“仅在使用该应用时允许” |
| `settings_a11y_title` | Suflyor accessibility service | Suflyor 无障碍服务 |
| `settings_a11y_not_running` | On but not running — turn it off and on again | 已开启但未运行，请关闭后重新开启 |
| `settings_a11y_hint` | Without it, Android mutes the mic while another app records video | 未开启时，其他应用录制视频会导致 Android 将麦克风静音 |
| `settings_a11y_restricted` | If the switch is greyed out: Settings → Apps → %1$s → ⋮ → “Allow restricted settings”. | 如果开关呈灰色：设置 → 应用 → %1$s → ⋮ → “允许受限设置”。 |
| `settings_app_info` | App info | 应用信息 |
| `settings_notifications_title` | Notifications | 通知 |
| `settings_notifications_hint` | An icon in the notification shade while Suflyor listens, with a Stop button | Suflyor 聆听时在通知栏显示图标，并提供停止按钮 |
| `settings_section_voice` | Voice following | 语音跟随 |
| `settings_speech_lang_auto_hint` | Auto picks it from the script's letters — now: %1$s | 根据稿件中的文字自动选择，当前为：%1$s |
| `settings_speech_lang_name_ru` | Russian | 俄语 |
| `settings_speech_lang_name_en` | English | 英语 |
| `settings_speech_lang_name_zh` | Chinese | 中文 |
| `settings_lead_title` | Look-ahead | 提前显示 |
| `settings_lead_value` | one: %d word<br>other: %d words | %d 个字词 |
| `settings_lead_hint` | How far the text runs ahead of what's recognized while you speak | 朗读时，文字比已识别的内容提前显示多少个字词 |
| `settings_highlight_title` | Highlight the next word | 高亮下一个字词 |
| `settings_highlight_hint` | Otherwise only the current line is marked | 关闭时仅标记当前行 |
| `settings_hotwords_title` | Boost script words | 增强稿件词汇识别 |
| `settings_hotwords_hint` | Better at rare words. Takes effect the next time the app starts | 提高生僻词识别效果，下次启动应用时生效 |
| `settings_section_text` | Text | 文本 |
| `settings_phrase_title` | One phrase per line | 每行一个短语 |
| `settings_phrase_hint` | Split the text into phrases you can say in one breath | 将文本拆分成一口气能读完的短语 |
| `settings_max_words_title` | Words per line | 每行字词数 |
| `settings_max_words_value` | up to %1$d | 最多 %1$d 个 |
| `settings_font_title` | Text size | 字号 |
| `settings_section_tile` | Quick Settings tile | 快捷设置磁贴 |
| `settings_tile_hint` | Pull down the shade and tap “Suflyor” — a window with the current script appears over the open app. Tap again to stop. | 下拉通知栏，点击“Suflyor 提词器”，即可在当前应用上方显示稿件悬浮窗。再次点击可停止。 |
| `settings_tile_add` | Add tile to Quick Settings | 添加到快捷设置 |
| `settings_tile_manual` | Pull the shade all the way down → ✎ (edit) → drag the “Suflyor” tile up. | 完全展开通知栏 → 编辑 → 将“Suflyor 提词器”磁贴拖到上方。 |
| `settings_tile_added` | Tile added to Quick Settings | 已添加到快捷设置 |
| `settings_tile_already_added` | The tile is already in Quick Settings | 快捷设置中已存在该磁贴 |
| `settings_section_keys` | Remote and buttons | 遥控器与按键 |
| `settings_keys_title` | Control with buttons | 使用按键控制 |
| `settings_keys_hint` | Volume, Bluetooth remote, ring, keyboard: in the window over the camera and on the full-screen prompter | 支持音量键、蓝牙遥控器、指环遥控器和键盘，可用于悬浮窗及全屏提词器 |
| `settings_volume_keys_title` | Volume buttons too | 同时启用音量键 |
| `settings_volume_keys_hint` | Turn off if your remote starts camera recording with a volume button | 如果遥控器通过音量键启动相机录制，请关闭此选项 |
| `settings_key_waiting` | Press a button on the remote… | 请按下遥控器上的按键… |
| `settings_key_none` | not assigned | 未分配 |
| `settings_key_assign` | Assign | 分配 |
| `settings_keys_reset` | Reset buttons to defaults | 恢复默认按键 |
| `settings_section_overlay` | Window over the camera | 相机上方的悬浮窗 |
| `settings_rotate_title` | Rotate for landscape recording | 横屏录制时自动旋转 |
| `settings_rotate_hint` | Phone on its side — the text moves next to the lens and turns | 手机横放时，文字会旋转并移到镜头旁边 |
| `settings_lines_title` | Lines in the window | 悬浮窗行数 |
| `settings_opacity_title` | Background opacity | 背景不透明度 |
| `settings_opacity_hint` | Lower lets you see yourself through the window | 调低后可透过悬浮窗看到自己 |
| `settings_diag_title` | Diagnostics in the window | 在悬浮窗中显示诊断信息 |
| `settings_diag_hint` | What Suflyor hears and who else is recording audio | 显示 Suflyor 听到的内容及其他正在录音的应用 |
| `settings_section_autoscroll` | Scrolling without voice | 非语音跟随滚动 |
| `settings_wpm_title` | Speed | 速度 |
| `settings_wpm_hint` | Words per minute: for auto-scroll and timing estimates | 每分钟字词数（中文按字计算），用于自动滚动和时长估算 |
| `settings_countdown_title` | Countdown before start | 开始前倒计时 |
| `settings_countdown_off` | off | 关闭 |
| `settings_countdown_value` | %1$d s | %1$d 秒 |
| `settings_countdown_hint` | 3-2-1 before auto-scroll starts | 自动滚动开始前进行 3-2-1 倒计时 |
| `settings_section_mic` | Microphone | 麦克风 |
| `settings_audio_source` | Audio source | 音频来源 |
| `settings_source_speech` | Speech | 语音识别 |
| `settings_source_standard` | Standard | 标准 |
| `settings_source_raw` | Unprocessed | 未处理 |
| `settings_sample_rate` | Sample rate | 采样率 |
| `settings_rate_khz` | %1$d kHz | %1$d kHz |
| `settings_section_about` | About | 关于 |
| `settings_language_title` | App language | 应用语言 |
| `settings_language_value` | English | 简体中文 |
| `settings_log_hint` | What happened with the microphone and recognition | 查看麦克风和语音识别的运行情况 |
| `settings_about_version` | Suflyor %1$s · speech recognition: sherpa-onnx; models: alphacep (Russian), icefall LibriSpeech (English), WenetSpeech (Chinese), Apache-2.0 | Suflyor %1$s · 语音识别：sherpa-onnx；模型：alphacep（俄语）、icefall LibriSpeech（英语）、WenetSpeech（中文），Apache-2.0 |
| `rehearsal_status_error` | error, see below | 发生错误，详情见下方 |
| `rehearsal_status_sound` | while you talk · %1$d wpm | 说话时滚动 · %1$d 字词/分钟 |
| `library_save_failed` | Couldn't save the script: %1$s | 无法保存稿件：%1$s |
| `script_copy_title` | %1$s (copy) | %1$s（副本） |
| `script_cd_share` | Share text | 分享文本 |
| `script_cd_duplicate` | Duplicate | 创建副本 |
| `script_share_title` | Send script | 发送稿件 |
| `backup_saved` | Scripts saved to the archive: %1$d | 已将 %1$d 篇稿件保存到备份压缩包 |
| `backup_failed` | Couldn't save the archive: %1$s | 无法保存备份压缩包：%1$s |
| `backup_restored` | Scripts restored: %1$d. | 已恢复 %1$d 篇稿件。 |
| `backup_skipped` | Skipped (empty or too long): %1$d. | 已跳过 %1$d 篇稿件（内容为空或过长）。 |
| `settings_section_fullscreen` | Full-screen prompter | 全屏提词器 |
| `settings_mirror_title` | Mirrored text | 文字镜像 |
| `settings_section_scripts` | Scripts | 稿件 |
| `settings_backup_title` | Save all scripts | 备份全部稿件 |
| `settings_backup_hint` | A zip with a Markdown file per script: for a new phone or a reinstall | 保存为 ZIP 压缩包，每篇稿件对应一个 Markdown 文件，方便换机或重装后恢复 |
| `settings_restore_title` | Restore from archive | 从备份恢复 |
| `settings_restore_hint` | Adds the scripts from such a zip; the ones you have stay | 从备份 ZIP 压缩包中添加稿件，保留现有稿件 |
| `settings_link_site` | Website | 网站 |
| `settings_link_code` | Source code | 源代码 |
| `settings_link_issue` | Report a problem | 反馈问题 |
| `settings_link_issue_hint` | On GitHub; attach the log from Settings, About, Log, Share | 在 GitHub 上反馈，请附上“设置 → 关于 → 日志 → 分享”导出的日志 |
| `settings_link_donate` | Support the project | 支持项目 |
| `settings_link_donate_hint` | Boosty, crypto: donations pay for new languages and test phones | 通过 Boosty 或加密货币捐赠，用于支持新增语言和购买测试手机 |
| `settings_about_license` | Free software under GPL-3.0, provided without warranty. | 基于 GPL-3.0 的自由软件，不提供任何担保。 |
| `editor_stats` | %1$s · ≈%2$s at %3$d wpm | %1$s · 按 %3$d 字词/分钟估算约 %2$s |
| `journal_crash_title` | The last run ended with a crash | 上次运行发生了崩溃 |
| `journal_crash_hint` | The report is kept on this phone. Recognized speech, app names and script titles are not in it. | 报告保存在本机，不包含识别的语音、应用名称或稿件标题。 |
| `journal_crash_share` | Share report | 分享报告 |
| `settings_section_advanced` | For advanced users | 高级设置 |
| `settings_sum_ready_all` | Everything is set | 已全部设置完成 |
| `settings_sum_ready_part` | Done %1$d of %2$d: tap to finish | 已完成 %1$d / %2$d 项，点击继续设置 |
| `settings_sum_overlay` | Lines: %1$d · text %2$d · background %3$d%% | 行数：%1$d · 字号：%2$d · 背景：%3$d%% |
| `settings_sum_fullscreen` | Text %1$d · mirror: %2$s | 字号：%1$d · 镜像：%2$s |
| `settings_sum_voice` | Speech language: %1$s | 语音识别语言：%1$s |
| `settings_sum_timed` | %1$d wpm · countdown: %2$s | %1$d 字词/分钟 · 倒计时：%2$s |
| `settings_sum_text_phrases` | Phrases of up to %1$d words | 每个短语最多 %1$d 个字词 |
| `settings_sum_text_paragraphs` | Paragraphs as in the text | 保留原文分段 |
| `settings_sum_keys_on` | On | 开启 |
| `settings_sum_keys_off` | Off | 关闭 |
| `settings_sum_scripts` | Save all to an archive or restore | 备份全部稿件或从备份恢复 |
| `settings_sum_advanced` | Microphone source and rate, diagnostics line | 麦克风来源、采样率和诊断信息 |
| `settings_sum_about` | Version %1$s · log · links | 版本 %1$s · 日志 · 链接 |
| `settings_overlay_page_hint` | The same can be changed right over the camera with the Aa button in the window. | 也可点击悬浮窗中的 Aa 按钮，在相机画面上方直接调整。 |
| `settings_fullscreen_page_hint` | The rehearsal screen: for practice, or for the phone under teleprompter glass. The same is under View on the rehearsal screen. | 用于排练或放在提词器反射玻璃下方，也可在排练界面的“显示”中调整。 |
| `settings_mirror_pick_hint` | Pick the one in which the text reads the right way in the glass. | 请选择能让反射玻璃中的文字方向正确的模式。 |
| `settings_speech_lang_default_title` | Default speech language | 默认语音识别语言 |
| `settings_speech_lang_default_hint` | For scripts where no language is chosen on the script screen. | 用于未在稿件详情中单独指定语言的稿件。 |
| `settings_timed_page_hint` | For the Speed and Sound modes and the length estimate. Speed also changes on the script screen and in the window. | 用于定速模式、声音模式和时长估算，也可在稿件详情及悬浮窗中调整速度。 |
| `settings_scripts_page_hint` | The same is in the ⋮ menu of the script list. | 稿件列表的 ⋮ 菜单中也提供这些操作。 |
| `mirror_off` | Off | 关闭 |
| `mirror_horizontal` | Horizontal | 水平 |
| `mirror_vertical` | Vertical | 垂直 |
| `mirror_both` | Both | 水平和垂直 |
| `speed_value` | %1$d wpm | %1$d 字词/分钟 |
| `script_lang_chip` | Speech: %1$s | 语音：%1$s |
| `script_lang_sheet_title` | Speech language of this script | 此稿件的语音识别语言 |
| `script_lang_sheet_hint` | The speech model of this language listens, and the words are matched and counted by its rules. | 使用所选语言的语音模型进行识别，并按该语言的规则匹配和统计字词数。 |
| `script_lang_follow` | As in Settings (%1$s now) | 跟随设置（当前：%1$s） |
| `script_speed_sheet_title` | Speed without voice | 非语音跟随滚动速度 |
| `script_speed_sheet_hint` | For the Speed and Sound modes and the length estimate. | 用于定速模式、声音模式和时长估算。 |
| `library_cd_menu` | More | 更多 |
| `library_add_archive` | From a backup | 从备份导入 |
| `library_add_archive_hint` | A zip saved with Save all scripts | 通过“备份全部稿件”保存的 ZIP 压缩包 |
| `rehearsal_view` | View | 显示 |
| `scroll_mode_voice` | Voice | 语音 |
| `scroll_mode_auto` | Speed | 定速 |
| `scroll_mode_sound` | Sound | 声音 |
| `speed_unit` | words per minute | 字词/分钟 |

## 开发版名称覆盖

| 资源名 | 英文原文 | 中文译文 |
| --- | --- | --- |
| `app_name` | Suflyor dev | Suflyor 提词器（开发版） |
| `a11y_label` | Suflyor dev: floating window and voice | Suflyor 开发版：悬浮窗与语音 |
| `tile_label` | Suflyor dev | Suflyor 提词器（开发版） |
