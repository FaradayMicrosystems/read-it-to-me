package com.faradaymicrosystems.nobodygenerator.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mono 16-bit PCM. */
class Pcm(val sampleRate: Int, val samples: ShortArray)

/** Reads the WAV files Android's speech engine writes, tolerating headers with missing sizes. */
object WavReader {
    fun read(file: File): Pcm = parse(file.readBytes())

    fun parse(bytes: ByteArray): Pcm {
        require(bytes.size >= 12) { "Audio file is empty" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        val wave = String(bytes, 8, 4, Charsets.US_ASCII)
        require(riff == "RIFF" && wave == "WAVE") { "Not a WAV file" }

        var sampleRate = 0
        var channels = 1
        var bits = 16
        var format = 1
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val declared = buf.getInt(pos + 4).toLong() and 0xFFFFFFFFL
            val start = pos + 8
            if (id == "fmt ") {
                format = buf.getShort(start).toInt() and 0xFFFF
                channels = buf.getShort(start + 2).toInt()
                sampleRate = buf.getInt(start + 4)
                bits = buf.getShort(start + 14).toInt()
            } else if (id == "data") {
                val available = bytes.size - start
                val size = if (declared == 0L || declared > available) available else declared.toInt()
                require(sampleRate > 0) { "WAV has no format chunk" }
                return Pcm(sampleRate, decode(buf, start, size, format, channels, bits))
            }
            pos = start + declared.toInt() + (declared.toInt() and 1)
            if (declared > bytes.size) break
        }
        error("WAV has no audio data")
    }

    private fun decode(buf: ByteBuffer, start: Int, size: Int, format: Int, channels: Int, bits: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        return when {
            bits == 16 -> {
                val frames = size / (2 * ch)
                ShortArray(frames) { f ->
                    var sum = 0
                    for (c in 0 until ch) sum += buf.getShort(start + (f * ch + c) * 2).toInt()
                    (sum / ch).toShort()
                }
            }
            bits == 8 -> {
                val frames = size / ch
                ShortArray(frames) { f ->
                    var sum = 0
                    for (c in 0 until ch) sum += ((buf.get(start + f * ch + c).toInt() and 0xFF) - 128) shl 8
                    (sum / ch).toShort()
                }
            }
            bits == 32 && format == 3 -> {
                val frames = size / (4 * ch)
                ShortArray(frames) { f ->
                    var sum = 0f
                    for (c in 0 until ch) sum += buf.getFloat(start + (f * ch + c) * 4)
                    ((sum / ch).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                }
            }
            else -> error("Unsupported WAV format: $bits-bit")
        }
    }

    /** Linear resample; only used if the engine ever changes rate between chunks. */
    fun resample(samples: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || samples.isEmpty()) return samples
        val outLen = (samples.size.toLong() * to / from).toInt()
        return ShortArray(outLen) { i ->
            val src = i.toDouble() * from / to
            val i0 = src.toInt().coerceAtMost(samples.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(samples.lastIndex)
            val frac = src - i0
            (samples[i0] * (1 - frac) + samples[i1] * frac).toInt().toShort()
        }
    }
}
