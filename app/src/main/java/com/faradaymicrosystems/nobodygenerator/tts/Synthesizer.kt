package com.faradaymicrosystems.nobodygenerator.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Coroutine wrapper around Android's built-in TextToSpeech engine. */
class Synthesizer private constructor(private val tts: TextToSpeech) {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.complete(Unit) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.completeExceptionally(SpeechException("Speech engine error")) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let {
                    pending.remove(it)?.completeExceptionally(SpeechException(describe(errorCode)))
                }
            }
        })
    }

    val maxInputLength: Int get() = TextToSpeech.getMaxSpeechInputLength().coerceIn(500, 3500)

    fun voices(): List<Voice> = runCatching { tts.voices?.toList() }.getOrNull() ?: emptyList()

    fun configure(voiceName: String?, rate: Float, pitch: Float) {
        voiceName?.let { name -> voices().firstOrNull { it.name == name }?.let { tts.voice = it } }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
    }

    /** Writes one piece of text to a WAV file. */
    suspend fun toFile(text: String, file: File, timeoutMs: Long = 180_000) {
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        val result = tts.synthesizeToFile(text, Bundle(), file, id)
        if (result != TextToSpeech.SUCCESS) {
            pending.remove(id)
            throw SpeechException("Speech engine refused the text")
        }
        try {
            withTimeout(timeoutMs) { done.await() }
        } finally {
            pending.remove(id)
        }
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        pending.values.forEach { it.cancel() }
        tts.stop()
        tts.shutdown()
    }

    companion object {
        suspend fun create(context: Context): Synthesizer = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var engine: TextToSpeech? = null
                engine = TextToSpeech(context.applicationContext) { status ->
                    val e = engine
                    if (status == TextToSpeech.SUCCESS && e != null) {
                        if (cont.isActive) cont.resume(Synthesizer(e))
                    } else {
                        e?.shutdown()
                        if (cont.isActive) {
                            cont.resumeWithException(
                                SpeechException("No text-to-speech engine. Install \"Speech Recognition & Synthesis from Google\" from the Play Store."),
                            )
                        }
                    }
                }
                cont.invokeOnCancellation { engine?.shutdown() }
            }
        }

        private fun describe(code: Int) = when (code) {
            TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT ->
                "This voice needs internet. Pick an offline voice or connect to the internet"
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> "This voice hasn't finished downloading yet"
            TextToSpeech.ERROR_OUTPUT -> "Couldn't write audio"
            TextToSpeech.ERROR_SYNTHESIS -> "Speech engine couldn't read part of the text"
            else -> "Speech engine error ($code)"
        }
    }
}

class SpeechException(message: String) : Exception(message)
