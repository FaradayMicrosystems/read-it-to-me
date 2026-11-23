package com.faradaymicrosystems.nobodygenerator.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlReaderTest {
    private val filler = (1..8).joinToString(" ") { "This is sentence $it of a fairly long paragraph about wombats." }
    private val html = """
        <html><head><title>Wombats | Nature Site</title><script>var x=1;</script></head>
        <body>
          <nav><a href="/">Home</a> <a href="/about">About</a></nav>
          <article>
            <h1>Wombats</h1>
            <p>$filler</p>
            <h2>Diet</h2>
            <p>Wombats eat grasses &amp; roots. See <a href="https://example.com">this page</a>.</p>
            <ul><li>Grass</li><li>Roots</li></ul>
            <pre>code();</pre>
            <p>$filler</p>
          </article>
          <footer>Copyright 2026 Nature Site</footer>
        </body></html>
    """.trimIndent()

    @Test fun extractsArticleInOrder() {
        val doc = HtmlReader.read(html, "https://nature.example/wombats")
        val texts = doc.segments.map { it.text }
        val all = texts.joinToString(" ")
        assertTrue(doc.title.contains("Wombats"))
        assertTrue(all.contains("Wombats eat grasses and roots. See this page."))
        assertTrue(texts.contains("Grass."))
        assertTrue(all.contains("Code example skipped."))
        assertTrue(all.indexOf("Diet") < all.indexOf("eat grasses"))
        assertFalse(all.contains("var x"))
        assertFalse(all.contains("Copyright"))
        assertEquals(1, texts.count { it.equals("Wombats.", ignoreCase = true) })
    }
}
