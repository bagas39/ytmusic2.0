# YTMusic 2.0

Aplikasi Android pemutar dan pengunduh YouTube Music yang dibangun di atas WebView modern dengan dukungan pemutaran latar belakang penuh, pemblokir iklan cerdas, pengunduh audio *on-device*, serta pemutar musik offline interaktif bergaya Spotify.

---

## 🚀 Fitur Utama

### 1. Pemutaran Latar Belakang (Background Playback)
- **Anti-Freeze & Anti-Pause**: Menimpa *Page Visibility API* dan `document.hasFocus` secara otomatis agar YouTube Music tidak menjeda audio saat layar dimatikan atau saat berganti aplikasi.
- **MediaStyle Notification **: Kontrol notifikasi lengkap di bilah status sistem Android dengan tombol navigasi interaktif (**⏮ Prev**, **⏯ Play/Pause**, **Next ⏭**).
- **CPU Partial WakeLock**: Memastikan audio tetap mengalir lancar tanpa terhenti oleh mekanisme *doze mode* bawaan sistem Android.

### 2. Ad-Blocker & Ad-Skip
- **Network Level Blocking**: Mencegat request ke host server iklan (`googleads.g.doubleclick.net`, `adservice.google.com`, dll.) secara langsung di tingkat WebViewClient dengan respon *mock* CORS valid (200 OK).
- **Fast-Forward Ad-Skip**: Jika iklan video tetap dimuat oleh player, skrip otomatis mempercepat pemutaran hingga **16x kecepatan**, membisukan (*mute*) suara, dan menekan tombol *Skip Ad* secara instan dalam hitungan milidetik.
- **Auto-Dismiss Dialog**: Menutup otomatis popup interupsi seperti dialog *"Are you still there?"* dan promosi langganan Premium tanpa mengganggu pengguna.

### 3. Pengunduh Audio Lokal
- **Didukung oleh yt-dlp & FFmpeg**: Mengekstrak aliran audio terbaik langsung di perangkat dan mengonversinya ke format MP3 lengkap dengan *metadata* (judul, artis) dan cover album (*thumbnail*).
- **Notifikasi Progres Real-Time**: Menampilkan persentase unduhan, kecepatan, dan perkiraan sisa waktu (ETA) di panel notifikasi.
- **Deteksi Duplikat File**: Memeriksa file sebelum mengunduh; jika lagu sudah pernah tersimpan di folder offline, aplikasi akan memunculkan dialog konfirmasi untuk memutar langsung atau mengunduh ulang.

### 4. Pemutar Lagu Offline
- **Panel Pemutar Interaktif**: Dilengkapi dengan teks judul berjalan (*Marquee*), label waktu berjalan, durasi total, dan **SeekBar** yang dapat digeser (*scrubbing*) untuk melompat ke detik yang diinginkan.
- **Integrasi Notifikasi Terpadu**: Notifikasi bilah status secara otomatis beralih menampilkan judul lagu offline dan nomor urut playlist (contoh: `Offline (1/5)`), serta tombol kontrol langsung mengendalikan pemutar offline.
- **Manajemen Playlist & File**:
  - Berpindah lagu secara otomatis (*auto-next*) saat lagu selesai diputar.
  - Opsi memutar di pemutar musik eksternal lain.
  - Opsi membagikan (*share*) file audio via WhatsApp, Telegram, dll.
  - Hapus lagu langsung dari dalam aplikasi.
  - Dukungan multi-tier untuk membuka folder unduhan (`DocumentsContract` -> `DownloadManager` -> `FileProvider` -> Salin path ke *clipboard*).

---

## 🏗 Arsitektur Proyek (Clean Architecture)

Kode dirancang dengan Separation of Concerns dan State & Event Hoisting:

```
app/src/main/java/com/example/ytmusic2/
├── MainActivity.kt                      # Pure Host Container
├── KeepAliveWebView.kt                  # Custom WebView with Anti-visibility suppression & bypass Transsion floating selection
├── MusicBackgroundService.kt            # Foreground Service pemutar audio & MediaStyle notification
│
├── coordinator/
│   └── AppCoordinator.kt                # Orkestrator (Menghubungkan Web, Player, Downloader, UI, dan Service)
│
├── ui/
│   ├── view/
│   │   └── PlayerViewController.kt      # Pengelola Tampilan UI (View Binding tombol, SeekBar, durasi waktu)
│   └── dialog/
│       └── AppDialogManager.kt          # Pengelola Seluruh Dialog (Duplikat, daftar offline, aksi file)
│
├── webview/
│   ├── YtmWebManager.kt                 # Pengelola Web (Ad-blocking, JS bypass, DOM scraping, web clicker)
│   └── SongMetadata.kt                  # Data model lagu yang sedang aktif
│
├── player/
│   └── OfflinePlayerManager.kt          # Controller MediaPlayer offline (Seekbar update loop, playlist queue)
│
├── downloader/
│   └── SongDownloader.kt                # Pipeline unduhan yt-dlp, duplicate checker, dan notifikasi ETA
│
└── util/
    ├── FileUtils.kt                     # Utilitas file storage, format audio, dan fallback buka folder
    ├── FormatUtils.kt                   # Formatting durasi waktu (mm:ss) dan ukuran file (MB)
    └── PermissionHelper.kt              # Pengelolaan izin runtime Storage & Notifikasi Android 13+
```

---

## 📜 Lisensi & Atribusi Pihak Ketiga (Open Source Notices)

Aplikasi ini dibangun menggunakan berbagai pustaka sumber terbuka (*open source*). Berdasarkan ketentuan lisensi masing-masing pustaka, berikut adalah atribusi dan pemberitahuan hak cipta resmi:

### 1. `youtubedl-android`
- **Pengembang / Repositori**: [junkfood02/youtubedl-android](https://github.com/junkfood02/youtubedl-android) (fork dari [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android))
- **Lisensi**: [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html)
- **Keterangan**: Pustaka pembungkus (*wrapper*) Android yang menyediakan runtime Python mandiri serta biner `yt-dlp` dan `FFmpeg` untuk platform Android.
> Karena proyek ini menggunakan pustaka berlisensi GPL-3.0, maka kode sumber turunan yang mendistribusikan pustaka ini juga tunduk pada ketentuan copyleft lisensi GPL-3.0.

### 2. `yt-dlp`
- **Pengembang / Repositori**: [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp)
- **Lisensi**: [The Unlicense (Public Domain)](https://unlicense.org/)
- **Keterangan**: Mesin ekstraksi audio/video baris perintah yang digunakan oleh `youtubedl-android` untuk mengambil informasi media dan aliran audio.

### 3. `FFmpeg`
- **Situs Resmi**: [https://ffmpeg.org](https://ffmpeg.org)
- **Lisensi**: [GNU Lesser General Public License v2.1+ (LGPL-2.1+)](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html) / [GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.html)
- **Pemberitahuan Wajib (*Notice*)**:
> *"This software uses libraries from the FFmpeg project under the LGPLv2.1 / GPLv3.0"*  
> FFmpeg adalah merek dagang terdaftar milik Fabrice Bellard, pencipta proyek FFmpeg. Biner FFmpeg yang dibundel di dalam aplikasi ini digunakan untuk melakukan pasca-pemrosesan audio (ekstraksi, penulisan tag metadata ID3, dan penanaman thumbnail cover album ke file audio MP3). Kode sumber FFmpeg dapat diakses dan diunduh di [ffmpeg.org](https://ffmpeg.org).

### 4. `AndroidX` & `Google Material Components`
- **Pustaka**:
  - `androidx.core:core-ktx`
  - `androidx.appcompat:appcompat`
  - `androidx.constraintlayout:constraintlayout`
  - `androidx.media:media`
  - `com.google.android.material:material`
- **Lisensi**: [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## ⚠️ Disclaimer

- Aplikasi ini dibuat semata-mata untuk **tujuan edukasi, pembelajaran arsitektur Android, dan penggunaan pribadi (*personal fair use*)**.
- **YouTube** dan **YouTube Music** adalah merek dagang terdaftar milik **Google LLC**. Aplikasi ini adalah proyek independen dan tidak berafiliasi, didukung, disponsori, atau disetujui secara khusus oleh Google LLC maupun YouTube.
- Seluruh hak cipta atas konten musik, lagu, lirik, dan materi audio tetap sepenuhnya menjadi milik pencipta konten, artis, dan label rekaman masing-masing. Pengguna bertanggung jawab penuh atas penggunaan materi yang diunduh sesuai dengan ketentuan hukum yang berlaku di wilayah masing-masing.

---

## 🛠 Prasyarat & Panduan Kompilasi

1. **Android Studio** (Hedgehog / Iguana / Jellyfish / Ladybug atau versi lebih baru).
2. **JDK 17 atau JDK 22**.
3. **Android SDK Platform 34** (targetSdk 34, minSdk 24).
4. Perintah kompilasi melalui terminal:
   ```powershell
   # Set JAVA_HOME jika diperlukan
   $env:JAVA_HOME="C:\Program Files\Java\jdk-22"

   # Kompilasi & Jalankan Unit Test
   .\gradlew.bat testDebugUnitTest

   # Bangun file APK Debug
   .\gradlew.bat assembleDebug
   ```
   File APK hasil kompilasi akan berada di `app/build/outputs/apk/debug/app-debug.apk`.