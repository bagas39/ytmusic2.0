package com.example.ytmusic2.util

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {

    const val DOWNLOAD_DIR = "YTMusic2/Downloads"

    /**
     * Mengambil direktori unduhan aplikasi di folder Download publik perangkat.
     */
    fun getDownloadDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DOWNLOAD_DIR
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Memeriksa apakah ekstensi file merupakan format audio yang didukung.
     */
    fun isAudio(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
            lower.endsWith(".webm") || lower.endsWith(".opus") ||
            lower.endsWith(".aac") || lower.endsWith(".ogg") ||
            lower.endsWith(".flac") || lower.endsWith(".wav")
    }

    /**
     * Mengambil seluruh file audio yang ada di direktori unduhan (diurutkan dari yang terbaru).
     */
    fun getOfflineAudioFiles(): List<File> {
        val dir = getDownloadDir()
        return dir.listFiles { f -> f.isFile && isAudio(f.name) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Membuka file audio menggunakan pemutar musik eksternal yang terpasang di HP.
     */
    fun openFileWithExternalApp(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Buka dengan..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Membagikan file audio ke aplikasi lain (WhatsApp, Telegram, Bluetooth, dll).
     */
    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan lagu"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Membuka folder unduhan dengan urutan fallback dari DocumentsContract, DownloadManager, FileProvider,
     * hingga penyalinan path ke clipboard jika sistem operasi membatasi direktori view.
     */
    fun openDownloadFolder(activity: Activity) {
        val dir = getDownloadDir()
        val path = dir.absolutePath

        // 1. Coba buka folder spesifik via DocumentsContract
        try {
            val uri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download/$DOWNLOAD_DIR"
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            return
        } catch (_: Exception) {}

        // 2. Coba buka folder Download via DocumentsContract
        try {
            val uri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download"
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            return
        } catch (_: Exception) {}

        // 3. Coba buka lewat DownloadManager bawaan Android
        try {
            val dmIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(dmIntent)
            return
        } catch (_: Exception) {}

        // 4. Coba buka lewat FileProvider modern
        try {
            val contentUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", dir)
            val fpIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(Intent.createChooser(fpIntent, "Buka folder unduhan"))
            return
        } catch (_: Exception) {}

        // 5. Fallback: salin path ke clipboard dan beri petunjuk
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Download Path", path)
        clipboard.setPrimaryClip(clip)

        AlertDialog.Builder(activity)
            .setTitle("Lokasi Folder Unduhan")
            .setMessage(
                "Path folder sudah disalin ke clipboard!\n\n" +
                "$path\n\n" +
                "Anda dapat membukanya melalui aplikasi Pengelola File / File Manager bawaan HP di:\n" +
                "Penyimpanan Internal > Download > YTMusic2 > Downloads"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
