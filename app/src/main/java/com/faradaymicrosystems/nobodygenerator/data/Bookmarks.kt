package com.faradaymicrosystems.nobodygenerator.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where the listener got up to in each recording, keyed by file name. */
interface PositionStore {
    fun load(): Map<String, Int>
    fun save(key: String, ms: Int)
    fun remove(key: String)
}

class PrefsPositionStore(context: Context) : PositionStore {
    private val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    override fun load(): Map<String, Int> = prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    override fun save(key: String, ms: Int) = prefs.edit().putInt(key, ms).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}

/**
 * Auto-bookmarks: remembers the last play position of every recording so it resumes like a podcast.
 * Finishing a recording clears its bookmark, so the next play starts from the top.
 */
class Bookmarks(private val store: PositionStore) {
    private val _positions = MutableStateFlow(store.load())
    val positions: StateFlow<Map<String, Int>> = _positions

    /** Position to resume from, or 0 to start at the beginning. */
    fun resumeAt(key: String, durationMs: Int): Int {
        val saved = _positions.value[key] ?: return 0
        // Near the end counts as finished; back up a moment so the listener catches the last words.
        if (durationMs > 0 && saved >= durationMs - FINISHED_WITHIN_MS) return 0
        return (saved - REWIND_MS).coerceAtLeast(0)
    }

    fun record(key: String, positionMs: Int, durationMs: Int) {
        val finished = durationMs > 0 && positionMs >= durationMs - FINISHED_WITHIN_MS
        if (positionMs < MIN_MS || finished) {
            clear(key)
            return
        }
        if (_positions.value[key] == positionMs) return
        _positions.value = _positions.value + (key to positionMs)
        store.save(key, positionMs)
    }

    fun clear(key: String) {
        if (key !in _positions.value) return
        _positions.value = _positions.value - key
        store.remove(key)
    }

    companion object {
        const val MIN_MS = 1_000
        const val FINISHED_WITHIN_MS = 3_000
        const val REWIND_MS = 2_000
    }
}
