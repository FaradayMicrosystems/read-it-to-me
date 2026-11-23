package com.faradaymicrosystems.nobodygenerator.text

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Extracts the readable article from a web page (no menus, ads or footers) and speaks it in order. */
object HtmlReader {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val SKIP = setOf(
        "script", "style", "noscript", "nav", "footer", "aside", "form", "button",
        "svg", "iframe", "select", "input", "template",
    )
    private val BLOCKS = HEADINGS + setOf(
        "p", "li", "blockquote", "pre", "figcaption", "td", "th", "dt", "dd", "div",
        "section", "article", "main", "header", "table", "thead", "tbody", "tr", "ul", "ol",
        "dl", "figure", "details", "summary",
    )

    fun fetch(url: String): SpeakableDocument {
        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .followRedirects(true)
            .timeout(30_000)
            .maxBodySize(15 * 1024 * 1024)
            .get()
        return read(doc.outerHtml(), doc.location().ifBlank { url })
    }

    fun read(html: String, url: String?, fallbackTitle: String = "Article"): SpeakableDocument {
        val page = Jsoup.parse(html, url ?: "")
        var title = page.title().trim()
        var content: Element? = null
        runCatching {
            val article = Readability4J(url ?: "https://localhost/", html).parse()
            article.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
            content = article.articleContent
        }
        val body = content?.takeIf { it.text().length >= 200 } ?: page.body()

        val out = SegmentBuilder()
        if (title.isNotBlank()) out.add(title, pauseAfterMs = 800, heading = true)
        walk(body, out)
        val segments = out.build().let { segs ->
            // Readability often repeats the title as the first heading.
            if (segs.size > 1 && segs[0].text.equals(segs[1].text, ignoreCase = true)) segs.drop(1) else segs
        }
        return SpeakableDocument(title.ifBlank { fallbackTitle }, segments)
    }

    private fun walk(el: Element, out: SegmentBuilder) {
        for (child in el.children()) {
            val tag = child.normalName()
            when {
                tag in SKIP -> Unit
                tag == "pre" -> out.add("Code example skipped.", 400)
                tag in HEADINGS -> {
                    out.pause(500)
                    out.add(child.text(), pauseAfterMs = 650, heading = true)
                }
                tag == "tr" -> out.add(child.children().joinToString(", ") { it.text() }, pauseAfterMs = 300)
                tag == "img" -> child.attr("alt").takeIf { it.isNotBlank() }?.let { out.add("Image: $it", 300) }
                hasBlockChild(child) -> walk(child, out)
                tag in BLOCKS -> out.add(child.text(), pauseAfterMs = if (tag == "li") 250 else 400)
                else -> Unit
            }
        }
    }

    private fun hasBlockChild(el: Element): Boolean = el.children().any { it.normalName() in BLOCKS }
}
