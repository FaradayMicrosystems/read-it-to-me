package com.faradaymicrosystems.nobodygenerator.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownReaderTest {
    private val md = """
        ---
        layout: post
        ---
        # My Notes

        Some **bold** text with a [link to docs](https://example.com/docs) and `inline code`.

        ## Steps
        - First item
        - Second *item*
        1. Numbered

        ```kotlin
        val x = 1
        ```

        | Name | Age |
        |------|-----|
        | Sam  | 30  |

        ![A cat](cat.png)

        Bare link: https://www.github.com/foo
    """.trimIndent()

    @Test fun readsMarkdownNaturally() {
        val doc = MarkdownReader.read(md, "fallback")
        val texts = doc.segments.map { it.text }
        assertEquals("My Notes", doc.title)
        assertEquals("My Notes.", texts[0])
        assertTrue(texts.contains("Some bold text with a link to docs and inline code."))
        assertTrue(texts.contains("Steps."))
        assertTrue(texts.contains("First item."))
        assertTrue(texts.contains("Second item."))
        assertTrue(texts.contains("Code example skipped."))
        assertTrue(texts.contains("Name, Age."))
        assertTrue(texts.contains("Sam, 30."))
        assertTrue(texts.any { it.contains("image: A cat") })
        assertTrue(texts.contains("Bare link: github.com/foo.") || texts.contains("Bare link: github.com."))
        val all = texts.joinToString(" ")
        listOf("#", "**", "`", "](", "layout", "val x").forEach { assertFalse("leaked '$it' in: $all", all.contains(it)) }
        assertTrue(doc.segments.first().isHeading)
    }

    @Test fun detectsMarkdown() {
        assertTrue(MarkdownReader.looksLikeMarkdown("# Title\n\nBody"))
        assertFalse(MarkdownReader.looksLikeMarkdown("Just a normal sentence. And another."))
    }
}
