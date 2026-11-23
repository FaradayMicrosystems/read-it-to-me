package com.faradaymicrosystems.nobodygenerator.ui

import android.media.MediaPlayer
import com.faradaymicrosystems.nobodygenerator.data.Bookmarks
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
    val speed: Float = 1.0f,
)

/**
 * Plays one MP3 at a time inside the app. Speed carries over from track to track, and each
 * track resumes where it was last left (see [Bookmarks]).
 */
class Player(
    private val scope: CoroutineScope,
    initialSpeed: Float = 1.0f,
    private val bookmarks: Bookmarks? = null,
) {
    private var mp: MediaPlayer? = null
    private var ticker: Job? = null
    private var lastSavedAt = 0L
    private var speed = initialSpeed
    private val _state = MutableStateFlow(PlayerState(speed = speed))
    val state: StateFlow<PlayerState> = _state

    fun setSpeed(v: Float) {
        speed = v
        _state.value = _state.value.copy(speed = v)
        // Setting PlaybackParams on a paused MediaPlayer un-pauses it, so only apply while playing;
        // a paused track picks the new speed up when it resumes.
        mp?.let { if (it.isPlaying) applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    fun toggle(file: File, title: String) {
        val current = mp
        if (current != null && _state.value.file == file) {
            if (current.isPlaying) {
                current.pause()
                bookmark()
            } else {
                current.start()
                applySpeed(current)
            }
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
                bookmarks?.clear(file.name)
            }
        }
        val resume = bookmarks?.resumeAt(file.name, player.duration) ?: 0
        if (resume > 0) player.seekTo(resume)
        player.start()
        applySpeed(player)
        mp = player
        _state.value = PlayerState(file, title, true, resume, player.duration, speed)
        tick()
    }

    fun seekTo(ms: Int) {
        mp?.seekTo(ms)
        _state.value = _state.value.copy(positionMs = ms)
        bookmark(ms)
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
                // Save every few seconds so the spot survives the app being closed or killed.
                val now = System.currentTimeMillis()
                if (now - lastSavedAt >= SAVE_EVERY_MS) {
                    lastSavedAt = now
                    bookmark()
                }
                if (!p.isPlaying) break
                delay(250)
            }
        }
    }

    /** Saves the current spot for the loaded track. */
    fun bookmark(positionMs: Int? = null) {
        val p = mp ?: return
        val file = _state.value.file ?: return
        runCatching { bookmarks?.record(file.name, positionMs ?: p.currentPosition, p.duration) }
    }

    fun release() {
        ticker?.cancel()
        bookmark()
        mp?.release()
        mp = null
        _state.value = PlayerState(speed = speed)
    }

    private companion object {
        const val SAVE_EVERY_MS = 5_000L
    }
}
