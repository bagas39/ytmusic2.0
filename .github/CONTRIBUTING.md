# Contributing to YTMusic 2.0

Thank you for your interest in contributing to **YTMusic 2.0**! Contributions, bug reports, and feature improvements are always welcome.

---

## Development Setup

### Prerequisites
- **JDK**: Java 17, 21, or 22
- **Android SDK**: API Level 34 (Android 14)
- **Android Studio**: Ladybug or newer recommended

### Building Locally
1. Clone the repository:
   ```bash
   git clone https://github.com/bagas39/ytmusic2.0.git
   cd ytmusic2.0
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. The compiled APK will be generated in `app/build/outputs/apk/debug/app-debug.apk`.

---

## Codebase Structure

Please keep the **Separation of Concerns (SoC)** architecture intact when adding features or fixing bugs:
- `MainActivity.kt`: Host container only (delegates lifecycle to `AppCoordinator`).
- `coordinator/AppCoordinator.kt`: Orchestrates interactions between UI, WebView, Offline Player, Downloader, and Background Service.
- `webview/YtmWebManager.kt`: Handles WebView setup, ad interception, and DOM scripts.
- `player/OfflinePlayerManager.kt`: Manages local media playback and playlist queue.
- `downloader/SongDownloader.kt`: Handles `yt-dlp` and `FFmpeg` audio extraction.
- `ui/`: Contains `PlayerViewController` and `AppDialogManager` for UI state and dialogs.

---

## Submitting a Pull Request

1. Fork the repository and create a new branch from `main`.
2. Make sure your changes compile cleanly (`./gradlew assembleDebug`).
3. Keep your changes focused on a single feature or bug fix.
4. Open a Pull Request with a clear summary of what was changed and why.
