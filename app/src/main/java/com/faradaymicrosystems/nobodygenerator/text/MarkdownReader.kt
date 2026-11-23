package com.faradaymicrosystems.nobodygenerator.text

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.jsoup.Jsoup

/** Turns Markdown into natural spoken segments: no hashes, asterisks, link URLs or code dumps. */
object MarkdownReader {
    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    private val FRONT_MATTER = Regex("""\A---\s*\n.*?\n---\s*\n""", RegexOption.DOT_MATCHES_ALL)

    fun read(markdown: String, fallbackTitle: String): SpeakableDocument {
        val source = FRONT_MATTER.replace(markdown.replace("\r\n", "\n"), "")
        val root = parser.parse(source)
        val out = SegmentBuilder()
        var title: String? = null
        fun block(node: Node) {
            when (node) {
                is Heading -> {
                    val text = inline(node)
                    if (title == null && node.level == 1) title = SpeechText.normalize(text)
                    out.pause(500)
                    out.add(text, pauseAfterMs = 650, heading = true)
                }
                is Paragraph -> out.add(inline(node), pauseAfterMs = if (node.parent is ListItem) 250 else 400)
                is FencedCodeBlock, is IndentedCodeBlock -> out.add("Code example skipped.", 400)
                is ThematicBreak -> out.pause(700)
                is HtmlBlock -> out.add(Jsoup.parse(node.literal).text())
                is TableBlock -> table(node, out)
                is BulletList, is OrderedList, is ListItem, is BlockQuote -> children(node).forEach(::block)
                else -> children(node).forEach(::block)
            }
        }
        children(root).forEach(::block)
        return SpeakableDocument(title?.takeIf { it.isNotBlank() } ?: fallbackTitle, out.build())
    }

    private fun table(table: TableBlock, out: SegmentBuilder) {
        val rows = mutableListOf<TableRow>()
        fun collect(n: Node) {
            if (n is TableRow) rows += n else children(n).forEach(::collect)
        }
        collect(table)
        for (row in rows) {
            val cells = children(row).filterIsInstance<TableCell>()
                .map { SpeechText.normalize(inline(it)) }
                .filter { it.isNotBlank() }
            out.add(cells.joinToString(", "), pauseAfterMs = 300)
        }
        out.pause(500)
    }

    fun inline(node: Node): String {
        val sb = StringBuilder()
        fun visit(n: Node) {
            when (n) {
                is Text -> sb.append(n.literal)
                is Code -> sb.append(n.literal)
                is SoftLineBreak, is HardLineBreak -> sb.append(' ')
                is HtmlInline -> Unit
                is Image -> {
                    val alt = collectText(n)
                    if (alt.isNotBlank()) sb.append(" (image: ").append(alt).append(") ")
                }
                is Link -> {
                    val label = collectText(n).trim()
                    if (label.isBlank() || label.startsWith("http", ignoreCase = true)) {
                        sb.append(SpeechText.spokenUrl(n.destination))
                    } else {
                        sb.append(label)
                    }
                }
                else -> children(n).forEach(::visit)
            }
        }
        children(node).forEach(::visit)
        return sb.toString()
    }

    private fun collectText(node: Node): String {
        val sb = StringBuilder()
        fun visit(n: Node) {
            when (n) {
                is Text -> sb.append(n.literal)
                is Code -> sb.append(n.literal)
                else -> children(n).forEach(::visit)
            }
        }
        children(node).forEach(::visit)
        return sb.toString()
    }

    private fun children(node: Node): List<Node> {
        val list = mutableListOf<Node>()
        var c = node.firstChild
        while (c != null) {
            list += c
            c = c.next
        }
        return list
    }

    /** Cheap check used when the user pastes text: does it look like Markdown? */
    fun looksLikeMarkdown(text: String): Boolean {
        val signals = listOf(
            Regex("""(?m)^#{1,6}\s+\S"""),
            Regex("""\*\*[^*\n]+\*\*"""),
            Regex("""\[[^\]\n]+]\([^)\s]+\)"""),
            Regex("""(?m)^```"""),
            Regex("""(?m)^\s*[-*+]\s+\S"""),
        )
        return signals.count { it.containsMatchIn(text) } >= 1
    }
}
