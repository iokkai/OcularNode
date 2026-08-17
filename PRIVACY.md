# Privacy Policy & Terms of Use for OcularNode

*Last updated: August 17, 2026*

[正體中文版 (Traditional Chinese)](#正體中文版) | [English Version](#english-version)

---

## English Version

### 1. Overview & Acceptance of Terms
**OcularNode** is an open-source, decentralized smart camera and monitoring application designed to repurpose Android devices into secure video nodes. 

**Acceptance of Terms**: By downloading, installing, accessing, or using OcularNode, you acknowledge that you have read, understood, and agreed to be bound by this Privacy Policy and Terms of Use. If you do not agree with any part of these terms, please do not install or use this application.

Our core privacy principle is simple: **We do not collect, store, transmit, or monetize your personal data, video feeds, or credentials.** All video processing, machine learning detection, and media storage occur **100% locally on your device** or through direct point-to-point (P2P) encrypted connections.

---

### 2. Permissions & Data Usage
OcularNode requests only the permissions necessary to provide video monitoring capabilities:

* **Camera (`android.permission.CAMERA`)**: Used exclusively to capture live video feeds, detect motion, and run on-device machine learning analysis.
* **Microphone (`android.permission.RECORD_AUDIO`)**: Used solely for streaming and recording ambient audio during video surveillance when enabled by the user.
* **Notifications (`android.permission.POST_NOTIFICATIONS`)**: Used to display persistent system status-bar notifications for foreground services (ensuring Android does not terminate continuous camera monitoring in the background) and pairing status. *(Note: Motion detection and power cut alerts are delivered remotely to your private Telegram account via Telegram Bot API).*
* **Boot & Foreground Services (`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`)**: Allows the application to automatically resume continuous 24/7 monitoring when the device starts up.
* **Device Owner / Kiosk Mode (`BIND_DEVICE_ADMIN`)**: Optional capability allowing dedicated camera hardware to stay awake, auto-reboot, and prevent OS-level background sleep.

---

### 3. Local Storage & Hardware-Backed Security
* **Media & Event Recordings**: All snapshots and recorded video clips are saved strictly within the device's internal storage or scoped app storage. No files are uploaded to any developer-operated servers.
* **Sensitive Credentials**: Authentication tokens (including Telegram Bot Tokens, Chat IDs, and Tailscale API Keys) are encrypted using Jetpack Security's `EncryptedSharedPreferences` backed by the **Android KeyStore (AES256-GCM / AES256-SIV)**.
* **Data Extraction Prevention**: Physical ADB backups are globally disabled (`android:allowBackup="false"`).

---

### 4. Third-Party Integrations (Bring Your Own Key)
* **Tailscale (Mesh VPN)**: For cross-network P2P streaming. Video streams travel directly between your authorized devices over WireGuard-encrypted tunnels.
* **Telegram Bot API**: For sending push alerts and snapshot photos to your private chat. All requests are sent directly from your device to `api.telegram.org` over HTTPS.
* **Google ML Kit**: All image classification and object detection models run **offline on your device**. No images or biometric information are ever sent to Google.

---

### 5. Legal Compliance & Surveillance Disclaimer
* **Compliance with Local Laws**: Users are solely responsible for ensuring that their use of OcularNode complies with all applicable local, national, and international laws, including wiretapping, audio recording, and privacy consent regulations. OcularNode must not be used for unauthorized surveillance.
* **Non-Certified Equipment (AS-IS)**: OcularNode is provided under the **GNU General Public License v3.0** on an "AS IS" basis. It is designed as an upcycling utility and is not a certified life-safety, fire alarm, or UL-certified commercial security system. The author assumes no liability for power failure, hardware degradation, monitoring interruption, or property damages.
* **Battery, Thermal & Hardware Safety**: OcularNode is designed for continuous 24/7 operation. Operating a device while continuously connected to power may accelerate battery aging, thermal build-up, or swelling. Users must ensure that devices are placed in well-ventilated environments, kept away from flammable materials, and are advised to utilize smart timer plugs or enable device-level charging protection limits. While advanced maker communities may discuss hardware modifications (such as lithium battery removal for direct DC power), these practices carry inherent electrical and safety risks, are not officially endorsed, and are not recommended for untrained individuals. Any hardware alterations are strictly at the user's own discretion and risk; the author provides no instructions and assumes no liability for damages.
* **Dedicated Hardware & Device Owner Disclaimer**: The 24/7 Dedicated Device mode (Device Owner / Kiosk) is designed exclusively for repurposed, dedicated hardware. Provisioning Device Owner privileges grants deep system-level controls which may restrict normal phone operations or require factory resetting. Do not enable this mode on your primary personal smartphone. Users assume all responsibility and risks associated with device provisioning.
* **Non-Medical & Infant Care Disclaimer**: OcularNode is not a medical device and is not certified for infant safety, vital sign monitoring, or elder fall detection. Network latency, operating system sleep states, or power outages may delay or interrupt video feeds and push alerts. It must never be relied upon as the sole monitoring or safety measure for human life, health, or infant care.

---

### 6. Contact & Source Code
OcularNode is 100% open source. You can inspect the complete source code and report issues on GitHub:
* **GitHub Repository**: [https://github.com/iokkai/OcularNode](https://github.com/iokkai/OcularNode)
* **License**: GNU General Public License v3.0

---

## 正體中文版

### 1. 隱私核心原則與條款接受
**OcularNode** 是一款開源且去中心化的智慧舊機轉生監控系統。

**條款之同意與接受**：當您下載、安裝、存取或使用 OcularNode，即代表您已完整閱讀、理解並同意接受本隱私權政策與使用條款之所有規範。若您不同意本條款之任何內容，請勿安裝或使用本應用程式。

我們的隱私政策核心原則非常明確：**我們絕不收集、上傳、存儲或轉售您的任何個人資料、影像畫面或認證金鑰。** 所有影像運算、邊緣 AI 辨識與影片儲存均 **100% 在您的本地裝置** 上完成，或透過點對點 (P2P) 加密通道直接傳輸。

---

### 2. 系統權限與使用目的
OcularNode 僅要求執行監控功能所必需的權限：

* **相機權限 (`CAMERA`)**：僅用於即時視訊預覽、動態移動偵測及本地邊緣 AI 辨識。
* **麥克風權限 (`RECORD_AUDIO`)**：僅在使用者啟用聲音錄製時，用於監控錄影之音訊收音。
* **通知權限 (`POST_NOTIFICATIONS`)**：用於在裝置狀態列顯示前台服務常駐通知（確保 Android 系統不會在背景中止相機監控服務）及 Telegram 配對進度提示。*（備註：動態偵測與斷電告警係透過 Telegram Bot API 直接遠端推播至使用者之 Telegram 帳號）*。
* **開機自啟與前台服務 (`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`)**：確保監控節點於設備重開機時能自動復原 24/7 全時守護。
* **裝置管理員／Kiosk 模式 (`BIND_DEVICE_ADMIN`)**：可選權限，供專用監控舊機啟用防休眠、防當機自動重啟等專用設備功能。

---

### 3. 本地儲存與硬體加密安全
* **監控影像與檔案**：所有警報照片與事件錄影皆存放在裝置本機內部儲存空間，絕不上傳至任何開發者伺服器。
* **機密憑證防護**：使用者設定之 Telegram Bot Token、Tailscale API Key 及 HTTP PIN 碼，均採用 Android KeyStore 結合 `EncryptedSharedPreferences` 進行 **AES256 硬體級加密儲存**。
* **防止實體提取**：全域停用 `android:allowBackup="false"`，杜絕未經授權的 ADB 實體備份提取。

---

### 4. 第三方服務直連規範 (BYOK)
OcularNode 支援使用者自主設定（Bring Your Own Key）第三方服務：
* **Tailscale**：用於跨網點對點 (P2P) 安全穿透。所有影像串流皆直接在您的受信任節點間傳輸，不經由第三方伺服器中轉。
* **Telegram Bot API**：用於發送警報推播與照片至您的私人頻道。所有請求均由本機直接以 HTTPS 連向 Telegram 官方伺服器。
* **Google ML Kit**：所有物體與寵物過濾辨識模型均在 **裝置端離線運算**，絕無任何影像資料上傳至 Google 雲端。

---

### 5. 法律遵從、非專業保全與安全免責聲明
* **當地隱私與法規遵從**：使用者於裝設及使用本軟體時，應自行確保符合當地相關法令規範（包含但不限於隱私權保護、個人資料保護法及防妨害秘密錄音錄影相關規定），嚴禁用於任何未經授權之偷拍或非法監控用途。
* **非專業認證保全設備**：本軟體依 **GNU GPL v3.0** 協議依「現狀 (AS-IS)」提供。本專案為舊機再利用之輔助工具，非專業認證之防盜、防災或保全系統。開發者不對因硬體老化、作業系統休眠、斷電、斷網或第三方 API 異常所致之監控中斷或財物損失承擔任何賠償責任。
* **電池健康、過熱與硬體安全免責**：本軟體設計為 24/7 全天候運作。長時間插電可能加速電池老化、發熱或膨脹。使用者應確保設備處於通風良好環境，遠離可燃物。建議搭配智慧定時插座或開啟裝置內建之電池保護上限。部分硬體社群所探討之進階改裝（如卸除鋰電池改為直供電）具有電氣與安全風險，非官方認可且不推薦未經專業訓練者嘗試；任何硬體改裝純屬使用者自主行為，衍生之安全與損壞風險須完全自負，開發者不提供指引亦不承擔任何責任。
* **專用舊機與系統管理模式免責**：「24/7 專用設備模式 (Device Owner / Kiosk)」專為閒置淘汰之專用舊手機設計。啟用系統管理權限可能導致裝置功能受限、停用常規設定或需重置裝置。請勿在日常主力個人手機上設定此模式，使用者須自行評估並承擔相關系統管理操作風險。
* **非醫療與非嬰幼兒看護設備宣告**：本軟體非醫療器材，亦非經認證之嬰幼兒生命安全、防猝死或長者跌倒看護監視系統。網路延遲、背景排程或硬體斷電皆可能導致串流或警報中斷，嚴禁作為生命照護或人身安全之唯一監護手段。

---

### 6. 開源協議與聯繫管道
OcularNode 為 100% 開源專案，歡迎查閱完整原始碼或提出建言：
* **GitHub 專案倉庫**：[https://github.com/iokkai/OcularNode](https://github.com/iokkai/OcularNode)
* **軟體授權條款**：GNU General Public License v3.0
