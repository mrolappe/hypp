package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.HypColor
import de.rholambdapi.hypp.Link
import de.rholambdapi.hypp.LinkKind
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun graphicsByRowBucketsByYAndClampsOutOfRangeRows() {
        val onRow2 = Graphic.Line(x = 0, y = 2, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val pastTheEnd = Graphic.Box(x = 0, y = 99, width = 10, height = 1, fillPattern = 0)
        val negative = Graphic.RoundedBox(x = 0, y = -5, width = 10, height = 1, fillPattern = 0)

        val byRow = HtmlSpans.graphicsByRow(listOf(onRow2, pastTheEnd, negative), lineCount = 5)

        assertEquals(listOf(onRow2), byRow[2])
        assertEquals(listOf(pastTheEnd), byRow[5])
        assertEquals(listOf(negative), byRow[0])
    }

    @Test
    fun nonImageGraphicMarkupIsARuleWhenAnyNonImageGraphicIsPresent() {
        val line = Graphic.Line(x = 0, y = 0, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val image = Graphic.Image(NodeIndex(1), x = 0, y = 0, width = 0, height = 0, ditherMask = null)

        assertEquals("<hr/>", HtmlSpans.nonImageGraphicMarkup(listOf(line)))
        assertEquals("<hr/>", HtmlSpans.nonImageGraphicMarkup(listOf(image, line)))
        assertNull(HtmlSpans.nonImageGraphicMarkup(listOf(image)))
        assertNull(HtmlSpans.nonImageGraphicMarkup(emptyList()))
    }
}
