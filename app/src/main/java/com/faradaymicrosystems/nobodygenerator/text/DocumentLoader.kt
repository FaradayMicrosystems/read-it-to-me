package com.faradaymicrosystems.nobodygenerator.text

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Where the words come from. */
sealed interface Source {
    data class LocalFile(val path: String, val displayName: String) : Source
    data class WebPage(val url: String) : Source
    data class PastedText(val text: String) : Source
}

class UnreadableDocumentException(message: String) : Exception(message)

object DocumentLoader {
    private val URL_ONLY = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

    /** Turns whatever is in the input box into a source. */
    fun fromInputText(text: String): Source {
        val trimmed = text.trim()
        return if (URL_ONLY.matches(trimmed)) Source.WebPage(trimmed) else Source.PastedText(trimmed)
    }

    fun load(context: Context, source: Source): SpeakableDocument = when (source) {
        is Source.WebPage -> HtmlReader.fetch(source.url)
        is Source.PastedText -> {
            val title = source.text.lineSequence().firstOrNull { it.isNotBlank() }
                ?.trim()?.removePrefix("#")?.trim()?.take(60) ?: "Pasted text"
            if (MarkdownReader.looksLikeMarkdown(source.text)) {
                MarkdownReader.read(source.text, title)
            } else {
                PlainTextReader.read(source.text, title)
            }
        }
        is Source.LocalFile -> loadFile(context, File(source.path), source.displayName)
    }

    private fun loadFile(context: Context, file: File, displayName: String): SpeakableDocument {
        val title = displayName.substringBeforeLast('.').ifBlank { displayName }
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val header = file.inputStream().use { s -> ByteArray(5).also { s.read(it) } }
        val isPdf = ext == "pdf" || String(header, Charsets.ISO_8859_1) == "%PDF-"
        return when {
            isPdf -> readPdf(context, file, title)
            ext in setOf("md", "markdown", "mdown", "mkd", "mkdn") -> MarkdownReader.read(file.readText(), title)
            ext in setOf("html", "htm", "xhtml") -> HtmlReader.read(file.readText(), null, title)
            else -> {
                val text = file.readText()
                if (text.count { it == '\u0000' } > 0) {
                    throw UnreadableDocumentException("That file type can't be read. Try a PDF, Markdown, text or HTML file.")
                }
                if (ext != "txt" && MarkdownReader.looksLikeMarkdown(text)) {
                    MarkdownReader.read(text, title)
                } else {
                    PlainTextReader.read(text, title)
                }
            }
        }
    }

    private fun readPdf(context: Context, file: File, fallbackTitle: String): SpeakableDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper().apply { lineSeparator = "\n" }
            val pages = (1..doc.numberOfPages).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(doc)
            }
            if (pages.all { it.isBlank() }) {
                throw UnreadableDocumentException(
                    "This PDF has no text to read. It's probably scanned images.",
                )
            }
            val out = SegmentBuilder()
            PdfReflow.reflow(pages).forEach { out.add(it, pauseAfterMs = 450) }
            val title = doc.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle
            return SpeakableDocument(title, out.build())
        }
    }
}
