package com.faradaymicrosystems.nobodygenerator.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.faradaymicrosystems.nobodygenerator.audio.Mp3Encoder
import com.faradaymicrosystems.nobodygenerator.audio.WavReader
import com.faradaymicrosystems.nobodygenerator.data.Library
import com.faradaymicrosystems.nobodygenerator.text.DocumentLoader
import com.faradaymicrosystems.nobodygenerator.text.Source
import com.faradaymicrosystems.nobodygenerator.text.SpeechText
import com.faradaymicrosystems.nobodygenerator.tts.Synthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext

/** Foreground service: document -> speech -> MP3, survives the screen turning off. */
class GenerateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            if (job == null) stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true || intent == null) return START_NOT_STICKY

        val request = Request.from(intent) ?: run { stopSelf(); return START_NOT_STICKY }
        Notifications.createChannels(this)
        val first = Notifications.progress(this, "Getting ready", "Reading your document…", 0, 0)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifications.PROGRESS_ID, first, type)

        job = scope.launch {
            try {
                generate(request)
            } catch (e: CancellationException) {
                Jobs.set(JobState.Idle)
            } catch (e: Throwable) {
                Log.e(TAG, "Generation failed", e)
                val message = friendly(e)
                Jobs.set(JobState.Failed(message))
                Notifications.failed(this@GenerateService, message)
            } finally {
                ServiceCompat.stopForeground(this@GenerateService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun generate(req: Request) {
        progress("Getting ready", "Reading your document…", 0, 0)
        val doc = withContext(Dispatchers.IO) { DocumentLoader.load(this@GenerateService, req.source) }
        if (doc.segments.isEmpty()) throw IllegalStateException("Couldn't find any readable text in that.")
        val title = doc.title

        val synth = Synthesizer.create(this)
        var encoder: Mp3Encoder? = null
        val partial = File(Library.dir(this), ".partial-${System.currentTimeMillis()}.mp3")
        val wav = File(cacheDir, "chunk.wav")
        var finished = false
        try {
            synth.configure(req.voiceName, req.rate, req.pitch)
            val max = synth.maxInputLength
            val pieces = doc.segments.flatMap { seg ->
                val parts = SpeechText.chunk(seg.text, max)
                parts.mapIndexed { i, p -> p to if (i == parts.lastIndex) seg.pauseAfterMs else 120 }
            }
            var encoderRate = 0
            var skipped = 0
            var lastUpdate = 0L
            partial.outputStream().buffered().use { out ->
                for ((index, piece) in pieces.withIndex()) {
                    coroutineContext.ensureActive()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdate > 700 || index == 0) {
                        lastUpdate = now
                        progress(title, "Speaking part ${index + 1} of ${pieces.size}", index, pieces.size)
                    }
                    val (text, pauseMs) = piece
                    if (!speakToWav(synth, text, wav)) {
                        skipped++
                        continue
                    }
                    val pcm = WavReader.read(wav)
                    val enc = encoder ?: Mp3Encoder(pcm.sampleRate, KBPS, title).also {
                        encoder = it
                        encoderRate = pcm.sampleRate
                    }
                    val samples = WavReader.resample(pcm.samples, pcm.sampleRate, encoderRate)
                    out.write(enc.encode(samples))
                    out.write(enc.encode(ShortArray(encoderRate * pauseMs / 1000)))
                }
                val enc = encoder ?: throw IllegalStateException(
                    "The speech engine didn't produce any audio. Try a different voice.",
                )
                if (skipped * 2 > pieces.size) {
                    throw IllegalStateException("The speech engine failed on most of the text. Try a different voice.")
                }
                out.write(enc.flush())
            }
            val final = Library.newFile(this, title)
            if (!partial.renameTo(final)) partial.copyTo(final, overwrite = true)
            finished = true
            Library.changed()
            Jobs.set(JobState.Finished(title, final, skipped))
            Notifications.finished(this, title)
        } finally {
            encoder?.close()
            synth.shutdown()
            wav.delete()
            partial.delete() // already renamed away when finished; otherwise discard the half-made file
            if (!finished) Log.i(TAG, "Discarded unfinished recording")
        }
    }

    /** One retry, because engines occasionally drop a request. */
    private suspend fun speakToWav(synth: Synthesizer, text: String, wav: File): Boolean {
        repeat(2) { attempt ->
            try {
                wav.delete()
                synth.toFile(text, wav)
                if (wav.length() > 44) return true
            } catch (e: CancellationException) {
                coroutineContext.ensureActive() // real cancel -> rethrows; timeout -> retry
            } catch (e: Exception) {
                Log.w(TAG, "Speech attempt ${attempt + 1} failed", e)
                if (attempt == 1 && e.message?.contains("internet") == true) throw e
            }
        }
        return false
    }

    private fun progress(title: String, stage: String, done: Int, total: Int) {
        Jobs.set(JobState.Running(title, stage, done, total))
        Notifications.update(this, Notifications.progress(this, title, stage, done, total))
    }

    private fun friendly(e: Throwable): String = when (e) {
        is UnknownHostException -> "No internet connection, so the article couldn't be downloaded."
        is org.jsoup.HttpStatusException -> "The website refused the request (error ${e.statusCode})."
        is IOException -> e.message?.let { "Couldn't read that: $it" } ?: "Couldn't read that document."
        else -> e.message ?: "Something went wrong."
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    data class Request(val source: Source, val voiceName: String?, val rate: Float, val pitch: Float) {
        companion object {
            fun from(intent: Intent): Request? {
                val source = when (intent.getStringExtra(EXTRA_KIND)) {
                    "file" -> Source.LocalFile(
                        intent.getStringExtra(EXTRA_PATH) ?: return null,
                        intent.getStringExtra(EXTRA_NAME) ?: "Document",
                    )
                    "url" -> Source.WebPage(intent.getStringExtra(EXTRA_TEXT) ?: return null)
                    "text" -> Source.PastedText(intent.getStringExtra(EXTRA_TEXT) ?: return null)
                    else -> return null
                }
                return Request(
                    source,
                    intent.getStringExtra(EXTRA_VOICE),
                    intent.getFloatExtra(EXTRA_RATE, 1f),
                    intent.getFloatExtra(EXTRA_PITCH, 1f),
                )
            }
        }
    }

    companion object {
        private const val TAG = "NobodyGenerator"
        private const val KBPS = 64
        const val ACTION_CANCEL = "com.faradaymicrosystems.nobodygenerator.CANCEL"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_VOICE = "voice"
        private const val EXTRA_RATE = "rate"
        private const val EXTRA_PITCH = "pitch"

        fun start(context: Context, source: Source, voiceName: String?, rate: Float, pitch: Float) {
            val intent = Intent(context, GenerateService::class.java)
                .putExtra(EXTRA_VOICE, voiceName)
                .putExtra(EXTRA_RATE, rate)
                .putExtra(EXTRA_PITCH, pitch)
            when (source) {
                is Source.LocalFile -> intent.putExtra(EXTRA_KIND, "file")
                    .putExtra(EXTRA_PATH, source.path).putExtra(EXTRA_NAME, source.displayName)
                is Source.WebPage -> intent.putExtra(EXTRA_KIND, "url").putExtra(EXTRA_TEXT, source.url)
                is Source.PastedText -> intent.putExtra(EXTRA_KIND, "text").putExtra(EXTRA_TEXT, source.text)
            }
            Jobs.set(JobState.Running("Getting ready", "Reading your document…", 0, 0))
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, GenerateService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
