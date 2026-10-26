package com.example.ytmusic2.ui.dialog

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.ytmusic2.util.FormatUtils
import java.io.File

/**
 * Mengelola pembuatan dan penayangan seluruh dialog pada aplikasi.
 * Memisahkan tanggung jawab UI dialog dari Activity dan Coordinator.
 */
class AppDialogManager(private val context: Context) {

    /**
     * Dialog konfirmasi saat lagu yang sama sudah pernah diunduh sebelumnya.
     */
    fun showDuplicateConfirmDialog(
        existingFile: File,
        onRedownload: () -> Unit,
        onPlayOffline: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Lagu Sudah Diunduh")
            .setMessage("File '${existingFile.name}' sudah ada di folder unduhan offline.\n\nApakah Anda ingin mengunduhnya ulang atau memutarnya sekarang?")
            .setPositiveButton("Unduh Ulang") { _, _ -> onRedownload() }
            .setNeutralButton("▶ Putar Offline") { _, _ -> onPlayOffline() }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Dialog daftar lagu offline atau dialog kosong jika belum ada file.
     */
    fun showOfflineFilesDialog(
        files: List<File>,
        storagePath: String,
        onSelectFileIndex: (Int) -> Unit,
        onOpenFolder: () -> Unit
    ) {
        if (files.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Lagu Offline (0)")
                .setMessage("Belum ada file lagu yang diunduh.\n\nLokasi penyimpanan:\n$storagePath")
                .setPositiveButton("Buka Folder") { _, _ -> onOpenFolder() }
                .setNegativeButton("Tutup", null)
                .show()
            return
        }

        val names = files.mapIndexed { index, file ->
            "${index + 1}. ${file.name} (${FormatUtils.formatFileSizeMb(file.length())})"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Lagu Offline (${files.size})")
            .setItems(names) { _, which ->
                onSelectFileIndex(which)
            }
            .setNeutralButton("📂 Buka Folder") { _, _ -> onOpenFolder() }
            .setNegativeButton("Tutup", null)
            .show()
    }

    /**
     * Dialog menu aksi pada file audio tertentu (Putar, Buka aplikasi lain, Share, Hapus).
     */
    fun showFileActionDialog(
        file: File,
        onPlay: () -> Unit,
        onOpenWith: () -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) {
        val options = arrayOf(
            "▶ Putar Lagu",
            "↗ Buka di Pemutar Lain",
            "📤 Bagikan File",
            "🗑 Hapus Lagu"
        )
        AlertDialog.Builder(context)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onPlay()
                    1 -> onOpenWith()
                    2 -> onShare()
                    3 -> onDelete()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Dialog konfirmasi sebelum menghapus file lagu offline secara permanen.
     */
    fun showDeleteConfirmDialog(
        file: File,
        onConfirmDelete: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Hapus Lagu?")
            .setMessage("Apakah Anda yakin ingin menghapus '${file.name}'?")
            .setPositiveButton("Hapus") { _, _ -> onConfirmDelete() }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Dialog pilihan waktu Sleep Timer (Pengatur Waktu Tidur).
     */
    fun showSleepTimerDialog(
        isActive: Boolean,
        remainingText: String?,
        onSelectMinutes: (Int) -> Unit,
        onCancelTimer: () -> Unit
    ) {
        val minutePresets = intArrayOf(15, 30, 45, 60)
        val options = (minutePresets.map { "⏲ $it Menit" } + "✏️ Custom (Atur Menit Sendiri)...").toTypedArray()
        val title = if (isActive && !remainingText.isNullOrBlank()) {
            "Sleep Timer (Aktif: $remainingText)"
        } else {
            "Sleep Timer"
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(options) { _, which ->
                if (which < minutePresets.size) {
                    onSelectMinutes(minutePresets[which])
                } else {
                    showCustomSleepTimerInputDialog(onSelectMinutes)
                }
            }
            .setNegativeButton("Tutup", null)

        if (isActive) {
            builder.setNeutralButton("❌ Matikan Timer") { _, _ ->
                onCancelTimer()
            }
        }

        builder.show()
    }

    /**
     * Dialog input angka menit custom untuk Sleep Timer.
     */
    private fun showCustomSleepTimerInputDialog(onSelectMinutes: (Int) -> Unit) {
        val density = context.resources.displayMetrics.density
        val paddingHorizontal = (20 * density).toInt()
        val paddingVertical = (8 * density).toInt()

        val input = EditText(context).apply {
            hint = "Contoh: 20 (dalam menit)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }

        val container = FrameLayout(context).apply {
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, 0)
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle("Custom Sleep Timer")
            .setMessage("Masukkan durasi waktu tidur (dalam menit):")
            .setView(container)
            .setPositiveButton("Mulai") { _, _ ->
                val minutes = input.text.toString().trim().toIntOrNull()
                if (minutes != null && minutes in 1..1440) {
                    onSelectMinutes(minutes)
                } else {
                    Toast.makeText(context, "Masukkan angka menit yang valid (1 - 1440)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
