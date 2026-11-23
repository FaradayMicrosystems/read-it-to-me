package com.faradaymicrosystems.nobodygenerator.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test fun urlsBecomeHostNames() {
        assertEquals("See abc.net.au for more", SpeechText.normalize("See https://www.abc.net.au/news/12345?x=1 for more"))
    }

    @Test fun stripsLeftoverMarkupAndLigatures() {
        assertEquals("Bold and code fine", SpeechText.normalize("**Bold** & `code` ﬁne"))
    }

    @Test fun addsFullStopOnlyWhenMissing() {
        assertEquals("Hello.", SpeechText.ensureSentenceEnd("Hello"))
        assertEquals("Really?", SpeechText.ensureSentenceEnd("Really?"))
    }

    @Test fun chunksRespectLimitAndKeepAllWords() {
        val text = (1..400).joinToString(" ") { "Sentence number $it is here." }
        val chunks = SpeechText.chunk(text, 500)
        assertTrue(chunks.all { it.length <= 500 })
        assertEquals(text.split(" ").size, chunks.sumOf { it.split(" ").size })
        assertTrue(chunks.all { it.endsWith(".") })
    }

    @Test fun chunksSplitGiantSentence() {
        val giant = (1..300).joinToString(" ") { "word$it" }
        val chunks = SpeechText.chunk(giant, 200)
        assertTrue(chunks.all { it.length <= 200 })
        assertEquals(giant, chunks.joinToString(" "))
    }

    @Test fun segmentBuilderSkipsSymbolOnlyLines() {
        val b = SegmentBuilder()
        b.add("***")
        b.add("---")
        b.add("Real words")
        assertEquals(listOf("Real words."), b.build().map { it.text })
        assertFalse(b.build().isEmpty())
    }
}
