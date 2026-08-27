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

    private fun entry(name: String, type: Int = IndexEntry.TYPE_INTERNAL) = IndexEntry(
        len = 0, type = type, seek = 0, compDiff = 0,
        next = 0, prev = 0, toc = 0, name = name, compressedLength = 0,
    )

    private fun node(index: Int, name: String, lines: List<Line>, kind: NodeKind = NodeKind.TEXT) = Node(
        index = NodeIndex(index), name = name, kind = kind, windowTitle = null,
        graphics = emptyList(), crossReferences = emptyList(), dataBlocks = emptyList(),
        objectTable = emptyList(), lines = lines,
    )

    private fun line(vararg spans: Span) = Line(spans.toList())

    private fun text(text: String) = Span(text, TextStyle.Normal)

    private fun linkSpan(target: Int, label: String) =
        Span(label, TextStyle.Normal, Link(LinkKind.LINK, NodeIndex(target), null, label))

    /** [entries] and [nodes] are index-aligned, as they are in a real parsed document. */
    private fun documentOf(entries: List<IndexEntry>, nodes: List<Node>) = HypDocument(
        header = Header(itableSize = 0, itableCount = entries.size, compilerVersion = 3, compilerOs = 2),
        extendedHeaders = emptyList(), entries = entries, charset = HypCharset.Default,
        nodes = nodes, images = emptyList(), diagnostics = emptyList(),
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
        popup = { label, content -> "POPUP($label){$content}" },
        stub = { label, description -> "STUB($label|$description)" },
        heading = { level, text, _ -> "#".repeat(level) + " " + text },
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
    fun headingReceivesNodeIndex() {
        val indexingSyntax = syntax.copy(heading = { level, text, index -> "H$index:" + "#".repeat(level) + " " + text })
        val rendered = renderMarkup(document, indexingSyntax)
        assertTrue(rendered.contains("H0:## Intro"))
        assertTrue(rendered.contains("H1:## Second"))
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

    // --- Targets with no section of their own (Group F; the same three cases HtmlSpans covers) ---

    @Test
    fun aPopupTargetIsInlinedAtTheLinkSiteAndGetsNoSectionOfItsOwn() {
        // Before Group F both nodes got a `## heading` section and the link was an ordinary
        // fragment, so a popup read as a page of its own — which ST-Guide never shows it as.
        val doc = documentOf(
            listOf(entry("Home"), entry("Pop", IndexEntry.TYPE_POPUP)),
            listOf(
                node(0, "Home", listOf(line(text("see "), linkSpan(1, "Pop")))),
                node(1, "Pop", listOf(line(text("first")), line(text("second"))), NodeKind.POPUP),
            ),
        )

        assertEquals("## Home\nsee POPUP(Pop){first\nsecond}", renderMarkup(doc, syntax))
    }

    @Test
    fun popupsThatLinkToEachOtherDoNotRecurse() {
        val doc = documentOf(
            listOf(entry("Home"), entry("A", IndexEntry.TYPE_POPUP), entry("B", IndexEntry.TYPE_POPUP)),
            listOf(
                node(0, "Home", listOf(line(linkSpan(1, "A")))),
                node(1, "A", listOf(line(linkSpan(2, "B"))), NodeKind.POPUP),
                node(2, "B", listOf(line(linkSpan(1, "A"))), NodeKind.POPUP),
            ),
        )

        // A popup reached from inside a popup degrades to its plain label — one level deep is all
        // an inline construct can nest legibly anyway, and it is what makes the walk terminate.
        assertEquals("## Home\nPOPUP(A){B}", renderMarkup(doc, syntax))
    }

    @Test
    fun anExternalRefIsStubbedRatherThanLinked() {
        val doc = documentOf(
            listOf(entry("Home"), entry("reflink.hyp/Main", IndexEntry.TYPE_EXTERNAL_REF)),
            listOf(node(0, "Home", listOf(line(linkSpan(1, "RefLink"))))),
        )

        val rendered = renderMarkup(doc, syntax)
        assertEquals("## Home\nSTUB(RefLink|external reference: reflink.hyp/Main)", rendered)
        assertFalse(rendered.contains("(#1)"), rendered)
    }

    @Test
    fun aSystemActionIsStubbedRatherThanLinked() {
        val doc = documentOf(
            listOf(entry("Home"), entry("stool.Tos", IndexEntry.TYPE_QUIT)),
            listOf(node(0, "Home", listOf(line(linkSpan(1, "Quit"))))),
        )

        assertEquals(
            "## Home\nSTUB(Quit|viewer action, not available in this document: stool.Tos)",
            renderMarkup(doc, syntax),
        )
    }

    @Test
    fun anOrdinaryNodeTargetIsStillAnOrdinaryLink() {
        val doc = documentOf(
            listOf(entry("Home"), entry("Other")),
            listOf(
                node(0, "Home", listOf(line(linkSpan(1, "Other")))),
                node(1, "Other", listOf(line(text("tail")))),
            ),
        )

        assertEquals("## Home\n[Other](#1)\n\n## Other\ntail", renderMarkup(doc, syntax))
    }

    @Test
    fun headingsLabelsAndStubDescriptionsAllGoThroughEscape() {
        // Every one of these is raw `.hyp` bytes reaching a markup sink; only `escape` knows how to
        // neutralise them for the dialect at hand.
        val doc = documentOf(
            listOf(entry("*Home*"), entry("*evil*.hyp/*node*", IndexEntry.TYPE_EXTERNAL_REF)),
            listOf(node(0, "*Home*", listOf(line(linkSpan(1, "*Ref*"))))),
        )

        assertEquals(
            "## \\*Home\\*\nSTUB(\\*Ref\\*|external reference: \\*evil\\*.hyp/\\*node\\*)",
            renderMarkup(doc, syntax),
        )
    }
}
