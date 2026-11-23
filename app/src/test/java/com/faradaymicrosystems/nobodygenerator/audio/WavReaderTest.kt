package com.faradaymicrosystems.nobodygenerator.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavReaderTest {
    private fun wav(samples: ShortArray, rate: Int, dataSize: Int? = null, extraChunk: Boolean = false): ByteArray {
        val extra = if (extraChunk) 8 + 4 else 0
        val b = ByteBuffer.allocate(44 + extra + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(0).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        if (extraChunk) b.put("LIST".toByteArray()).putInt(4).putInt(0)
        b.put("data".toByteArray()).putInt(dataSize ?: (samples.size * 2))
        samples.forEach { b.putShort(it) }
        return b.array()
    }

    @Test fun readsNormalWav() {
        val s = shortArrayOf(1, -2, 300, -400, 32767)
        val pcm = WavReader.parse(wav(s, 24000))
        assertEquals(24000, pcm.sampleRate)
        assertArrayEquals(s, pcm.samples)
    }

    @Test fun toleratesZeroDataSizeAndExtraChunks() {
        val s = ShortArray(100) { (it * 10).toShort() }
        val pcm = WavReader.parse(wav(s, 22050, dataSize = 0, extraChunk = true))
        assertEquals(22050, pcm.sampleRate)
        assertArrayEquals(s, pcm.samples)
    }

    @Test fun resampleChangesLength() {
        val out = WavReader.resample(ShortArray(22050), 22050, 24000)
        assertEquals(24000, out.size)
    }
}
