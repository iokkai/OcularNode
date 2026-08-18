# OcularNode 系統架構規格書 (Architecture Specification Whitepaper)

> **版本**：v1.0.0 (Production-Ready)  
> **定位**：無人值守 P2P 去中心化智慧安防監控節點系統 (100% Zero-Central-Server & Zero-Touch)  
> **授權**：GNU General Public License v3.0 (GPL-3.0)

---

## 📑 目錄 (Table of Contents)
1. [系統總覽與設計哲學](#1-系統總覽與設計哲學)
2. [第一階段：基礎架構與權限分流 (Unified APK & Device Owner)](#2-第一階段基礎架構與權限分流-unified-apk--device-owner)
3. [第二階段：網路與 Tailscale 連線層 (P2P Mesh & Zero-Touch VPN)](#3-第二階段網路與-tailscale-連線層-p2p-mesh--zero-touch-vpn)
4. [第三階段：影音擷取與 WebRTC P2P 串流層 (MediaCodec & Signaling)](#4-第三階段影音擷取與-webrtc-p2p-串流層-mediacodec--signaling)
5. [第四階段：邊緣 AI 運算、事件偵測與在地存儲層 (Edge AI & Storage)](#5-第四階段邊緣-ai-運算事件偵測與在地存儲層-edge-ai--storage)
6. [全系統架構資料流圖 (System Data Flow Diagram)](#6-全系統架構資料流圖-system-data-flow-diagram)
7. [安全防護、容錯與自癒機制 (Resilience & Watchdogs)](#7-安全防護容錯與自癒機制-resilience--watchdogs)

---

## 1. 系統總覽與設計哲學

OcularNode 旨在將閒置的舊 Android 智慧型手機轉化為工業級、高度自主且無人值守的 P2P 智慧監控攝影機節點（Camera Node），並相容一般手機作為監控端（Viewer Node）。

### 核心設計原則：
* **100% 零中央伺服器 (Zero Central Server)**：不依賴任何外部雲端中繼主機，保障 100% 數位主權與隱私。
* **零接觸部署 (Zero-Touch Provisioning)**：利用 Android Device Owner (DO) MDM 機制，掃描 QR Code 後全程自動化配置。
* **資源受限防護 (Hardware-Conscious / Anti-Thermal)**：針對舊手機散熱不良、電池老化等痛點，建立多層次溫控與動態降頻保護。
* **無人值守自我修復 (Self-Healing Watchdogs)**：斷網、斷電重啟、程序被殺皆能自癒復原。

---

## 2. 第一階段：基礎架構與權限分流 (Unified APK & Device Owner)

### 2.1 單一安裝包角色分流 (Unified APK Routing)
* **單一二進位包 (Single APK)**：鏡頭端與觀看端共用同一份 APK。
* **自動分流機制**：
  * App 啟動時透過 `DevicePolicyManager.isDeviceOwnerApp(packageName)` 進行特權判定。
  * **Device Owner = true**：強制固定為 `CAMERA` 模式，跳過使用者角色選擇畫面，立即進入相機服務初始化管線。
  * **Device Owner = false**：預設進入 `VIEWER` 模式，使用者可手動切換至相機模式或觀看模式。

### 2.2 鎖定模式 (Kiosk Mode / Lock Task Mode)
* **Kiosk 螢幕死鎖**：
  * 鏡頭端在啟動後透過 `dpm.setLockTaskPackages(admin, arrayOf(packageName))` 註冊白名單，並呼叫 `activity.startLockTask()`。
  * 徹底禁用系統導覽列（Home、Back、Overview/Recents），屏蔽通知列下拉與系統對話框。
* **管理員逃生門 (Escape Hatch)**：
  * UI 隱藏區域連續點擊觸發管理員密碼驗證彈窗。
  * 驗證通過後呼叫 `activity.stopLockTask()`，允許工程人員進行本機除錯或維護。
* **偽黑屏防烙印模式 (OLED Burn-in Protection / Pure Black)**：
  * 鏡頭端提供全黑省電覆蓋層，關閉背光或以全黑像素運作，大幅降低發熱並防止 OLED 螢幕烙印，背景相機管線維持全速運作。

### 2.3 靜默權限核准 (Silent Dangerous Permissions Grant)
* **免彈窗全自動授權**：利用 `dpm.setPermissionGrantState()` 靜默核准：
  * `android.Manifest.permission.CAMERA`
  * `android.Manifest.permission.RECORD_AUDIO`
  * `android.Manifest.permission.POST_NOTIFICATIONS` (Android 13+)
  * `android.Manifest.permission.ACCESS_FINE_LOCATION`
* **電池最佳化豁免 (Battery Optimization Bypass)**：
  * DO 模式下抑制系統設定引導彈窗，透過前景服務 (`ForegroundService`) 與 `PARTIAL_WAKE_LOCK` 實現永久常駐。

---

## 3. 第二階段：網路與 Tailscale 連線層 (P2P Mesh & Zero-Touch VPN)

### 3.1 QR Code 部署參數傳遞 (Provisioning Bundle)
* 出廠重置掃碼時，透過 `EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE` 傳遞 JSON 參數：
  * `tailscale_auth_key`：Tailscale 預先授權之 Reusable + Ephemeral AuthKey。
  * `wifi_ssid` / `wifi_password`：Wi-Fi 自動連網參數。
  * `github_owner` / `github_repo`：靜默 OTA 更新來源儲存庫。
* **雙通道接收相容性**：
  * 傳統/廣播：`AdminReceiver.onProfileProvisioningComplete`。
  * Android 10+ 前景轉接：`AdminPolicyComplianceActivity`。

### 3.2 Tailscale MDM 政策注入與漸進式 Always-On VPN
* **靜默安裝**：透過 Android `PackageInstaller` API 背景靜默安裝 Tailscale APK。
* **MDM 政策注入**：
  ```kotlin
  val restrictions = Bundle().apply {
      putString("AuthKey", authKey)
  }
  dpm.setApplicationRestrictions(adminComponent, "com.tailscale.ipn", restrictions)
  ```
* **漸進式 Always-On VPN (Progressive Lockdown)**：
  * **Phase A (常態/初始化)**：`dpm.setAlwaysOnVpnPackage(admin, "com.tailscale.ipn", false)`。避免未握手前全面阻斷公網導致死鎖（Bootstrapping Trap）。
  * **Phase B (嚴格安全/可配置)**：當本機成功取得 `100.64.0.0/10` 虛擬 IP 且隧道就緒後，可動態升級啟用 `lockdown = true`。

### 3.3 前景保活服務與網路看門狗 (Keep-Alive & Network Watchdog)
* **NAT 穿透保活 (Heartbeat Pinger)**：
  * 前景服務以 15~25 秒為週期，向 Tailnet 網段或觀看端發送輕量 UDP/ICMP 心跳，防止路由器 NAT 映射表逾時過期。
* **看門狗自癒 (Watchdog)**：
  * 實時監測 VPN 虛擬介面狀態，若 VPN 崩潰或 Wi-Fi 斷線重連，自動觸發重啟與政策重注入。

---

## 4. 第三階段：影音擷取與 WebRTC P2P 串流層 (MediaCodec & Signaling)

### 4.1 內嵌去中心化信令伺服器 (Embedded Ktor WebSocket Server)
* **100% Zero-Server 架構**：捨棄外部 STUN/TURN/信令伺服器，鏡頭端內嵌輕量 Ktor WebSocket 服務。
* **直連交換**：觀看端透過 Tailscale IP (`100.x.y.z:port`) 直連鏡頭端交換 SDP 與 ICE Candidates。
* **存取安全**：信令握手採用 Token/金鑰驗證；設定最大並行連線數（2~3 Clients），防止硬體超載。

### 4.2 H.264 硬體編碼與溫控動態降級 (Thermal & Bandwidth Throttling)
* **強制 MediaCodec 硬體壓碼**：全面禁止軟體編碼（VP8/VP9），將 CPU 佔用控制於極低水平。
* **雙維度自適應降級**：
  * **溫度監控 (`BatteryManager.EXTRA_TEMPERATURE` / Thermal API)**：
    * `<40°C`：1080p @ 30fps (高畫質全速)。
    * `40°C~44°C`：自動降級至 720p @ 20fps，碼率減半。
    * `>45°C`：降級至 480p @ 10fps，暫停次要 AI 推論，強制保護電池與晶片。
  * **網路頻寬自適應**：依據 WebRTC 封包遺失率 (Packet Loss) 即時向下動態調整 Bitrate。

### 4.3 雙向語音與回音消除 (Acoustic Echo Cancellation)
* **通話模式**：指定 `MediaRecorder.AudioSource.VOICE_COMMUNICATION` 啟用晶片硬體 DSP。
* **音訊濾波**：強制掛載 `AcousticEchoCanceler` (AEC) 與 `NoiseSuppressor` (NS)。
* **防嘯叫操作**：UI 支援 Push-to-Talk（按住說話）半雙工模式，發話時自動靜音鏡頭端麥克風，杜絕聲學正回授。

---

## 5. 第四階段：邊緣 AI 運算、事件偵測與在地存儲層 (Edge AI & Storage)

### 5.1 雙層節流事件偵測管線 (Two-Tier Detection Pipeline)
```mermaid
graph TD
    A[Camera Frame Stream] --> B[Downsample & Grayscale (160x120)]
    B --> C[Tier 1: Frame Differencing (3-5 fps)]
    C -- No Motion (< threshold) --> D[Sleep / Discard]
    C -- Motion Detected (>= threshold) --> E[Tier 2: Wake up ML Kit / TFLite]
    E --> F{Object Classification}
    F -- Human / Pet / Target --> G[Trigger Event Alert & Video Recording]
    F -- False Positive / Noise --> H[Cooldown & Sleep]
```

### 5.2 循環錄影與 5 秒預錄緩衝 (Circular Pre-roll Buffer & Storage Quota)
* **5 秒記憶體環狀緩衝 (Ring Buffer)**：
  * 以 `ArrayDeque` 常駐保存過去 5 秒影音幀（約 75 幀）。
  * 事件觸發時，將「事發前 5 秒 + 事發中 + 事發後 10 秒（連續動態可延長至 3 分鐘）」寫入 MP4。
* **FIFO 磁碟配額管理**：
  * 設定硬體配額上限（如 5GB），空間不足時自動先進先出清理最舊的錄影與快照。

### 5.3 零雲端推播與離線喚醒策略 (Zero-Server Push Notifications)
* **核心層 (方案 A：Tailnet 內網直連廣播)**：
  * 鏡頭端透過 WebSocket 直連發送 JSON Alert，毫秒級抵達處於前景或 Keep-Alive 狀態的觀看端。
* **擴展層 (離線 Webhook 喚醒)**：
  * 支援 Telegram Bot / Discord / ntfy 等外部 Webhook 通道，在觀看端處於深度凍結休眠時傳遞零個資警報與截圖。

---

## 6. 全系統架構資料流圖 (System Data Flow Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 部署人員 / 使用者
    participant Setup as Android SetupWizard / QR
    participant App as OcularNode (Camera Node)
    participant TS as Tailscale VPN (com.tailscale.ipn)
    participant Viewer as OcularNode (Viewer Node)
    participant TG as Telegram / Webhook (Optional)

    Note over Admin, Setup: 階段一與二：零接觸部署與網路組網
    Admin->>Setup: 掃描部署 QR Code (含 AuthKey & Wi-Fi)
    Setup->>App: 賦予 Device Owner 權限 (AdminPolicyCompliance)
    App->>App: 靜默核准相機/錄音權限 + 進入 Kiosk Lock Task
    App->>TS: 靜默安裝 APK + 注入 AuthKey + 啟用 Always-On VPN
    TS-->>App: 建立 WireGuard 虛擬網卡 (100.64.0.0/10)

    Note over App, Viewer: 階段三：影音串流與直連信令
    App->>App: 啟動 Ktor WebSocket 信令伺服器 & H.264 硬體編碼
    Viewer->>App: 透過 100.x.y.z 直連 WebSocket (交換 SDP / ICE)
    App-->>Viewer: WebRTC P2P 加密視訊與雙向語音串流 (AEC/NS)

    Note over App, TG: 階段四：邊緣 AI 偵測與警報
    App->>App: Tier 1 像素差分偵測到動態 -> 喚醒 Tier 2 ML Kit 人物辨識
    App->>App: 觸發錄影 (合併前置 5 秒 Ring Buffer 存檔 MP4)
    App->>Viewer: Tailnet 內網廣播即時警報 (JSON Alert)
    opt 啟用外網通知
        App->>TG: 發送事件快照與警報訊息
    end
```

---

## 7. 安全防護、容錯與自癒機制 (Resilience & Watchdogs)

| 異常情境 | 系統偵測機制 | 自動自癒與防護動作 |
| :--- | :--- | :--- |
| **市電斷電 / 異常重開機** | `BootAndPowerReceiver` 監聽開機與電源廣播 | 開機自動拉起前景服務，重設 `setLockTaskPackages` 並自動恢復 Kiosk 鎖定與相機串流。 |
| **機身過熱 (>45°C)** | `BatteryManager` 溫度廣播與 Thermal API | 動態降低幀率至 10fps、解析度降至 480p，暫停 AI 推論，透過 Webhook 發送過熱預警。 |
| **Tailscale VPN 異常中斷** | `Keep-Alive Pinger` 連續探測失敗 | Network Watchdog 自動呼叫重啟指令並重新注入 MDM Restrictions。 |
| **儲存空間耗盡** | `StorageCleanupManager` 磁碟餘量監測 | 依照 FIFO 原則自動批次刪除最舊的事件影片，保留系統安全運作空間。 |
| **程式崩潰 / 記憶體被殺** | 前景常駐通知服務 (`ForegroundService`) | 系統自動以 `START_STICKY` 重新調度拉起服務並重置狀態機。 |

---
*文件產出時間：2026-08-18 | OcularNode 核心架構白皮書*
