package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Link
import de.rholambdapi.hypp.LinkKind
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ReflowTest {
    private fun line(text: String) = Line(listOf(Span(text, TextStyle.Normal)))

    @Test
    fun joinsConsecutiveNonBlankLinesWithASpace() {
        val lines = listOf(line("The quick brown fox"), line("jumps over the lazy dog."))

        val result = reflowLines(lines)

        assertEquals(1, result.size)
        assertEquals("The quick brown fox jumps over the lazy dog.", result.single().text)
    }

    @Test
    fun blankLineStartsANewParagraph() {
        val lines = listOf(line("First paragraph,"), line("still first."), line(""), line("Second paragraph."))

        val result = reflowLines(lines)

        assertEquals(
            listOf("First paragraph, still first.", "", "Second paragraph."),
            result.map { it.text },
        )
    }

    @Test
    fun bulletLinesNeverMergeWithNeighbors() {
        val lines = listOf(
            line("Intro line."),
            line("· first item"),
            line("· second item"),
            line("Trailing line."),
        )

        val result = reflowLines(lines)

        assertEquals(
            listOf("Intro line.", "· first item", "· second item", "Trailing line."),
            result.map { it.text },
        )
    }

    @Test
    fun preservesLinksAndStylesAcrossAJoin() {
        val link = Link(kind = LinkKind.LINK, target = NodeIndex(1), lineNumber = null, label = "here")
        val lines = listOf(
            Line(listOf(Span("see ", TextStyle.Normal))),
            Line(listOf(Span("here", TextStyle.Normal, link))),
        )

        val result = reflowLines(lines)

        assertEquals(1, result.size)
        assertEquals("see  here", result.single().text)
        val linkSpan = result.single().spans.last()
        assertEquals(link, linkSpan.link)
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals(emptyList(), reflowLines(emptyList()))
    }

    @Test
    fun reflowAppliesToEveryNodeInADocument() {
        val doc = HypDocument(
            header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
            extendedHeaders = emptyList(),
            entries = emptyList(),
            charset = HypCharset.Default,
            nodes = listOf(
                Node(
                    index = NodeIndex(0),
                    name = "Home",
                    kind = NodeKind.TEXT,
                    windowTitle = null,
                    graphics = emptyList(),
                    crossReferences = emptyList(),
                    dataBlocks = emptyList(),
                    objectTable = emptyList(),
                    lines = listOf(line("wrapped"), line("text.")),
                ),
            ),
            images = emptyList(),
            diagnostics = emptyList(),
        )

        val result = reflow(doc)

        assertEquals(listOf("wrapped text."), result.nodes.single().lines.map { it.text })
    }
}
