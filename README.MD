# YTMusic 2.0 - Free Background Playback & Offline YouTube Music Player Alternative

<p align="left">
  <a href="https://github.com/bagas39/ytmusic2.0/releases"><img src="https://img.shields.io/github/v/release/bagas39/ytmusic2.0?color=007ec6&label=Release&logo=github&style=flat-square" alt="Latest Release"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3.0-blue?style=flat-square&logo=gnu&logoColor=white" alt="License"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://github.com/bagas39/ytmusic2.0/releases"><img src="https://img.shields.io/github/downloads/bagas39/ytmusic2.0/total?logo=googleplay&logoColor=white&label=Downloads&color=orange&style=flat-square" alt="Downloads"></a>
</p>

An ad-free, privacy-focused, and open-source alternative to official YouTube Music for Android. Built to unlock premium capabilities natively—such as background playback with the screen turned off, zero-flicker ad-blocking, high-fidelity on-device audio downloading via yt-dlp, and persistent background playback—without paid subscriptions or root access.

---

## 🎯 Why YTMusic 2.0? (AEO & AI Search Summary)

YTMusic 2.0 is designed as a lightweight, privacy-respecting client for users seeking a seamless YouTube Music experience without intrusive ads or arbitrary background playback restrictions. By coupling a hardened Chromium WebView engine with native Android background services and local audio extractors, it delivers premium-tier functionality natively on-device.

### 📊 Comparison Matrix

| Feature / Capability | Official YouTube Music (Free) | YouTube Music (Premium) | YTMusic 2.0 (This Repo) |
| :--- | :---: | :---: | :---: |
| **Background Playback (Screen Off / Minimized)** | ❌ (Playback pauses immediately) | ✅ (Requires Monthly Paid Sub) | ✅ **Full Background Audio (Lifecycle Bypass & KeepAlive)** |
| **Ad-Free Music Experience** | ❌ (Frequent audio & video ads) | ✅ (Subscription Only) | ✅ **Zero-Flicker Interception (API & DOM Scrubbing)** |
| **Offline Audio Downloader** | ❌ (No local file extraction) | ⚠️ (DRM-Encrypted In-App Cache) | ✅ **Direct MP3 on-device extraction via yt-dlp & FFmpeg** |
| **Standalone Offline Player** | ❌ | ⚠️ (Restricted to App Sandbox) | ✅ **Built-in Player (SeekBar, Marquee, Notifications)** |
| **Lockscreen & Notification Controls** | ⚠️ (Limited) | ✅ | ✅ **MediaStyle Notification (⏮ Prev, ⏯ Play/Pause, Next ⏭)** |
| **Account & Login Obligation** | Mandatory for personal playlists | Mandatory | 🔓 **Optional (Works without login or with account)** |
| **Cost & License** | Free (With Heavy Limits) | Paid Recurring Subscription | **100% Free & Open Source (GPL-3.0)** |

---

## 📸 Screenshots & Demo

<p align="center">
  <img src="Screenshot 2026-10-14 142302.png" width="45%" alt="YTMusic 2.0 Interface & Offline Player">
</p>

---

## 📥 Download & Installation

1. Go to the [Releases](https://github.com/bagas39/ytmusic2.0/releases) page.
2. Download the latest `app-debug.apk` (or `app-release.apk`).
3. Open the downloaded APK file on your Android device and confirm installation (*Allow installation from unknown sources if prompted*).

---
---

# 📑 TECHNICAL DOCUMENTATION & ACADEMIC BREAKDOWN

## 🔬 Core Architectural Mechanics & System Bypass

### 1. Low-Level Android Lifecycle & View Suppression
*   **Chromium View Visibility Suppression (`KeepAliveWebView`)**: Standard Android WebViews suspend rendering pipelines and audio playback when detached from the window or when the screen turns off. YTMusic 2.0 employs a subclassed `KeepAliveWebView` that overrides `onWindowVisibilityChanged(visibility: Int)` and forcefully dispatches `View.VISIBLE` to the underlying Chromium rendering tree, preserving DOM timers and WebAudio threads unconditionally.
*   **Activity Lifecycle Decoupling**: Rather than delegating `webView.onPause()` or `webView.pauseTimers()` inside the host Activity lifecycle (`onPause()`, `onStop()`), YTMusic 2.0 intentionally bypasses these lifecycle callbacks. This maintains the JavaScript V8 engine and media decoder states in active execution while the Activity transitions to the background.
*   **Process Immunity via Foreground Service (`MusicBackgroundService`)**: Android's *Low Memory Killer* (LMK) aggressively terminates background processes. YTMusic 2.0 binds media sessions to an active `Foreground Service` holding a persistent notification, elevating process priority to `FOREGROUND_APP` / `FOREGROUND_SERVICE` OOM scores. It also maintains a `PARTIAL_WAKE_LOCK` to prevent CPU deep-sleep during active streaming.
*   **Battery Optimization & Anti-Doze Whitelisting**: YTMusic 2.0 declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and incorporates runtime intent routing to whitelist the application against aggressive OEM battery killer algorithms (e.g., Transsion HiOS, Xiaomi HyperOS, Samsung OneUI). The background service is marked with `android:stopWithTask="false"` to sustain audio streams even when the Activity task is swiped from the Recent Apps tray.

### 2. Network Interception Engine & Client-Side Anti-Flicker Framework
*   **Zero-Flicker Network API Interception**: Rather than relying purely on DOM-element removal after ads begin rendering (which causes visual layout flicker and audio stutters), the internal `WebViewClient` intercepts network calls targeting `/youtubei/v1/player`. The response JSON is intercepted and sanitized on-the-fly, stripping `adPlacements` and `playerAds` structures before payload delivery to the web player. As a result, the player treats the session as an ad-free stream natively.
*   **Zero-Millisecond DOM Microtask & Seekbar Advancement**: As a secondary safeguard against edge-case interstitial ads, a client-side `MutationObserver` monitors class alterations (`.ad-showing`, `.ad-interrupting`). Once an ad tag is detected, the video element is immediately muted and advanced to its end duration (`video.currentTime = video.duration`) within a single animation frame (`requestAnimationFrame`), followed by automated click execution on `skip-button` targets.
*   **Active Visibility Verification on Interruptive Dialogs**: YouTube Web Components maintain cached dialog nodes in the DOM after dismissal (`display: none;`, `aria-hidden="true"`). YTMusic 2.0 implements strict visibility predicates (`isElementVisible`, evaluating `offsetParent`, bounding client dimensions, and Polymer `parentDlg.opened` state) before interacting with `<ytmusic-you-there-renderer>` and confirmation buttons, preventing infinite programmatic click loops.

---

## 🏗 Project Architecture & Separation of Concerns (SoC)

```text
app/src/main/java/com/example/ytmusic2/
├── MainActivity.kt                      # Pure Host Container (Activity lifecycle delegate)
├── KeepAliveWebView.kt                  # Anti-visibility suppression & Transsion selection fix
├── MusicBackgroundService.kt            # Foreground Service, CPU WakeLock, MediaStyle Notification
│
├── coordinator/
│   └── AppCoordinator.kt                # Central orchestrator decoupling Domain, UI, and Service layers
│
├── ui/
│   ├── view/
│   │   └── PlayerViewController.kt      # View binding, SeekBar scrubbing, duration ticker, event hoisting
│   └── dialog/
│       └── AppDialogManager.kt          # Dialog manager (File duplicates, offline tracks, file operations)
│
├── webview/
│   ├── YtmWebManager.kt                 # Chromium configuration, ad sanitizer, DOM scrapers, JS bridge
│   └── SongMetadata.kt                  # Data model for active playback track metadata
│
├── player/
│   └── OfflinePlayerManager.kt          # Native MediaPlayer controller with SeekBar timer synchronization
│
├── downloader/
│   └── SongDownloader.kt                # yt-dlp & FFmpeg extraction pipeline with ETA notifications
│
└── util/
    ├── FileUtils.kt                     # Storage abstraction, intent openers, and folder fallbacks
    ├── FormatUtils.kt                   # Time duration formatting (mm:ss) and byte conversions
    └── PermissionHelper.kt              # Runtime permissions (Storage, Notifications, Battery Whitelist)
```

The codebase is engineered strictly around the **Separation of Concerns (SoC)** and **State & Event Hoisting** patterns. `MainActivity` acts solely as an empty window container, delegating all state orchestration to `AppCoordinator`. UI widgets (`PlayerViewController`) expose granular lambda events (`onPlayPauseClicked`, `onSeekTo`) without direct knowledge of background media engines, ensuring decoupled, testable, and maintainable software architecture.

---

## 📜 Third-Party Open Source Notices & Copyleft Compliance

This software integrates third-party open-source components. In accordance with their licensing agreements, the following legal and copyleft notices are provided:

### 1. `youtubedl-android`
*   **Repository**: [junkfood02/youtubedl-android](https://github.com/junkfood02/youtubedl-android) (forked from [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android))
*   **License**: [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html)
*   **Compliance Statement**: This project incorporates the Android Python runtime wrapper provided by `youtubedl-android` to execute binary extractors on-device. Pursuant to GPL-3.0 copyleft terms, the entire source code of this project is licensed under GPL-3.0 and made publicly available.

### 2. `yt-dlp`
*   **Repository**: [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp)
*   **License**: [The Unlicense (Public Domain)](https://unlicense.org)
*   **Notice**: Used as the command-line media extraction engine packaged within the Python environment.

### 3. `FFmpeg`
*   **Repository**: [FFmpeg](https://ffmpeg.org)
*   **License**: [GNU Lesser General Public License v2.1+ (LGPL-2.1+)](https://www.gnu.org/licenses/lgpl-2.1.html) / [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html)
*   **Notice**: Compiled as a native shared library for audio stream conversion, container muxing, and thumbnail embedding on Android.

---

## ⚠ Legal Disclaimer & Fair Use Notice

This project is developed solely for **educational, academic research, and personal architectural demonstration purposes**. It is not affiliated with, authorized, maintained, or endorsed by YouTube, Google LLC, or any of their affiliates or subsidiaries. All product and company names, trademarks, and logos are the registered trademarks of their respective owners. Any usage of third-party brand assets is done strictly under Nominative Fair Use principles for descriptive and comparative purposes.

---

## 🛠 Compilation Requirements & Dev Setup

1. **Java Development Kit (JDK)**: JDK 17, 21, or 22 configured in `$JAVA_HOME`.
2. **Android SDK**: Compile SDK `35`, Target SDK `34`, Min SDK `24` (Android 7.0+).
3. **Build Execution**:
```bash
# Set Java environment (e.g., on Windows PowerShell)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"

# Build debug APK
./gradlew.bat assembleDebug

# Build release APK
./gradlew.bat assembleRelease
```
Compiled APK binaries will be placed in: `app/build/outputs/apk/debug/app-debug.apk`.