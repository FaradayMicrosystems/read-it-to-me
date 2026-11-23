package com.faradaymicrosystems.nobodygenerator.text

object PlainTextReader {
    private val PARAGRAPH_BREAK = Regex("""\n\s*\n""")

    fun read(text: String, title: String): SpeakableDocument {
        val out = SegmentBuilder()
        PARAGRAPH_BREAK.split(text.replace("\r\n", "\n"))
            .map { para -> para.lines().joinToString(" ") { it.trim() } }
            .forEach { out.add(it, pauseAfterMs = 450) }
        return SpeakableDocument(title, out.build())
    }
}
