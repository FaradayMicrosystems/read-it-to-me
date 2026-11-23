package com.faradaymicrosystems.nobodygenerator

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.faradaymicrosystems.nobodygenerator.audio.Mp3Encoder
import com.faradaymicrosystems.nobodygenerator.audio.WavReader
import com.faradaymicrosystems.nobodygenerator.text.DocumentLoader
import com.faradaymicrosystems.nobodygenerator.text.Source
import com.faradaymicrosystems.nobodygenerator.tts.KokoroModel
import com.faradaymicrosystems.nobodygenerator.tts.KokoroVoices
import com.faradaymicrosystems.nobodygenerator.tts.SpeechEngine
import com.faradaymicrosystems.nobodygenerator.tts.Synthesizer
import com.faradaymicrosystems.nobodygenerator.tts.VoiceCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sin

/** Runs the real on-phone pieces: native MP3 encoder, PDF reader and the speech engine. */
@RunWith(AndroidJUnit4::class)
class OnDevicePipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nativeMp3EncoderProducesMp3() {
        val pcm = ShortArray(24000) { (8000 * sin(it * 2 * Math.PI * 440 / 24000)).toInt().toShort() }
        val bytes = Mp3Encoder(24000, 64, "Test").use { it.encode(pcm) + it.flush() }
        assertTrue("mp3 too small: ${bytes.size}", bytes.size > 3000)
        val id3 = bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
        val sync = (bytes[0].toInt() and 0xFF) == 0xFF
        assertTrue("not an mp3 header", id3 || sync)
    }

    @Test
    fun pdfTextIsExtractedAndReflowed() {
        val file = File(context.cacheDir, "test.pdf")
        val pdf = PdfDocument()
        val paint = Paint().apply { textSize = 12f }
        for (n in 1..2) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
            page.canvas.drawText("The quick brown fox jumps over the lazy dog on page $n and", 40f, 100f, paint)
            page.canvas.drawText("keeps running until it reaches the river.", 40f, 116f, paint)
            pdf.finishPage(page)
        }
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()

        val doc = DocumentLoader.load(context, Source.LocalFile(file.absolutePath, "test.pdf"))
        val all = doc.segments.joinToString(" ") { it.text }
        Log.i("NobodyTest", "PDF read as: $all")
        assertTrue(all, all.contains("lazy dog on page 1 and keeps running"))
    }

    @Test
    fun speechEngineWritesAudioThatEncodesToMp3() = runBlocking {
        val synth = try {
            Synthesizer.create(context)
        } catch (e: Exception) {
            assumeTrue("No TTS engine on this device: ${e.message}", false)
            return@runBlocking
        }
        try {
            val options = VoiceCatalog.options(synth.voices())
            Log.i("NobodyTest", "Voices: ${options.joinToString { it.name }}")
            val voice = VoiceCatalog.defaultVoice(options)
            synth.configure(voice?.name, 1.0f, 1.0f)
            val wav = File(context.cacheDir, "t.wav")
            synth.toFile("G'day. This is a test of the Read It To Me speech pipeline.", wav)
            val pcm = WavReader.read(wav)
            Log.i("NobodyTest", "WAV: ${pcm.sampleRate} Hz, ${pcm.samples.size} samples")
            assertTrue("too little audio", pcm.samples.size > pcm.sampleRate)
            val mp3 = Mp3Encoder(pcm.sampleRate, 64, "t").use { it.encode(pcm.samples) + it.flush() }
            assertTrue(mp3.size > 2000)
        } finally {
            synth.shutdown()
        }
    }

    /**
     * Downloads + installs the natural voices for real, then speaks a sentence full of names and
     * numbers. Guards against sherpa-onnx versions that silently drop unknown words: the full
     * sentence is ~9 s of audio; with words dropped it's under 5 s.
     */
    @Test
    fun naturalVoiceInstallsAndSpeaksEveryWord() = runBlocking {
        val t0 = System.currentTimeMillis()
        KokoroModel.install(context)
        Log.i("NobodyTest", "Natural voices installed in ${(System.currentTimeMillis() - t0) / 1000}s")
        assertTrue(KokoroModel.isReady(context))

        val text = "Kuznetsov tested CachyOS and Ryoku in Toowoomba on the 23rd of November 2026, paying \$4.50 at 3:15 pm."
        SpeechEngine.open(context, KokoroVoices.default.optionName, 1.0f, 1.0f).use { engine ->
            val t1 = System.currentTimeMillis()
            val pcm = engine.synthesize(text)!!
            val seconds = pcm.samples.size.toFloat() / pcm.sampleRate
            val took = (System.currentTimeMillis() - t1) / 1000f
            Log.i("NobodyTest", "Kokoro: ${pcm.sampleRate} Hz, %.1fs audio in %.1fs".format(seconds, took))
            assertTrue("words were dropped: only %.1fs of audio".format(seconds), seconds > 7f)
            val mp3 = Mp3Encoder(pcm.sampleRate, 64, "k").use { it.encode(pcm.samples) + it.flush() }
            assertTrue(mp3.size > 20_000)
        }
    }
}
