package com.faradaymicrosystems.nobodygenerator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays a short clip of PCM (used for voice samples). */
object PcmPlayer {
    private var track: AudioTrack? = null

    @Synchronized
    fun play(pcm: Pcm) {
        stop()
        if (pcm.samples.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcm.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.samples.size * 2)
            .build()
        t.write(pcm.samples, 0, pcm.samples.size)
        t.play()
        track = t
    }

    @Synchronized
    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }
}
