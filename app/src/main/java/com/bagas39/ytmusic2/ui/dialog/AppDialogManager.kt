package com.bagas39.ytmusic2.ui.dialog

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bagas39.ytmusic2.util.FormatUtils
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
            .setNeutralButton("Putar Offline") { _, _ -> onPlayOffline() }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Dialog daftar lagu offline atau dialog kosong jika belum ada file.
     */
    fun showOfflineFilesDialog(
        files: List<File>,
        storagePath: String,
        hasLyricsPredicate: (File) -> Boolean = { false },
        onSelectFileIndex: (Int) -> Unit,
        onBatchDeleteClicked: () -> Unit = {},
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
            val lyricBadge = if (hasLyricsPredicate(file)) " [Lirik]" else ""
            "${index + 1}. ${file.nameWithoutExtension}$lyricBadge (${FormatUtils.formatFileSizeMb(file.length())})"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Lagu Offline (${files.size})")
            .setItems(names) { _, which ->
                onSelectFileIndex(which)
            }
            .setPositiveButton("Hapus Banyak") { _, _ -> onBatchDeleteClicked() }
            .setNeutralButton("Buka Folder") { _, _ -> onOpenFolder() }
            .setNegativeButton("Tutup", null)
            .show()
    }

    /**
     * Dialog pilihan ganda untuk menghapus banyak lagu offline beserta file `.lrc` dan sampulnya sekaligus.
     */
    fun showBatchDeleteDialog(
        files: List<File>,
        hasLyricsPredicate: (File) -> Boolean = { false },
        onConfirmBatchDelete: (List<File>) -> Unit
    ) {
        if (files.isEmpty()) return

        val checked = BooleanArray(files.size) { false }
        val labels = files.mapIndexed { index, file ->
            val lyricBadge = if (hasLyricsPredicate(file)) " [Lirik]" else ""
            "${index + 1}. ${file.nameWithoutExtension}$lyricBadge (${FormatUtils.formatFileSizeMb(file.length())})"
        }.toTypedArray()

        val dialog = AlertDialog.Builder(context)
            .setTitle("Pilih Lagu untuk Dihapus")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Hapus Terpilih", null)
            .setNeutralButton("Pilih Semua", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val neutralBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val listView = dialog.listView

            neutralBtn.setOnClickListener {
                val allSelected = checked.all { it }
                val targetState = !allSelected
                for (i in checked.indices) {
                    checked[i] = targetState
                    listView?.setItemChecked(i, targetState)
                }
                neutralBtn.text = if (targetState) "Batal Pilih Semua" else "Pilih Semua"
            }

            positiveBtn.setOnClickListener {
                val selectedFiles = files.filterIndexed { index, _ -> checked[index] }
                if (selectedFiles.isEmpty()) {
                    Toast.makeText(context, "Pilih minimal 1 lagu untuk dihapus", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                AlertDialog.Builder(context)
                    .setTitle("Hapus ${selectedFiles.size} Lagu?")
                    .setMessage(
                        "Apakah Anda yakin ingin menghapus ${selectedFiles.size} lagu terpilih " +
                        "beserta file lirik (.lrc) dan sampul album pendampingnya?"
                    )
                    .setPositiveButton("Ya, Hapus") { _, _ ->
                        onConfirmBatchDelete(selectedFiles)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }

        dialog.show()
    }

    /**
     * Dialog menu aksi pada file audio tertentu (Putar, Buka aplikasi lain, Share, Kelola Lirik, Hapus).
     */
    fun showFileActionDialog(
        file: File,
        hasLyrics: Boolean = false,
        onPlay: () -> Unit,
        onOpenWith: () -> Unit,
        onShare: () -> Unit,
        onDownloadLyrics: () -> Unit = {},
        onDeleteLyricsOnly: () -> Unit = {},
        onDelete: () -> Unit
    ) {
        val lyricsOption = if (hasLyrics) {
            "Hapus Lirik (.lrc) Saja"
        } else {
            "Cari & Unduh Lirik (.lrc)"
        }
        val options = arrayOf(
            "Putar Lagu",
            "Buka di Pemutar Lain",
            "Bagikan File",
            lyricsOption,
            "Hapus Lagu (Beserta Lirik & Sampul)"
        )
        AlertDialog.Builder(context)
            .setTitle(file.nameWithoutExtension)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onPlay()
                    1 -> onOpenWith()
                    2 -> onShare()
                    3 -> if (hasLyrics) onDeleteLyricsOnly() else onDownloadLyrics()
                    4 -> onDelete()
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
            .setMessage("Apakah Anda yakin ingin menghapus '${file.name}' beserta file lirik (.lrc) dan sampul pendampingnya?")
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
        val options = (minutePresets.map { "$it Menit" } + "Custom (Atur Menit Sendiri)...").toTypedArray()
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
            builder.setNeutralButton("Matikan Timer") { _, _ ->
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
