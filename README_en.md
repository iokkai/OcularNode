# OcularNode 👁️

[English](README_en.md) | [正體中文](README.md)

[![Android CI](https://github.com/iokkai/OcularNode/actions/workflows/android.yml/badge.svg)](https://github.com/iokkai/OcularNode/actions)
[![Development Status](https://img.shields.io/badge/Status-Under%20Active%20Development-orange.svg)](https://github.com/iokkai/OcularNode)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

> 📱 **Transform spare Android smartphones into dedicated, high-performance smart surveillance cameras.**

> [!WARNING]
> ⚠️ **Project Under Active Development (WIP / Experimental)**  
> This project is under intensive development. **All features are currently experimental, unstable, and subject to breaking changes.** It is not intended for mission-critical security setups. Feedback, issues, and PRs are warmly welcome!

> [!IMPORTANT]
> 🔒 **100% Zero Central Server・Maximum Privacy Guarantee**  
> **OcularNode operates without any external central or cloud servers!**  
> All live video streams, event recordings, and two-way audio communications are **strictly transmitted directly within your local home network (LAN) or protected through Google WebRTC P2P end-to-end encrypted connections**. Your camera feed is never routed through third-party cloud relays.

---

## 🌟 Core Features

- 🛡️ **Zero Central Server & 100% Decentralized Architecture**:
  - No dependency on vendor cloud platforms, subscription fees, or cloud leakage risks.
  - Direct WebRTC P2P / local connection between Viewer and Camera Node with full offline availability.
- 🌐 **Dual Connection Modes (Local LAN Direct & WebRTC P2P Traversal)**:
  - **🏠 Home Wi-Fi Direct (LAN)**: Connect directly over local Wi-Fi (`192.168.x.x` and IPv6) with zero external dependency, ultra-low latency, and complete offline privacy.
  - **⚡ WebRTC P2P Remote Direct Streaming**: Standard WebRTC (ICE / STUN NAT traversal with DTLS-SRTP end-to-end encryption) enabling millisecond-level low-latency direct streaming without exposing public IPs or configuring router port forwarding.
- 🪄 **Device Owner Zero-Touch Provisioning Wizard**:
  - Fast network setup via dedicated QR code scanning with auto-configured E2EE pairing keys.
  - Dedicated Kiosk lock mode, auto-start on boot, and customized screen-off power-saving monitoring.
- ⚡ **Dual-Mode Architecture**:
  - **Camera (Node Mode)**: Low-power background WebRTC / MJPEG HTTP streaming service, two-way live intercom, auto night vision switching.
  - **Viewer (Monitor Mode)**: Multi-camera live grid view, low-latency WebRTC playback, remote camera configuration, and live audio broadcast.
- 🧠 **Edge AI Motion Detection**:
  - Frame-differential pixel analysis.
  - Google ML Kit object recognition and categorization filter (human, animal, vehicle, etc.).
  - Automated pre-record buffer and event video generation.
- 📲 **Telegram Bot Alerts**:
  - Instant motion alerts with photo snapshot and video clip delivery.
  - Power cut (unplugged) / power restored alerts and low-battery warnings.
  - High-temperature thermal throttling protection (45°C) alert and recovery notification.
- 🔄 **Silent OTA Updates**: Direct integration with GitHub Releases to check and update seamlessly in the background.

---

## 🏗️ System Architecture

```mermaid
graph TD
    A[CameraStreamService] --> B[BatteryPowerMonitor]
    A --> C[ScheduleManager]
    A --> D[StorageCleanupManager]
    A --> E[MotionPipelineManager]
    A --> F[RemoteCommandHandler]
    
    E --> G[ML Kit AI Filter]
    E --> H[EventVideoRecorder]
    E --> I[TelegramNotifier]
    E --> J[Room AppDatabase]
    
    F --> K[MjpegHttpServer / Web API]
    F --> L[CameraManagerHelper]
    F --> M[WebRtcServerManager]
```

- **State & Data Persistence**: Jetpack DataStore + EncryptedSharedPreferences (Hardware MasterKey encrypted credentials) + Room (Incremental non-destructive migrations).
- **UI Architecture**: Jetpack Compose + Material 3 Design System (Centralized Semantic Design Tokens).

---

## 🛠️ Build & Development

### Requirements
- **Android Studio**: Ladybug / Meerkat (or latest version)
- **JDK**: Java 17+ (Recommended: Android Studio Embedded JBR)
- **Min SDK**: API 24 (Android 7.0)
- **Target SDK**: API 36 (Android 16)

### Gradle CLI Commands

```bash
# Run unit test suite
./gradlew testArmDebugUnitTest

# Build Debug APK (ARM Architecture)
./gradlew assembleArmDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 🔐 Security & Privacy

1. **Zero Central Server**: Fully decentralized architecture. No central cloud servers, relay stations, or intermediate proxies.
2. **LAN-Only & WebRTC P2P Protection**: Video and audio streams are broadcast strictly within your local home network (LAN) by default. Remote connections are established over private, end-to-end encrypted WebRTC P2P (DTLS-SRTP).
3. **HTTP PIN Authentication**: Protects streaming endpoints, recordings, and microphone access against unauthorized local network access.
4. **Hardware-Backed Credential Security**: Telegram Bot Token and pairing secrets are encrypted using Android Jetpack `EncryptedSharedPreferences` backed by the Android KeyStore hardware master key.
5. **Prevent Data Extraction**: Global `allowBackup` is disabled to prevent unauthorized ADB physical backup extractions.
6. **Zero Trackers & Ads**: Zero commercial tracking SDKs, analytics, or third-party advertisements. Complete user privacy guaranteed.

---

## 📜 License & Privacy
 
* Software License: [GNU General Public License v3.0 (GPL-3.0)](LICENSE)
* Privacy Policy & Terms of Use: [PRIVACY.md](PRIVACY.md)
