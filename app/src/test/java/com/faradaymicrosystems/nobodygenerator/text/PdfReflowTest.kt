package com.faradaymicrosystems.nobodygenerator.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReflowTest {
    private fun page(n: Int, vararg body: String) =
        (listOf("Quarterly Report 2026") + body.toList() + listOf("Page $n of 3")).joinToString("\n")

    @Test fun rebuildsParagraphsAndDropsHeadersFooters() {
        val pages = listOf(
            page(1,
                "Introduction",
                "The quick brown fox jumps over the lazy dog and keeps run-",
                "ning across the field until it reaches the river bank where",
                "it finally stops.",
                "A second paragraph starts here and continues for a while so",
                "that it is long enough to look like normal body text and"),
            page(2,
                "this sentence continued from the previous page without a break",
                "and ends here.",
                "• First bullet point",
                "• Second bullet point"),
            page(3, "Conclusion", "All done now, thanks for reading this document today."),
        )
        val paras = PdfReflow.reflow(pages)
        val all = paras.joinToString("\n")
        assertFalse(all.contains("Quarterly Report"))
        assertFalse(all.contains("Page 1"))
        assertTrue(all.contains("keeps running across"))
        assertEquals("Introduction", paras[0])
        assertTrue(paras.any { it.startsWith("The quick brown fox") && it.endsWith("finally stops.") })
        assertTrue(paras.any { it.startsWith("A second paragraph") && it.contains("this sentence continued") })
        assertTrue(paras.contains("• First bullet point"))
        assertTrue(paras.contains("Conclusion"))
    }
}
