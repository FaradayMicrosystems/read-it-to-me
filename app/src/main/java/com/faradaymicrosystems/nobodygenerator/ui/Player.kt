package com.faradaymicrosystems.nobodygenerator.ui

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val file: File? = null,
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

/** Plays one MP3 at a time inside the app. */
class Player(private val scope: CoroutineScope) {
    private var mp: MediaPlayer? = null
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    fun toggle(file: File, title: String) {
        val current = mp
        if (current != null && _state.value.file == file) {
            if (current.isPlaying) current.pause() else current.start()
            _state.value = _state.value.copy(playing = current.isPlaying)
            if (current.isPlaying) tick()
            return
        }
        release()
        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener {
                _state.value = _state.value.copy(playing = false, positionMs = 0)
                seekTo(0)
            }
            start()
        }
        mp = player
        _state.value = PlayerState(file, title, true, 0, player.duration)
        tick()
    }

    fun seekTo(ms: Int) {
        mp?.seekTo(ms)
        _state.value = _state.value.copy(positionMs = ms)
    }

    fun skip(deltaMs: Int) {
        val p = mp ?: return
        seekTo((p.currentPosition + deltaMs).coerceIn(0, p.duration))
    }

    fun stopIf(file: File) {
        if (_state.value.file == file) release()
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = mp ?: break
                runCatching {
                    _state.value = _state.value.copy(positionMs = p.currentPosition, playing = p.isPlaying)
                }
                if (!p.isPlaying) break
                delay(250)
            }
        }
    }

    fun release() {
        ticker?.cancel()
        mp?.release()
        mp = null
        _state.value = PlayerState()
    }
}
