# Privacy

- **Document scanning.** Camera/gallery images are decoded and recognized locally by bundled Tesseract models, including on first use without a network. Images and OCR results are never uploaded. The system camera receives access to a single temporary photo through FileProvider; the system document picker grants access only to the selected image. Successful recognition or leaving the scanner deletes the temporary photo; review drafts remain private until saved or discarded. No broad photo-library permission is requested.

**Speech recognition stays on your phone. Network access is optional and used only for an online script catalog you configure.**

- **Network.** Starting the app with a configured catalog URL, saving that URL or choosing Sync sends an HTTP GET request to that address (and its redirects). The server receives the normal connection metadata, such as your IP address. No microphone audio, recognized phrases or local scripts are uploaded. HTTPS is recommended; HTTP is also supported for local servers. Cached scripts work offline, and clearing the URL disables synchronization.
- **Microphone.** Audio is used only while the prompter listens: during a session over another app or a rehearsal inside the app. During a session over another app, a notification is shown (on Android 13 and newer only if you allowed notifications; otherwise the session is listed among active apps in Quick Settings). On Android 12 and newer the system also shows its microphone indicator. The speech model built into the app turns the sound into words. Audio is never recorded to a file, stored or sent anywhere.
- **Accessibility service.** Its shortcut button opens or closes the floating prompter without disabling the service. During a session over another app:
  - Android keeps giving the prompter the microphone while another app (the camera, 剪映, 抖音, 快手, 小红书) records video;
  - the floating window is shown;
  - volume and remote-control keys move the text. This is on by default with a preset set of keys, and you can turn it off or reassign the keys in Settings → Remote and buttons.

  It **cannot read screen content** (`canRetrieveWindowContent="false"`). The only thing it notices is the name of the app in front, which goes to the in-app journal. Between sessions it receives no key presses and ignores window events.
- **Display over other apps** (optional). This permission is only a fallback for showing the prompter window when the accessibility service is off. It gives no access to what is on the screen.
- **Your scripts** are stored in the app's private storage on the phone and are excluded from cloud backup. They leave the phone only if you share them yourself, or when Android's own phone-to-phone transfer moves your apps to a new device.
- **The log** (Settings → About → Log) keeps technical events in memory, including recognized phrases, so that problems can be diagnosed. It stays there until the app's process ends or you tap Clear, and it leaves the app only if you tap Share or Copy yourself. Release builds don't copy it to the system log. The bundled speech library may still write technical warnings there (for example, about audio resampling), but never your words.
- **Crash report.** If the app crashes or stops responding, it saves one report in its private folder: the error and the last log lines, without recognized phrases, app names or script titles. The log screen shows it; it leaves the phone only if you tap Share, and Delete removes it.
- **No analytics, no ads, no accounts, no tracking.** The app contains no third-party SDKs of this kind.

Questions: open an issue on GitHub.

---

# Конфиденциальность

Сканирование выполняется локально встроенным Tesseract, без сети с первого запуска. Снимки и распознанный текст не отправляются. Камера и системный выбор изображений дают доступ только к нужному файлу. Временный снимок удаляется после распознавания или выхода, черновик хранится в закрытой папке до сохранения или отмены.

**Распознавание речи работает на телефоне. Сеть используется только для указанного вами каталога сценариев.**

- **Сеть.** При запуске приложения с настроенным каталогом, сохранении ссылки или ручной синхронизации приложение отправляет GET-запрос по указанному адресу и перенаправлениям. Сервер получает обычные сетевые данные, включая IP-адрес. Звук, распознанная речь и локальные сценарии не отправляются. Рекомендуется HTTPS; для локального сервера поддерживается HTTP. Сохранённые сценарии доступны офлайн; пустая ссылка отключает синхронизацию.
- **Микрофон** работает только тогда, когда суфлёр слушает: во время сессии поверх другого приложения или репетиции внутри приложения. Во время сессии поверх другого приложения видно уведомление (на Android 13 и новее — если уведомления разрешены; иначе сессия видна в списке активных приложений в шторке). На Android 12 и новее система вдобавок показывает свой индикатор микрофона. Звук превращается в слова встроенной моделью прямо на телефоне. Он не записывается в файлы, не хранится и никуда не отправляется.
- **Служба специальных возможностей** работает только во время сессии поверх другого приложения. Она нужна для трёх вещей:
  - чтобы Android не отнимал микрофон, пока другое приложение снимает видео;
  - чтобы показывать окно поверх других приложений;
  - чтобы кнопки громкости и пульта управляли текстом. Это включено по умолчанию для стандартного набора кнопок; выключить или переназначить их можно в Настройках → «Пульт и кнопки».

  **Содержимое экрана она читать не может.** Она видит только название приложения на экране и пишет его в журнал. Между сессиями она не получает нажатий и не обрабатывает события окон.
- **Показ поверх других окон** (по желанию) — запасной способ показать окно суфлёра, если служба специальных возможностей выключена. Доступа к содержимому экрана это разрешение не даёт.
- **Сценарии** хранятся в закрытой папке приложения и не попадают в облачную резервную копию. Телефон они покидают, только если ты сам ими поделишься или перенесёшь приложения на новый телефон штатным переносом Android.
- **Журнал** хранится в памяти, в том числе распознанные фразы, пока процесс приложения не завершится или пока ты не нажмёшь «Очистить». Из приложения он уходит, только если ты сам нажмёшь «Поделиться» или «Скопировать». Релизная сборка не копирует журнал в системный лог. Встроенная библиотека распознавания может писать туда технические предупреждения, например о пересчёте частоты звука, но не твои слова.
- **Отчёт о сбое.** Если приложение упало или зависло, оно сохраняет в своей закрытой папке один отчёт: ошибку и последние строки журнала, без распознанных фраз, названий приложений и сценариев. Отчёт виден на экране журнала; с телефона он уходит, только если ты нажмёшь «Отправить отчёт», а «Удалить» его стирает.
- **Аналитики, рекламы, аккаунтов и слежки нет.**

---

# 隐私说明

扫描文稿使用 APK 内置的 Tesseract 引擎及简繁中文、英文模型，首次使用也支持断网识别。图片和 OCR 识别结果仅在本机处理，不上传。拍照调用系统相机并只授权当前临时照片；相册使用系统文件选择器，只读取选中的图片，不申请整个相册权限。识别成功或退出扫描页后删除临时照片，校对草稿保存在应用私有目录，保存或取消时删除；异常终止遗留的临时文件会在后续扫描时清理。

语音识别在手机本地完成，麦克风音频和识别结果不会上传。在线稿件功能在已配置链接的应用启动时、保存链接或点击更新时向你配置的地址发起 GET 请求；服务器可以看到 IP 地址等正常连接信息。推荐 HTTPS，也支持局域网 HTTP。链接保存在应用私有设置中，清空链接后停止同步，已缓存稿件保留。

无障碍快捷按钮仅控制提词悬浮窗的开关；服务不读取屏幕内容。提词时保持屏幕常亮，结束提词后恢复正常息屏行为，不修改手机的全局锁屏时间。
