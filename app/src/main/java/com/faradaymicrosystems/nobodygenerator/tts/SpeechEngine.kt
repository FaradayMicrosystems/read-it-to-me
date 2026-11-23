package com.faradaymicrosystems.nobodygenerator.tts

import android.content.Context
import com.faradaymicrosystems.nobodygenerator.audio.Pcm
import com.faradaymicrosystems.nobodygenerator.audio.WavReader
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Turns a short piece of text into mono PCM. One engine per job; call [close] when done. */
interface SpeechEngine : AutoCloseable {
    /** Longest piece of text to hand over at once. */
    val maxChunk: Int

    /** Null if the engine produced no audio for this text. */
    suspend fun synthesize(text: String): Pcm?

    override fun close()

    companion object {
        /** Opens the engine that owns [voiceName]: Kokoro for "kokoro:…" voices, the phone's engine otherwise. */
        suspend fun open(context: Context, voiceName: String?, rate: Float, pitch: Float): SpeechEngine {
            val kokoro = KokoroVoices.find(voiceName)
            if (KokoroVoices.isKokoro(voiceName)) {
                if (kokoro == null || !KokoroModel.isReady(context)) {
                    throw IOException("The natural voices aren't installed. Download them again, or pick a phone voice.")
                }
                return KokoroEngine.create(KokoroModel.dir(context), kokoro, rate)
            }
            return SystemEngine.create(context, voiceName, rate, pitch)
        }
    }
}

/** Android's built-in text-to-speech (Google voices etc.). */
class SystemEngine private constructor(private val synth: Synthesizer, private val wav: File) : SpeechEngine {
    override val maxChunk: Int get() = synth.maxInputLength

    override suspend fun synthesize(text: String): Pcm? {
        wav.delete()
        synth.toFile(text, wav)
        return if (wav.length() > 44) WavReader.read(wav) else null
    }

    override fun close() {
        synth.shutdown()
        wav.delete()
    }

    companion object {
        suspend fun create(context: Context, voiceName: String?, rate: Float, pitch: Float): SystemEngine {
            val synth = Synthesizer.create(context)
            synth.configure(voiceName, rate, pitch)
            return SystemEngine(synth, File(context.cacheDir, "chunk.wav"))
        }
    }
}

/** Offline neural voices: Kokoro v1.0 running on sherpa-onnx. Pitch isn't adjustable. */
class KokoroEngine private constructor(
    private val tts: OfflineTts,
    private val voice: KokoroVoice,
    private val speed: Float,
) : SpeechEngine {
    // Kokoro slows down and drifts on long input; sentence-sized pieces sound best.
    override val maxChunk: Int = 300

    override suspend fun synthesize(text: String): Pcm? = withContext(Dispatchers.Default) {
        val audio = tts.generate(text, voice.sid, speed)
        val samples = audio.samples
        if (samples.isEmpty() || audio.sampleRate <= 0) {
            null
        } else {
            Pcm(audio.sampleRate, ShortArray(samples.size) { i -> (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort() })
        }
    }

    override fun close() {
        tts.release()
    }

    companion object {
        fun threads(): Int = if (Runtime.getRuntime().availableProcessors() >= 6) 4 else 2

        suspend fun create(dir: File, voice: KokoroVoice, speed: Float): KokoroEngine = withContext(Dispatchers.Default) {
            val kokoro = OfflineTtsKokoroModelConfig(
                model = File(dir, "model.onnx").path,
                voices = File(dir, "voices.bin").path,
                tokens = File(dir, "tokens.txt").path,
                dataDir = File(dir, "espeak-ng-data").path,
                // Words missing from the lexicon fall back to espeak ("en" is British English there).
                lexicon = File(dir, if (voice.british) "lexicon-gb-en.txt" else "lexicon-us-en.txt").path,
                lang = if (voice.british) "en" else "",
            )
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = threads(), debug = false, provider = "cpu"),
                maxNumSentences = 1,
            )
            KokoroEngine(OfflineTts(config = config), voice, speed.coerceIn(0.5f, 2.0f))
        }
    }
}
