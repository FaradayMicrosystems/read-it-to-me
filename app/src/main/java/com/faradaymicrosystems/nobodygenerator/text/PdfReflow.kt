package com.faradaymicrosystems.nobodygenerator.text

/**
 * PDF text arrives as hard-wrapped lines with page headers, footers, page numbers and
 * hyphenated words. This rebuilds proper paragraphs so speech flows naturally.
 */
object PdfReflow {
    private val PAGE_NUMBER = Regex("""^(page\s*)?\d{1,4}(\s*(of|/)\s*\d{1,4})?$""", RegexOption.IGNORE_CASE)
    private val ROMAN = Regex("""^[ivxlc]{1,6}$""", RegexOption.IGNORE_CASE)
    private val LIST_START = Regex("""^([•\-–*▪◦]|\d{1,3}[.)]|[a-z][.)])\s+""")
    private val DIGITS = Regex("""\d+""")
    private val TERMINAL = Regex("""[.!?:;"”’)]$""")

    fun reflow(pages: List<String>): List<String> {
        val pageLines = pages.map { page -> page.replace("\r", "").lines().map { it.trim() } }
        val repeated = repeatedEdgeLines(pageLines)

        val lines = mutableListOf<String>()
        for (page in pageLines) {
            val nonBlank = page.withIndex().filter { it.value.isNotEmpty() }.map { it.index }
            val edge = (nonBlank.take(2) + nonBlank.takeLast(2)).toSet()
            page.forEachIndexed { i, line ->
                val drop = line.isNotEmpty() && (
                    PAGE_NUMBER.matches(line) || ROMAN.matches(line) ||
                        (i in edge && edgeKey(line) in repeated)
                    )
                if (!drop) lines += line
            }
            lines += "" // page boundary is a soft break; merged below if mid-sentence
        }

        val lengths = lines.filter { it.length > 15 }.map { it.length }.sorted()
        val typical = if (lengths.isEmpty()) 0 else lengths[lengths.size / 2]

        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var prev = ""
        fun endParagraph() {
            if (current.isNotBlank()) paragraphs += current.toString().trim()
            current.setLength(0)
            prev = ""
        }

        for ((index, line) in lines.withIndex()) {
            if (line.isEmpty()) {
                // A blank line ends a paragraph, unless it is just a page break mid-sentence.
                val next = lines.drop(index + 1).firstOrNull { it.isNotEmpty() }
                val midSentence = prev.isNotEmpty() && !TERMINAL.containsMatchIn(prev) &&
                    next != null && next.first().isLowerCase()
                if (!midSentence) endParagraph()
                continue
            }
            if (current.isNotEmpty()) {
                val prevShort = typical > 30 && prev.length < typical * 0.6
                val breakHere = LIST_START.containsMatchIn(line) ||
                    (prevShort && TERMINAL.containsMatchIn(prev)) ||
                    (prevShort && !TERMINAL.containsMatchIn(prev) && line.first().isUpperCase())
                if (breakHere) {
                    endParagraph()
                    current.append(line)
                } else if (prev.endsWith("-") && prev.length > 1 && prev[prev.length - 2].isLetter() &&
                    line.first().isLowerCase()
                ) {
                    current.setLength(current.length - 1) // re-join hyphenated word
                    current.append(line)
                } else {
                    current.append(' ').append(line)
                }
            } else {
                current.append(line)
            }
            prev = line
        }
        endParagraph()
        return paragraphs
    }

    private fun edgeKey(line: String) = DIGITS.replace(line.lowercase(), "#")

    private fun repeatedEdgeLines(pages: List<List<String>>): Set<String> {
        if (pages.size < 3) return emptySet()
        val counts = HashMap<String, Int>()
        for (page in pages) {
            val nonBlank = page.filter { it.isNotEmpty() }
            (nonBlank.take(2) + nonBlank.takeLast(2)).map(::edgeKey).toSet().forEach {
                counts[it] = (counts[it] ?: 0) + 1
            }
        }
        val threshold = maxOf(2, (pages.size * 0.5).toInt())
        return counts.filterValues { it >= threshold }.keys
    }
}
