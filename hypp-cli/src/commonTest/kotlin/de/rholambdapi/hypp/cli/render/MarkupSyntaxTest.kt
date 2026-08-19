package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.IndexEntry
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkupSyntaxTest {
    // TextStyle's attribute bits are the format's own vector; the constants are private to the
    // library, so `attributeBitsAreAsAssumed` guards these literals.
    private fun styled(attributes: Int) = TextStyle(TextStyle.Normal.bits or attributes)

    private val bold = styled(1)
    private val italic = styled(4)
    private val underlined = styled(8)
    private val boldItalic = styled(1 or 4)

    private fun entry(name: String) = IndexEntry(
        len = 0, type = IndexEntry.TYPE_INTERNAL, seek = 0, compDiff = 0,
        next = 0, prev = 0, toc = 0, name = name, compressedLength = 0,
    )

    private fun node(index: Int, name: String, lines: List<Line>) = Node(
        index = NodeIndex(index), name = name, kind = NodeKind.TEXT, windowTitle = null,
        graphics = emptyList(), crossReferences = emptyList(), dataBlocks = emptyList(),
        objectTable = emptyList(), lines = lines,
    )

    private val document = HypDocument(
        header = Header(itableSize = 0, itableCount = 2, compilerVersion = 3, compilerOs = 2),
        extendedHeaders = emptyList(),
        entries = listOf(entry("Intro"), entry("Second")),
        charset = HypCharset.Default,
        nodes = listOf(
            node(
                0, "Intro",
                listOf(
                    Line(
                        listOf(
                            Span("plain ", TextStyle.Normal),
                            Span("bold", bold),
                            Span(" and ", TextStyle.Normal),
                            Span("ital", italic),
                        ),
                    ),
                    Line(
                        listOf(
                            Span("under", underlined),
                            Span("bi", boldItalic),
                            Span("*star*", TextStyle.Normal),
                            Span("Go", TextStyle.Normal, Link(LinkKind.LINK, NodeIndex(7), null, "Go")),
                        ),
                    ),
                ),
            ),
            node(1, "Second", listOf(Line(listOf(Span("tail", TextStyle.Normal))))),
        ),
        images = emptyList(),
        diagnostics = emptyList(),
    )

    private val syntax = MarkupSyntax(
        boldOpen = "**", boldClose = "**",
        italicOpen = "_", italicClose = "_",
        underlineOpen = "++", underlineClose = "++",
        link = { label, target -> "[$label](#$target)" },
        heading = { level, text -> "#".repeat(level) + " " + text },
        escape = { it.replace("*", "\\*") },
    )

    @Test
    fun attributeBitsAreAsAssumed() {
        assertTrue(bold.isBold)
        assertFalse(bold.isItalic || bold.isUnderlined)
        assertTrue(italic.isItalic)
        assertFalse(italic.isBold || italic.isUnderlined)
        assertTrue(underlined.isUnderlined)
        assertFalse(underlined.isBold || underlined.isItalic)
        assertTrue(boldItalic.isBold && boldItalic.isItalic)
        assertFalse(boldItalic.isUnderlined)
    }

    @Test
    fun walksNodesLinesAndSpans() {
        val expected = """
            ## Intro
            plain **bold** and _ital_
            ++under++**_bi_**\*star\*[Go](#7)

            ## Second
            tail
        """.trimIndent()
        assertEquals(expected, renderMarkup(document, syntax))
    }

    @Test
    fun emptyDocumentRendersEmpty() {
        val empty = HypDocument(
            header = Header(itableSize = 0, itableCount = 0, compilerVersion = 3, compilerOs = 2),
            extendedHeaders = emptyList(), entries = emptyList(), charset = HypCharset.Default,
            nodes = emptyList(), images = emptyList(), diagnostics = emptyList(),
        )
        assertEquals("", renderMarkup(empty, syntax))
    }

    @Test
    fun nodeWithoutLinesIsJustItsHeading() {
        val headingOnly = HypDocument(
            header = Header(itableSize = 0, itableCount = 1, compilerVersion = 3, compilerOs = 2),
            extendedHeaders = emptyList(), entries = listOf(entry("Bare")), charset = HypCharset.Default,
            nodes = listOf(node(0, "Bare", emptyList())), images = emptyList(), diagnostics = emptyList(),
        )
        assertEquals("## Bare", renderMarkup(headingOnly, syntax))
    }
}
