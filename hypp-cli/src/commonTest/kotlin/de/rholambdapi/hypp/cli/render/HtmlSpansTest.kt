package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.HypColor
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
    fun linkSpanWithCustomHref() {
        val link = Link(kind = LinkKind.LINK, target = NodeIndex(5), lineNumber = null, label = "go")
        val html = HtmlSpans.renderSpan(Span("go", TextStyle.Normal, link)) { target -> "node-${target.value}.xhtml" }
        assertEquals("<a href=\"node-5.xhtml\">go</a>", html)
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

    @Test
    fun renderGridCentersAGraphicWhenXIsZero() {
        val graphic = Graphic.Line(x = 0, y = 1, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val n = node(List(2) { Line(emptyList()) }, listOf(graphic))

        val html = HtmlSpans.renderGrid(n) { null }

        assertTrue(
            html.contains("<div style=\"position:absolute;z-index:1;top:1em;left:50%;transform:translateX(-50%)\">"),
            html,
        )
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
