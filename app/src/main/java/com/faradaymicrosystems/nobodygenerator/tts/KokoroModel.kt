package com.faradaymicrosystems.nobodygenerator.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads and installs the offline Kokoro voice model (sherpa-onnx's kokoro-multi-lang-v1_0),
 * keeping only the English files. Download resumes if interrupted.
 */
object KokoroModel {
    const val PACKAGE = "kokoro-multi-lang-v1_0"
    const val DOWNLOAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$PACKAGE.tar.bz2"
    const val ARCHIVE_SHA256 = "60703707bc05ccd48d60e1eb723f38b07d6e474795a0b6a588d7220400e0f7a4"
    const val ARCHIVE_BYTES = 349_886_476L

    /** Space needed while installing: the archive plus the unpacked English files, with headroom. */
    const val NEEDED_BYTES = 800L * 1024 * 1024
    val DOWNLOAD_MB: Long get() = ARCHIVE_BYTES / 1_000_000

    /** Files we keep, with their exact sizes, so a damaged install is never used. */
    private val EXPECTED = mapOf(
        "model.onnx" to 325_560_556L,
        "voices.bin" to 28_200_960L,
        "tokens.txt" to 687L,
        "lexicon-gb-en.txt" to 6_366_635L,
        "lexicon-us-en.txt" to 5_956_885L,
    )

    sealed interface State {
        data object NotInstalled : State
        data class Downloading(val done: Long, val total: Long) : State
        data class Installing(val fraction: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.NotInstalled)
    val state: StateFlow<State> = _state

    private fun root(ctx: Context) = File(ctx.filesDir, "voices")
    fun dir(ctx: Context) = File(root(ctx), PACKAGE)
    private fun marker(ctx: Context) = File(dir(ctx), ".installed")
    private fun archive(ctx: Context) = File(root(ctx), "$PACKAGE.tar.bz2.part")

    fun isReady(ctx: Context) = marker(ctx).exists()

    /** Syncs [state] with what's on disk, unless an install is in progress. */
    fun refresh(ctx: Context) {
        val s = _state.value
        if (s is State.Downloading || s is State.Installing) return
        if (isReady(ctx)) _state.value = State.Ready
        else if (s !is State.Failed) _state.value = State.NotInstalled
    }

    /** Immediate UI feedback while the download service spins up. */
    fun markStarting() {
        if (_state.value !is State.Ready) _state.value = State.Downloading(0, ARCHIVE_BYTES)
    }

    fun setFailed(message: String) {
        _state.value = State.Failed(message)
    }

    fun setNotInstalled(ctx: Context) {
        _state.value = if (isReady(ctx)) State.Ready else State.NotInstalled
    }

    fun remove(ctx: Context) {
        root(ctx).deleteRecursively()
        _state.value = State.NotInstalled
    }

    suspend fun install(ctx: Context) = withContext(Dispatchers.IO) {
        if (isReady(ctx)) {
            _state.value = State.Ready
            return@withContext
        }
        val root = root(ctx).apply { mkdirs() }
        val part = archive(ctx)
        val stillNeeded = NEEDED_BYTES - part.length()
        if (root.usableSpace < stillNeeded) {
            throw IOException(
                "Not enough free space: natural voices need about ${NEEDED_BYTES / 1_000_000} MB free while installing.",
            )
        }
        _state.value = State.Downloading(part.length(), ARCHIVE_BYTES)
        download(part)

        _state.value = State.Installing(0f)
        if (sha256(part) != ARCHIVE_SHA256) {
            part.delete()
            throw IOException("The voice download was damaged. Please try again.")
        }

        val tmp = File(root, "$PACKAGE.tmp").apply { deleteRecursively(); mkdirs() }
        extract(part, tmp)
        verify(tmp)
        File(tmp, ".installed").writeText(ARCHIVE_SHA256)
        val dest = dir(ctx)
        dest.deleteRecursively()
        if (!tmp.renameTo(dest)) throw IOException("Couldn't finish installing the voices.")
        part.delete()
        _state.value = State.Ready
    }

    private suspend fun download(part: File) {
        var attempt = 0
        while (part.length() < ARCHIVE_BYTES) {
            coroutineContext.ensureActive()
            try {
                downloadOnce(part)
            } catch (e: IOException) {
                if (++attempt >= 5) throw IOException("Download failed: ${e.message}. Check your connection and try again.", e)
                delay(2000L * attempt)
            }
        }
        if (part.length() > ARCHIVE_BYTES) {
            part.delete()
            throw IOException("The voice download was the wrong size. Please try again.")
        }
    }

    private suspend fun downloadOnce(part: File) {
        val have = part.length()
        val conn = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 30_000
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false // server ignored the range; start over
                416 -> { part.delete(); return } // already complete or bad range; restart cleanly
                else -> throw IOException("server said $code")
            }
            var done = if (append) have else 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var last = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 512 * 1024) {
                            last = done
                            _state.value = State.Downloading(done, ARCHIVE_BYTES)
                        }
                    }
                }
            }
            _state.value = State.Downloading(done, ARCHIVE_BYTES)
        } finally {
            conn.disconnect()
        }
    }

    /** Which archive entries we keep, and where. Null = skip (Chinese-only files, docs). */
    fun keepPath(entryName: String): String? {
        val rel = entryName.removePrefix("./").substringAfter('/', "")
        if (rel.isEmpty() || rel.contains("..")) return null
        if (rel.startsWith("espeak-ng-data/")) return rel
        return if (rel in EXPECTED.keys || rel == "LICENSE") rel else null
    }

    private suspend fun extract(archive: File, dest: File) {
        val total = archive.length().toFloat()
        val counter = CountingStream(archive.inputStream().buffered(1 shl 16))
        TarArchiveInputStream(BZip2CompressorInputStream(counter)).use { tar ->
            val base = dest.canonicalPath + File.separator
            var last = 0L
            while (true) {
                coroutineContext.ensureActive()
                val entry = tar.nextEntry ?: break
                if (counter.count - last > 2_000_000) {
                    last = counter.count
                    _state.value = State.Installing((counter.count / total).coerceIn(0f, 1f))
                }
                val rel = keepPath(entry.name) ?: continue
                val out = File(dest, rel)
                if (!out.canonicalPath.startsWith(base)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else if (entry.isFile) {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered(1 shl 16).use { tar.copyTo(it) }
                }
            }
        }
    }

    private fun verify(dir: File) {
        for ((name, size) in EXPECTED) {
            val f = File(dir, name)
            if (!f.isFile || f.length() != size) throw IOException("Voice install is incomplete ($name). Please try again.")
        }
        if (!File(dir, "espeak-ng-data/en_dict").isFile) throw IOException("Voice install is incomplete (espeak). Please try again.")
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private class CountingStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }
}
