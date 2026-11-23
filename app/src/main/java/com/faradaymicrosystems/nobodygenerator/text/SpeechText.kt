package com.faradaymicrosystems.nobodygenerator.text

import java.net.URI
import java.text.Normalizer

/** One piece of text to speak, followed by a pause. */
data class Segment(
    val text: String,
    val pauseAfterMs: Int = 350,
    val isHeading: Boolean = false,
)

data class SpeakableDocument(val title: String, val segments: List<Segment>) {
    val characterCount: Int get() = segments.sumOf { it.text.length }
}

/** Builds a list of segments, dropping empties and merging pauses. */
class SegmentBuilder {
    private val items = mutableListOf<Segment>()

    fun add(raw: String, pauseAfterMs: Int = 350, heading: Boolean = false) {
        val cleaned = SpeechText.normalize(raw)
        if (cleaned.isBlank() || !cleaned.any { it.isLetterOrDigit() }) return
        val text = SpeechText.ensureSentenceEnd(cleaned)
        if (items.lastOrNull()?.text == text) return
        items += Segment(text, pauseAfterMs, heading)
    }

    fun pause(ms: Int) {
        val last = items.lastOrNull() ?: return
        items[items.lastIndex] = last.copy(pauseAfterMs = maxOf(last.pauseAfterMs, ms))
    }

    fun build(): List<Segment> = items.toList()
}

object SpeechText {
    private val URL = Regex("""\b(?:https?://|www\.)[^\s<>()\[\]"']+""", RegexOption.IGNORE_CASE)
    private val INVISIBLE = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
    private val BULLETS = Regex("[•▪◦●■□►▶✓✔☐☑]")
    private val MARKUP_RUNS = Regex("""(\*{2,}|_{2,}|~{2,}|`+)""")
    private val LINE_MARKERS = Regex("""(^|\s)(#{1,6}|>+)(?=\s)""")
    private val PIPES = Regex("""\s*\|\s*""")
    private val SPACES = Regex("""\s+""")
    private val SENTENCE_SPLIT = Regex("""(?<=[.!?…])["'”’)]*\s+""")
    private val ENDS_SENTENCE = Regex("""[.!?…:;]["'”’)\]]*$""")

    fun normalize(input: String): String {
        var t = Normalizer.normalize(input, Normalizer.Form.NFKC)
        t = INVISIBLE.replace(t, "")
        t = URL.replace(t) { spokenUrl(it.value) }
        t = t.replace("&", " and ")
        t = t.replace("→", " to ").replace("⇒", " to ")
        t = BULLETS.replace(t, " ")
        t = MARKUP_RUNS.replace(t, "")
        t = LINE_MARKERS.replace(t, " ")
        t = PIPES.replace(t, ", ")
        t = SPACES.replace(t, " ").trim()
        t = t.trim(',', ' ')
        return t
    }

    /** "https://www.abc.net.au/news/123" -> "abc.net.au". */
    fun spokenUrl(url: String): String {
        val withScheme = if (url.startsWith("www.", ignoreCase = true)) "http://$url" else url
        val host = runCatching { URI(withScheme.trimEnd('.', ',', ';', ':')).host }.getOrNull()
        return host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: "a link"
    }

    fun ensureSentenceEnd(text: String): String {
        val t = text.trimEnd()
        if (t.isEmpty() || ENDS_SENTENCE.containsMatchIn(t)) return t
        return "$t."
    }

    /** Splits text into pieces no longer than [max] characters, on sentence boundaries where possible. */
    fun chunk(text: String, max: Int): List<String> {
        require(max > 20)
        if (text.length <= max) return listOf(text)
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) pieces += current.toString().trim()
            current.setLength(0)
        }
        for (sentence in splitSentences(text)) {
            for (part in splitLong(sentence, max)) {
                if (current.length + part.length + 1 > max) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
        }
        flush()
        return pieces
    }

    fun splitSentences(text: String): List<String> =
        SENTENCE_SPLIT.split(text).map { it.trim() }.filter { it.isNotEmpty() }

    private fun splitLong(sentence: String, max: Int): List<String> {
        if (sentence.length <= max) return listOf(sentence)
        val out = mutableListOf<String>()
        var rest = sentence
        while (rest.length > max) {
            val window = rest.substring(0, max)
            var cut = window.lastIndexOf(", ")
            if (cut < max / 3) cut = window.lastIndexOf(' ')
            if (cut < max / 3) cut = max - 1
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }
}
