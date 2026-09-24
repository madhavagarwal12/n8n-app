# Android Standalone n8n Server App

A standalone native Android application that runs a full instance of [n8n](https://n8n.io) (workflow automation) natively on non-rooted Android hardware (`arm64-v8a`).

---

## Features

- **PRoot Rootfs Sandbox**: Emulates Linux FHS on non-rooted Android without requiring Docker or custom kernel modules.
- **Zero-Config Web Access**: Direct IP (`http://<WIFI_IP>:5678`) and mDNS network discovery (`http://n8n-android.local:5678`).
- **Dynamic QR Code**: Generates real-time connection QR codes using ZXing for quick smartphone/tablet/laptop pairing.
- **Foreground Service Persistence**:
  - `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE_SPECIAL_USE` (Android 14+ compliant).
  - CPU `PARTIAL_WAKE_LOCK` and `WIFI_MODE_FULL_HIGH_PERF` to prevent drops when the screen is locked.
  - Ongoing notification with quick "Stop Server" control and live address.
- **Interactive UI (Jetpack Compose & Material 3)**:
  - Real-time setup / extraction progress tracker.
  - Live system stats (CPU/Memory usage, heap limit, server uptime).
  - Built-in Monospaced Console Log Viewer with log level filtering (`INFO`, `WARN`, `ERROR`), search, copy, and clear controls.

---

## Architecture Overview

```
[ Jetpack Compose UI ]
        |
        v
[ Foreground Service (WakeLock, WifiLock, NetworkCallback, NSD mDNS) ]
        |
        v
[ Process Supervisor (ProcessBuilder) ]
        |
        v
[ PRoot Engine (filesDir/bin/proot) ]
        |
        +--> Rootfs (Alpine ARM64: Node.js v20 + global n8n)
        +--> Persistent Volume (/data/data/com.app.n8n/files/data/.n8n -> /root/.n8n)
```

---

## Project Structure

```
.
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── README.md
│       ├── java/com/app/n8n/
│       │   ├── N8nApp.kt
│       │   ├── MainActivity.kt
│       │   ├── model/
│       │   │   ├── ServerState.kt
│       │   │   ├── LogEntry.kt
│       │   │   └── SystemStats.kt
│       │   ├── extractor/
│       │   │   └── FileExtractor.kt
│       │   ├── process/
│       │   │   └── N8nProcessSupervisor.kt
│       │   ├── network/
│       │   │   └── NetworkHelper.kt
│       │   ├── service/
│       │   │   └── ServerForegroundService.kt
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   │   ├── Color.kt
│       │   │   │   ├── Type.kt
│       │   │   │   └── Theme.kt
│       │   │   ├── components/
│       │   │   │   ├── QrCodeView.kt
│       │   │   │   ├── StatusBadge.kt
│       │   │   │   ├── SystemStatsCard.kt
│       │   │   │   ├── UrlCard.kt
│       │   │   │   └── TerminalLogViewer.kt
│       │   │   └── screens/
│       │   │       ├── SetupScreen.kt
│       │   │       ├── DashboardScreen.kt
│       │   │       └── LogsBottomSheet.kt
│       │   └── viewmodel/
│       │       └── MainViewModel.kt
│       └── res/
├── scripts/
│   └── build_rootfs.sh
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    └── libs.versions.toml
```

---

## Building the Rootfs & Binary Assets

To bundle prebuilt assets inside the APK:
1. Run `scripts/build_rootfs.sh` on an ARM64 Linux system or inside Docker `buildx`.
2. Copy `n8n-rootfs-arm64.tar.xz` and `proot` into `app/src/main/assets/`.
