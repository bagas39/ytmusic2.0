package com.bagas39.ytmusic2.downloader

import java.io.File

data class DownloadTask(
    val videoId: String,
    val title: String,
    val artist: String = "",
    val forceDownload: Boolean = false,
    val onStart: ((displayTitle: String) -> Unit)? = null,
    val onDuplicateFound: ((existingFile: File) -> Unit)? = null,
    val onSuccess: ((displayTitle: String) -> Unit)? = null,
    val onPartialSuccess: ((displayTitle: String, savedFileName: String, warning: String) -> Unit)? = null,
    val onError: ((displayTitle: String, error: String) -> Unit)? = null
)
