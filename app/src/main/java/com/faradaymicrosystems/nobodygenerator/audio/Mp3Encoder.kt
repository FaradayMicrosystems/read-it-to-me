package com.faradaymicrosystems.nobodygenerator.audio

import java.io.Closeable

/** Streams mono 16-bit PCM into MP3 using the bundled LAME library. */
class Mp3Encoder(sampleRate: Int, kbps: Int = 64, title: String? = null) : Closeable {
    private var handle: Long = nativeInit(sampleRate, kbps, title, "Read It To Me")

    init {
        check(handle != 0L) { "MP3 encoder failed to start" }
    }

    fun encode(pcm: ShortArray): ByteArray = nativeEncode(handle, pcm, pcm.size)

    fun flush(): ByteArray = nativeFlush(handle)

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("nobodylame")
        }

        @JvmStatic private external fun nativeInit(sampleRate: Int, kbps: Int, title: String?, artist: String?): Long
        @JvmStatic private external fun nativeEncode(handle: Long, pcm: ShortArray, count: Int): ByteArray
        @JvmStatic private external fun nativeFlush(handle: Long): ByteArray
        @JvmStatic private external fun nativeClose(handle: Long)
    }
}
