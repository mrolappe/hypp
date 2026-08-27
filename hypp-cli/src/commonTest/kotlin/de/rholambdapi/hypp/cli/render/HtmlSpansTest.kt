package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypColor
import de.rholambdapi.hypp.HypDocument
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hand-derived expected strings from the ported hyp2html span-rendering rules (bold/italic/underline
 * nesting order b-i-u outermost-in, colour span only when it differs from [TextStyle.Normal]) — so a
 * bug in the [HtmlSpans] port shows up as a mismatch here, rather than the test being written to match
 * whatever the port happens to produce.
 */
class HtmlSpansTest {
    // TextStyle's public contract (KDoc on TextStyle.kt): bits 0-5 attribute vector, bits 8-11
    // foreground colour index, bits 12-15 background colour index.
    private fun style(attrBits: Int, fg: HypColor = HypColor.BLACK, bg: HypColor = HypColor.WHITE): TextStyle =
        TextStyle(attrBits or (fg.ordinal shl 8) or (bg.ordinal shl 12))

    private val bold = 1
    private val italic = 4
    private val underlined = 8

    @Test
    fun plainText() {
        assertEquals("plain", HtmlSpans.renderSpan(Span("plain", TextStyle.Normal)))
    }

    @Test
    fun boldText() {
        assertEquals("<b>bold</b>", HtmlSpans.renderSpan(Span("bold", style(bold))))
    }

    @Test
    fun italicText() {
        assertEquals("<i>italic</i>", HtmlSpans.renderSpan(Span("italic", style(italic))))
    }

    @Test
    fun underlinedText() {
        assertEquals("<u>underlined</u>", HtmlSpans.renderSpan(Span("underlined", style(underlined))))
    }

    @Test
    fun combinedBoldItalicUnderlined() {
        // Ported logic wraps sequentially (bold first, then italic, then underlined), so each
        // later wrap ends up outermost: underlined-outermost, bold-innermost — not the b-outermost
        // nesting a naive reading of "bold/italic/underlined, in that order" might suggest.
        assertEquals(
            "<u><i><b>all</b></i></u>",
            HtmlSpans.renderSpan(Span("all", style(bold or italic or underlined))),
        )
    }

    @Test
    fun linkSpan() {
        val link = Link(kind = LinkKind.LINK, target = NodeIndex(5), lineNumber = null, label = "go")
        assertEquals("<a href=\"#5\">go</a>", HtmlSpans.renderSpan(Span("go", TextStyle.Normal, link)))
    }

    @Test
    fun linkSpanWithCustomMarkup() {
        val link = Link(kind = LinkKind.LINK, target = NodeIndex(5), lineNumber = null, label = "go")
        val html = HtmlSpans.renderSpan(Span("go", TextStyle.Normal, link)) { text, target ->
            "<a href=\"node-${target.value}.xhtml\">$text</a>"
        }
        assertEquals("<a href=\"node-5.xhtml\">go</a>", html)
    }

    @Test
    fun linkTextIsEscapedBeforeReachingTheRenderersOwnMarkup() {
        val link = Link(kind = LinkKind.LINK, target = NodeIndex(5), lineNumber = null, label = "go")
        val html = HtmlSpans.renderSpan(Span("<b>&", TextStyle.Normal, link)) { text, _ -> text }
        assertEquals("&lt;b&gt;&amp;", html)
    }

    @Test
    fun foregroundColoredSpan() {
        assertEquals(
            "<span style=\"color:rgb(255,0,0);\">red</span>",
            HtmlSpans.renderSpan(Span("red", style(0, fg = HypColor.RED))),
        )
    }

    @Test
    fun backgroundColoredSpan() {
        assertEquals(
            "<span style=\"background-color:rgb(255,0,0);\">bg</span>",
            HtmlSpans.renderSpan(Span("bg", style(0, bg = HypColor.RED))),
        )
    }

    @Test
    fun textWithHtmlSpecialCharacters() {
        assertEquals("a &amp; b &lt; c &gt; d", HtmlSpans.escapeHtml("a & b < c > d"))
        assertEquals(
            "&lt;script&gt;&amp;",
            HtmlSpans.renderSpan(Span("<script>&", TextStyle.Normal)),
        )
    }

    // The exact bug reported against a real corpus file: a decoded `.hyp` line containing a
    // literal byte 0x03 (an Atari-font icon glyph AtariSt's clean-room table has no mapping for)
    // broke EPUB/XHTML well-formedness — a strict XML reader renders only up to that byte, which
    // looks like the document got truncated. Every C0 control code but tab/LF/CR is exhaustively
    // covered here (the whole domain is 32 values, so exhaustive beats sampling).
    @Test
    fun everyXmlUnsafeControlCharacterBecomesAControlPicturesGlyph() {
        for (code in 0..0x1F) {
            if (code == 0x09 || code == 0x0A || code == 0x0D) continue
            val escaped = HtmlSpans.escapeHtml(Char(code).toString())
            assertEquals(Char(0x2400 + code).toString(), escaped, "byte 0x${code.toString(16)}")
        }
    }

    @Test
    fun tabNewlineAndCarriageReturnPassThroughUnescaped() {
        assertEquals("\t\n\r", HtmlSpans.escapeHtml("\t\n\r"))
    }

    @Test
    fun controlCharacterInTheMiddleOfARealSentenceIsNeutralizedInPlace() {
        assertEquals(
            "Control - ${Char(0x2403)} !",
            HtmlSpans.escapeHtml("Control - ${Char(0x03)} !"),
        )
    }

    private fun node(lines: List<Line>, graphics: List<Graphic> = emptyList()) = Node(
        index = NodeIndex(0),
        name = "n",
        kind = NodeKind.TEXT,
        windowTitle = null,
        graphics = graphics,
        crossReferences = emptyList(),
        dataBlocks = emptyList(),
        objectTable = emptyList(),
        lines = lines,
    )

    @Test
    fun renderGridWithNoGraphicsIsOneParagraphAndNoWrapperDivs() {
        val n = node(listOf(Line(listOf(Span("only text", TextStyle.Normal)))))

        val html = HtmlSpans.renderGrid(n) { null }

        assertEquals(
            "<div style=\"position:relative;line-height:1\"><p style=\"margin:0\">only text</p></div>",
            html,
        )
    }

    @Test
    fun renderGridPositionsANonCenteredGraphicByItsRealXAndY() {
        val graphic = Graphic.Line(x = 5, y = 2, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val n = node(List(3) { Line(emptyList()) }, listOf(graphic))

        val html = HtmlSpans.renderGrid(n) { null }

        assertTrue(html.contains("<div style=\"position:absolute;z-index:1;top:2em;left:5ch\">"), html)
    }

    private fun textLine(text: String) = Line(listOf(Span(text, TextStyle.Normal)))

    @Test
    fun renderGridCentersAGraphicWhenXIsZero() {
        // `x == 0` means centred for images only, and centred means "within this node's own text
        // column" — the character grid the rest of the node is laid out on — not within a viewport
        // whose width the .hyp format knows nothing about.
        val graphic = Graphic.Image(NodeIndex(1), x = 0, y = 1, width = 0, height = 0, ditherMask = null)
        val n = node(listOf(textLine("0123456789"), textLine("012345")), listOf(graphic))

        val html = HtmlSpans.renderGrid(n) { "<img/>" }

        assertTrue(
            html.contains(
                "<div style=\"position:absolute;z-index:1;top:1em;left:calc(10ch / 2);transform:translateX(-50%)\">",
            ),
            html,
        )
    }

    @Test
    fun renderGridPlacesAVectorGraphicAtItsRawXEvenWhenThatXIsZero() {
        // The format gives @line/@box/@rbox an x of 1-255 — "centred" is not one of their
        // placement modes, so a zero there is a column, never a centring request.
        val graphic = Graphic.Line(x = 0, y = 1, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val n = node(List(2) { Line(emptyList()) }, listOf(graphic))

        val html = HtmlSpans.renderGrid(n) { null }

        assertTrue(html.contains("top:1em;left:0ch"), html)
        assertTrue(!html.contains("translateX"), html)
    }

    @Test
    fun renderGridFlowsALineImageBetweenTheRowsItSplitsInsteadOfOverlayingThem() {
        // The st-guide "Main" bug: its banner is an @limage (width == 1), which ST-Guide treats as
        // a line — the text below it moves down. Rendered as an overlay it landed on top of the
        // node's whole table of contents instead.
        val banner = Graphic.Image(NodeIndex(1), x = 0, y = 1, width = 1, height = 0, ditherMask = null)
        val n = node(listOf(textLine("header"), textLine("contents"), textLine("more")), listOf(banner))

        val html = HtmlSpans.renderGrid(n) { "<img/>" }

        assertEquals(
            "<div style=\"position:relative;line-height:1\"><p style=\"margin:0\">header</p></div>" +
                "<div style=\"width:8ch;text-align:center\"><img/></div>" +
                "<div style=\"position:relative;line-height:1\"><p style=\"margin:0\">contents\nmore</p></div>",
            html,
        )
        assertTrue(!html.contains("position:absolute"), html)
    }

    @Test
    fun renderGridOffsetsAnOverlayBelowALineImageIntoItsOwnSegment() {
        // An overlay's `top:<row>em` is only meaningful relative to the rows it shares a container
        // with — once a line image pushes the rows below it down, its overlays must move too.
        val banner = Graphic.Image(NodeIndex(1), x = 3, y = 1, width = 1, height = 0, ditherMask = null)
        val box = Graphic.Box(x = 2, y = 3, width = 4, height = 1, fillPattern = 0)
        val n = node(List(5) { textLine("0123456") }, listOf(banner, box))

        val html = HtmlSpans.renderGrid(n) { "<img/>" }

        assertTrue(html.contains("<div style=\"margin-left:3ch\"><img/></div>"), html)
        assertTrue(html.contains("position:absolute;z-index:1;top:2em;left:2ch"), html)
    }

    @Test
    fun anOverlayClampedPastTheLastRowIsStillDrawnOnlyOnceAlongsideALineImageThere() {
        // Both clamp to row == lines.size, which makes the segment *before* the line image end
        // there too — the out-of-range overlay must not be claimed by that segment and again by
        // the trailing one.
        val banner = Graphic.Image(NodeIndex(1), x = 2, y = 99, width = 1, height = 0, ditherMask = null)
        val box = Graphic.Box(x = 4, y = 99, width = 3, height = 1, fillPattern = 0)
        val n = node(List(2) { textLine("ab") }, listOf(banner, box))

        val html = HtmlSpans.renderGrid(n) { "<img/>" }

        assertEquals(1, Regex("position:absolute").findAll(html).count(), html)
    }

    @Test
    fun renderGridClampsOutOfRangeYToTheNodesRowBounds() {
        val pastTheEnd = Graphic.Box(x = 3, y = 99, width = 10, height = 1, fillPattern = 0)
        val negative = Graphic.RoundedBox(x = 3, y = -5, width = 10, height = 1, fillPattern = 0)
        val n = node(List(5) { Line(emptyList()) }, listOf(pastTheEnd, negative))

        val html = HtmlSpans.renderGrid(n) { null }

        assertTrue(html.contains("top:5em;left:3ch"), html)
        assertTrue(html.contains("top:0em;left:3ch"), html)
    }

    @Test
    fun renderGridNeedsOnlyTopForAMultiRowGraphic() {
        // A height=3 RoundedBox (the real y=55,height=3 corpus case) is positioned by its start
        // row alone — VectorGraphicSvg already sizes the graphic's own markup `height`em tall, so
        // no per-row bucketing/spanning is needed here.
        val roundedBox = Graphic.RoundedBox(x = 2, y = 4, width = 10, height = 3, fillPattern = 0)
        val n = node(List(8) { Line(emptyList()) }, listOf(roundedBox))

        val html = HtmlSpans.renderGrid(n) { null }

        assertTrue(html.contains("<div style=\"position:absolute;z-index:1;top:4em;left:2ch\">"), html)
        assertEquals(1, Regex("position:absolute").findAll(html).count())
    }

    // --- Popups (bug 8) and external refs (bug 9): targets with no page of their own ---

    private fun entry(type: Int, name: String) =
        IndexEntry(len = 0, type = type, seek = 0, compDiff = 0, next = 0, prev = 0, toc = 0, name = name, compressedLength = 0)

    /** [entries] and [nodes] are index-aligned, as they are in a real parsed document. */
    private fun document(entries: List<IndexEntry>, nodes: List<Node> = emptyList()) = HypDocument(
        header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
        extendedHeaders = emptyList(),
        entries = entries,
        charset = HypCharset.Default,
        nodes = nodes,
        images = emptyList(),
        diagnostics = emptyList(),
    )

    private fun popupNode(index: Int, name: String, text: String) = Node(
        index = NodeIndex(index),
        name = name,
        kind = NodeKind.POPUP,
        windowTitle = null,
        graphics = emptyList(),
        crossReferences = emptyList(),
        dataBlocks = emptyList(),
        objectTable = emptyList(),
        lines = listOf(textLine(text)),
    )

    @Test
    fun anOrdinaryNodeTargetHasNoStubContentAndIsNotAStubTarget() {
        val doc = document(listOf(entry(IndexEntry.TYPE_INTERNAL, "Home")), listOf(node(listOf(textLine("hi")))))

        assertNull(HtmlSpans.stubContent(doc, NodeIndex(0)))
        assertTrue(!HtmlSpans.isStubTarget(doc, NodeIndex(0)))
    }

    @Test
    fun anOutOfRangeTargetHasNoStubContent() {
        val doc = document(emptyList())

        assertNull(HtmlSpans.stubContent(doc, NodeIndex(7)))
        assertTrue(!HtmlSpans.isStubTarget(doc, NodeIndex(7)))
    }

    @Test
    fun aPopupTargetsStubContentIsThePopupNodesOwnGrid() {
        val doc = document(
            listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_POPUP, "Pop")),
            listOf(node(listOf(textLine("hi"))), popupNode(1, "Pop", "a & b")),
        )

        assertTrue(HtmlSpans.isStubTarget(doc, NodeIndex(1)))
        assertEquals(
            "<div style=\"position:relative;line-height:1\"><p style=\"margin:0\">a &amp; b</p></div>",
            HtmlSpans.stubContent(doc, NodeIndex(1)),
        )
    }

    @Test
    fun popupStubContentIsEscapedExactlyOnce() {
        // The grid comes back from renderGrid already escaped; a renderer that re-escaped it before
        // dropping it into a <dialog>/<details> would show a literal `&amp;` to the reader.
        val doc = document(
            listOf(entry(IndexEntry.TYPE_POPUP, "Pop")),
            listOf(popupNode(0, "Pop", "<b> & </b>")),
        )

        val stub = assertNotNull(HtmlSpans.stubContent(doc, NodeIndex(0)))
        assertTrue(stub.contains("&lt;b&gt; &amp; &lt;/b&gt;"), stub)
        assertTrue(!stub.contains("&amp;lt;"), stub)
        assertTrue(!stub.contains("&amp;amp;"), stub)
    }

    @Test
    fun anExternalRefsStubContentNamesTheFileAndNodeItPointsAt() {
        val doc = document(listOf(entry(IndexEntry.TYPE_EXTERNAL_REF, "reflink.hyp/Main")))

        assertTrue(HtmlSpans.isStubTarget(doc, NodeIndex(0)))
        assertEquals(
            "External reference — not included in this document: reflink.hyp/Main",
            HtmlSpans.stubContent(doc, NodeIndex(0)),
        )
    }

    @Test
    fun anExternalRefWithoutAFileNameNamesOnlyTheNode() {
        val doc = document(listOf(entry(IndexEntry.TYPE_EXTERNAL_REF, "STool")))

        assertEquals(
            "External reference — not included in this document: STool",
            HtmlSpans.stubContent(doc, NodeIndex(0)),
        )
    }

    @Test
    fun anExternalRefsNameIsEscapedBeforeItReachesTheOutput() {
        // fileName/nodeName are raw `.hyp` bytes — an attacker-authored file naming an entry
        // `<script>/x` must not be able to inject markup into any HTML-shaped renderer's output.
        val doc = document(listOf(entry(IndexEntry.TYPE_EXTERNAL_REF, "<script>alert(1)</script>&/x\"y")))

        val stub = assertNotNull(HtmlSpans.stubContent(doc, NodeIndex(0)))
        assertTrue(!stub.contains("<script>"), stub)
        assertTrue(stub.contains("&lt;script&gt;alert(1)&lt;/script&gt;&amp;/x\"y"), stub)
    }

    @Test
    fun aSystemActionTargetIsDescribedRatherThanLinkedTo() {
        // It has no page either — the dead `node-<n>.xhtml` href it used to produce is what made
        // Calibre report a missing referenced file, exactly as an external ref did.
        val doc = document(listOf(entry(IndexEntry.TYPE_SYSTEM, "stool.Tos")))

        assertTrue(HtmlSpans.isStubTarget(doc, NodeIndex(0)))
        assertEquals(
            "Viewer action — not available in this document: stool.Tos",
            HtmlSpans.stubContent(doc, NodeIndex(0)),
        )
    }

    @Test
    fun aSystemActionsNameIsEscapedBeforeItReachesTheOutput() {
        val doc = document(listOf(entry(IndexEntry.TYPE_QUIT, "<img src=x onerror=alert(1)>&")))

        val stub = assertNotNull(HtmlSpans.stubContent(doc, NodeIndex(0)))
        assertTrue(!stub.contains("<img"), stub)
        assertTrue(stub.contains("&lt;img src=x onerror=alert(1)&gt;&amp;"), stub)
    }

    @Test
    fun vectorGraphicMarkupRendersInlineSvgForEveryNonImageGraphic() {
        val line = Graphic.Line(x = 0, y = 0, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val image = Graphic.Image(NodeIndex(1), x = 0, y = 0, width = 0, height = 0, ditherMask = null)

        val lineOnly = HtmlSpans.vectorGraphicMarkup(listOf(line))
        assertNotNull(lineOnly)
        assertTrue(lineOnly.contains("<svg"))
        assertTrue(lineOnly.contains("marker-start"))

        assertEquals(lineOnly, HtmlSpans.vectorGraphicMarkup(listOf(image, line)))
        assertNull(HtmlSpans.vectorGraphicMarkup(listOf(image)))
        assertNull(HtmlSpans.vectorGraphicMarkup(emptyList()))
    }
}
