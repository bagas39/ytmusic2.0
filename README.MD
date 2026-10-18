# YTMusic 2.0

<p align="left">
  <a href="https://github.com/bagas39/ytmusic2.0/releases"><img src="https://img.shields.io/github/v/release/bagas39/ytmusic2.0?color=007ec6&label=Release&logo=github&style=flat-square" alt="Latest Release"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3.0-blue?style=flat-square&logo=gnu&logoColor=white" alt="License"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://github.com/bagas39/ytmusic2.0/releases"><img src="https://img.shields.io/github/downloads/bagas39/ytmusic2.0/total?logo=googleplay&logoColor=white&label=Downloads&color=orange&style=flat-square" alt="Downloads"></a>
</p>

A fast, lightweight, and privacy-respecting YouTube Music client for Android. Built from the ground up to solve the most frustrating limitations of streaming on mobile: sudden playback stops when locking the screen, disruptive ads, and restricted offline access. 

By combining a customized Chromium WebView runtime with native Android background services and an on-device `yt-dlp` extraction engine, **YTMusic 2.0** brings true background playback, seamless ad skipping, and lossless local MP3 downloads directly into your hands—without accounts, paid subscriptions, or root permissions.

---

## 🎯 Overview & Key Highlights

Most mobile browsers and wrapper apps struggle with persistent playback because Android aggressively freezes background WebViews to conserve power. YTMusic 2.0 overcomes this limitation through custom low-level view handling and process immunity techniques, ensuring your music keeps playing uninterrupted whether you are multitasking, gaming, or turning your screen off.

### 📊 Feature Comparison

| Feature / Capability | Official YouTube Music (Free) | Official YouTube Music (Premium) | YTMusic 2.0 |
| :--- | :---: | :---: | :---: |
| **Background Playback (Screen Off / Minimized)** | ❌ Pauses immediately | ✅ Included | ✅ **Unrestricted Background Playback (Lifecycle & KeepAlive Bypass)** |
| **Ad-Free Listening** | ❌ Frequent interruptions | ✅ Ad-free stream | ✅ **Zero-Flicker Interception (Network API & DOM Sanitization)** |
| **Local Audio Downloads** | ❌ Not available | ⚠️ DRM-locked cache only | ✅ **Direct MP3 on-device extraction via yt-dlp & FFmpeg** |
| **Built-in Offline Player** | ❌ | ⚠️ Sandboxed in-app only | ✅ **Interactive Player (Scrubbing SeekBar, Marquee, Notifications)** |
| **System Notification Controls** | ⚠️ Limited | ✅ Media controls | ✅ **Full MediaStyle Notification (⏮ Prev, ⏯ Play/Pause, Next ⏭)** |
| **Login Requirement** | Required for playlists | Required | 🔓 **Optional (Listen anonymously or sign in with your account)** |
| **Cost & Freedom** | Free with heavy restrictions | Paid monthly subscription | **100% Free & Open Source (GPL-3.0)** |

---

## 📸 Interface Preview

<p align="center">
  <img src="Screenshot 2026-10-14 142302.png" width="45%" alt="YTMusic 2.0 Interface & Offline Player">
</p>

---

## 📥 Getting Started

1. Head over to the [Releases](https://github.com/bagas39/ytmusic2.0/releases) tab.
2. Grab the latest `app-debug.apk` (or `app-release.apk`).
3. Tap the downloaded file to install on your Android device (*Enable "Install Unknown Apps" for your browser/file manager if prompted*).
4. Launch the app, pick your favorite track, and enjoy continuous playback even with your screen locked.

---
---

# 📑 TECHNICAL DOCUMENTATION & ARCHITECTURAL BREAKDOWN

## 🔬 Core Mechanics & Low-Level Bypasses

### 1. Android Lifecycle & View Suppression
*   **Chromium View Visibility Override (`KeepAliveWebView`)**: Standard Android WebViews automatically halt JavaScript execution, timers, and WebAudio pipelines when the view tree loses visibility or the display turns off. YTMusic 2.0 implements a specialized `KeepAliveWebView` that overrides `onWindowVisibilityChanged(visibility: Int)`. It permanently dispatches `View.VISIBLE` down to Chromium's internal compositor, ensuring media decoders and DOM timers remain active in all states.
*   **Decoupled Activity Lifecycle**: The host Activity deliberately omits standard `webView.onPause()` and `webView.pauseTimers()` invocations during `onPause()` and `onStop()`. This decouples the browser runtime from the UI window lifecycle, preventing Android from freezing audio playback when navigating away from the app.
*   **Process Immunity via Foreground Service (`MusicBackgroundService`)**: Android's *Low Memory Killer* (LMK) routinely terminates cached background processes. YTMusic 2.0 anchors audio playback to an active `Foreground Service` holding an interactive notification, raising the application's process priority to `FOREGROUND_SERVICE`. An ongoing `PARTIAL_WAKE_LOCK` keeps the CPU running while the device enters deep sleep.
*   **Battery Optimization & Anti-Doze Safeguards**: To counter aggressive OEM background killing policies (such as Transsion HiOS, Xiaomi HyperOS, and Samsung OneUI), the app integrates `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to request exemption from system Doze mode. The playback service specifies `android:stopWithTask="false"` to prevent accidental termination when swiping away the app from the Recent Apps list.

### 2. Network Interception Engine & Anti-Flicker Framework
*   **Zero-Flicker Network API Sanitization**: Rather than waiting for ad elements to load and deleting them from the DOM (which causes visible layout shifts and audio stutters), the `WebViewClient` intercepts network requests targeting `/youtubei/v1/player`. The response payload is sanitized on-the-fly, stripping `adPlacements` and `playerAds` structures before the player receives them. The YouTube player engine therefore treats the incoming media stream as inherently ad-free.
*   **Microtask Observation & Fast-Forward Fallback**: For edge-case interstitial ads, a zero-delay `MutationObserver` monitors `.ad-showing` class mutations. When an ad element appears, playback is instantly muted and fast-forwarded to its end duration (`video.currentTime = video.duration`) within a single `requestAnimationFrame` microtask, immediately triggering the native `skip-button` click.
*   **Active Visibility Verification for Interruption Dialogs**: Polymer Web Components leave closed confirmation dialogs in the DOM tree marked with `display: none;` or `aria-hidden="true"`. YTMusic 2.0 checks strict element visibility predicates (`offsetParent`, bounding geometry, and Polymer's internal `opened` state) before interacting with `<ytmusic-you-there-renderer>` prompts, preventing infinite confirmation click loops.

---

## 🏗 Project Architecture & Separation of Concerns (SoC)

```text
app/src/main/java/com/example/ytmusic2/
├── MainActivity.kt                      # Minimal Host Container (Delegates lifecycle to coordinator)
├── KeepAliveWebView.kt                  # Anti-visibility suppression & Transsion selection fix
├── MusicBackgroundService.kt            # Foreground Service, CPU WakeLock, MediaStyle Notification
│
├── coordinator/
│   └── AppCoordinator.kt                # Central orchestrator connecting UI, Web, Player, and Services
│
├── ui/
│   ├── view/
│   │   └── PlayerViewController.kt      # View binding, SeekBar scrubbing, duration ticker, event hoisting
│   └── dialog/
│       └── AppDialogManager.kt          # Dialog manager (Duplicate prompts, offline tracks, file operations)
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

The codebase strictly adheres to **Separation of Concerns (SoC)** and **State & Event Hoisting**:
* **`MainActivity`** serves exclusively as a clean hosting window, delegating application behavior to `AppCoordinator`.
* **`PlayerViewController`** manages UI rendering and emits high-level user actions through clean lambda callbacks (`onPlayPauseClicked`, `onSeekTo`), keeping UI logic decoupled from media controllers.
* **`YtmWebManager`**, **`OfflinePlayerManager`**, and **`SongDownloader`** encapsulate their respective domains independently, ensuring the codebase remains modular, readable, and maintainable.

---

## 📜 Third-Party Open Source Notices & Copyleft Compliance

This project incorporates open-source components. In accordance with their licensing agreements, the following copyright attributions and compliance statements are provided:

### 1. `youtubedl-android`
*   **Repository**: [junkfood02/youtubedl-android](https://github.com/junkfood02/youtubedl-android) (forked from [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android))
*   **License**: [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html)
*   **Compliance Statement**: This project uses the embedded Python runtime wrapper from `youtubedl-android` to execute extraction tools locally. In compliance with GPL-3.0 copyleft requirements, all downstream source code for this project is open-source and licensed under GPL-3.0.

### 2. `yt-dlp`
*   **Repository**: [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp)
*   **License**: [The Unlicense (Public Domain)](https://unlicense.org)
*   **Notice**: Packaged within the local Python environment for media stream extraction.

### 3. `FFmpeg`
*   **Repository**: [FFmpeg](https://ffmpeg.org)
*   **License**: [GNU Lesser General Public License v2.1+ (LGPL-2.1+)](https://www.gnu.org/licenses/lgpl-2.1.html) / [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html)
*   **Notice**: Compiled as a native shared library for audio stream conversion, container muxing, and thumbnail embedding.

---

## ⚠ Legal Disclaimer & Fair Use Notice

This project is developed solely for **educational, academic research, and personal architectural demonstration purposes**. It is not affiliated with, endorsed by, or associated with YouTube, Google LLC, or any of their parent companies or subsidiaries. All trademarks, service marks, and company names are the property of their respective owners. Any reference to third-party services is made strictly under Nominative Fair Use principles for descriptive and comparative purposes.

---

## 🛠 Compilation & Development Setup

1. **Java Development Kit (JDK)**: JDK 17, 21, or 22 configured in `$JAVA_HOME`.
2. **Android SDK**: Compile SDK `35`, Target SDK `34`, Min SDK `24` (Android 7.0+).
3. **Build Commands**:
```powershell
# Set Java environment (e.g., on Windows PowerShell)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"

# Build debug APK
.\gradlew.bat assembleDebug

# Build release APK
.\gradlew.bat assembleRelease
```
Compiled APK binaries will be generated at: `app/build/outputs/apk/debug/app-debug.apk`.