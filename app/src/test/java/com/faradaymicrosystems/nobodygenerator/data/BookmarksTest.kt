package com.faradaymicrosystems.nobodygenerator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarksTest {
    private class MemStore : PositionStore {
        val map = mutableMapOf<String, Int>()
        override fun load() = map.toMap()
        override fun save(key: String, ms: Int) { map[key] = ms }
        override fun remove(key: String) { map.remove(key) }
    }

    @Test fun resumesWhereLeftOffWithSmallRewind() {
        val store = MemStore()
        Bookmarks(store).record("a.mp3", 120_000, 600_000)
        // A fresh instance (app restarted) still knows the spot.
        assertEquals(118_000, Bookmarks(store).resumeAt("a.mp3", 600_000))
    }

    @Test fun eachFileKeepsItsOwnSpot() {
        val b = Bookmarks(MemStore())
        b.record("a.mp3", 10_000, 60_000)
        b.record("b.mp3", 40_000, 60_000)
        assertEquals(10_000, b.positions.value["a.mp3"])
        assertEquals(40_000, b.positions.value["b.mp3"])
    }

    @Test fun unplayedStartsAtZero() {
        assertEquals(0, Bookmarks(MemStore()).resumeAt("new.mp3", 60_000))
    }

    @Test fun finishingClearsTheBookmark() {
        val store = MemStore()
        val b = Bookmarks(store)
        b.record("a.mp3", 30_000, 60_000)
        b.record("a.mp3", 59_000, 60_000)
        assertNull(b.positions.value["a.mp3"])
        assertNull(store.map["a.mp3"])
        assertEquals(0, b.resumeAt("a.mp3", 60_000))
    }

    @Test fun seekingBackToStartClears() {
        val b = Bookmarks(MemStore())
        b.record("a.mp3", 30_000, 60_000)
        b.record("a.mp3", 0, 60_000)
        assertNull(b.positions.value["a.mp3"])
    }
}
