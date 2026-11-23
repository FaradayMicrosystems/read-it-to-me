package com.faradaymicrosystems.nobodygenerator.data

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recording(val file: File, val title: String, val sizeBytes: Long, val durationMs: Long, val createdAt: Long)

/** Finished MP3s, kept in the app's private storage until saved or shared. */
object Library {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun changed() {
        _version.value += 1
    }

    fun dir(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun newFile(context: Context, title: String): File {
        val safe = title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ")
            .replace(Regex("""\s+"""), " ").trim().take(60).ifBlank { "Recording" }
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(Date())
        var file = File(dir(context), "$safe ($stamp).mp3")
        var n = 2
        while (file.exists()) file = File(dir(context), "$safe ($stamp) $n.mp3").also { n++ }
        return file
    }

    fun list(context: Context): List<Recording> = dir(context)
        .listFiles { f -> f.isFile && f.name.endsWith(".mp3") && !f.name.startsWith(".") }
        .orEmpty()
        .sortedByDescending { it.lastModified() }
        .map { f -> Recording(f, f.nameWithoutExtension, f.length(), durationOf(f), f.lastModified()) }

    private fun durationOf(file: File): Long = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(file.absolutePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                release()
            }
        }
    }.getOrDefault(0L)
}
