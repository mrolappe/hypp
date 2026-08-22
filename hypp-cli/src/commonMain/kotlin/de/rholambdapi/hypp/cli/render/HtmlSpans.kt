package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle
import de.rholambdapi.hypp.cli.render.VectorGraphicSvg.toSvg

/**
 * A `.hyp` node's lines are a fixed-width character-cell grid — indentation and multi-column
 * layout are literal runs of space characters, not markup — so every HTML-shaped renderer's
 * `<body>` needs to stop collapsing whitespace (the browser/e-reader default) and use a
 * fixed-width font to keep the grid's columns aligned.
 */
internal const val HTML_BODY_STYLE = "white-space:pre-wrap;font-family:monospace"

/**
 * Ported from `hyp2html`'s span renderer in `hypp`'s own `commonTest` (`Hyp2Html.kt`) — the exact
 * same rules, shared by every renderer that needs HTML-shaped span markup.
 */
object HtmlSpans {
    /**
     * [linkHref] defaults to a same-page fragment (`#<index>`), correct for [HtmlRenderer]'s
     * single-page output where every node has a matching `id`. [EpubRenderer] overrides it to a
     * cross-file href, since each node there is its own XHTML document.
     */
    fun renderSpan(span: Span, linkHref: (NodeIndex) -> String = { "#${it.value}" }): String = buildString {
        val link = span.link
        val text = escapeHtml(span.text)
        if (link != null) {
            append("<a href=\"").append(linkHref(link.target)).append("\">").append(text).append("</a>")
            return@buildString
        }
        val style = span.style
        var open = text
        if (style.isBold) open = "<b>$open</b>"
        if (style.isItalic) open = "<i>$open</i>"
        if (style.isUnderlined) open = "<u>$open</u>"
        val css = buildString {
            if (style.foreground != TextStyle.Normal.foreground) {
                append("color:rgb(${style.foreground.red},${style.foreground.green},${style.foreground.blue});")
            }
            if (style.background != TextStyle.Normal.background) {
                append("background-color:rgb(${style.background.red},${style.background.green},${style.background.blue});")
            }
        }
        append(if (css.isEmpty()) open else "<span style=\"$css\">$open</span>")
    }

    /**
     * XML 1.0 forbids raw C0 control characters in element content (outside tab/LF/CR) — an
     * unescaped one breaks well-formedness for [EpubRenderer]'s strict XHTML, and a strict reader
     * (e.g. Apple Books/Preview) responds by rendering only up to that point, which looks like
     * truncated content. Bytes in this range that survive [de.rholambdapi.hypp.HypCharset]
     * decoding are the source `.hyp` file's own icon-style glyphs (e.g. an arrow-key icon) that
     * v1's clean-room charset table has no confirmed mapping for (see `HypCharset`'s own scope
     * note) — rather than guess at one, or drop the character and lose information, this maps it
     * to Unicode's own public "Control Pictures" block (U+2400 + code), which is always safe XML
     * content and still marks that something was there.
     */
    fun escapeHtml(text: String): String = buildString(text.length) {
        for (char in text) when {
            char == '&' -> append("&amp;")
            char == '<' -> append("&lt;")
            char == '>' -> append("&gt;")
            char.isXmlUnsafeControlChar() -> append(Char(CONTROL_PICTURES_BASE + char.code))
            else -> append(char)
        }
    }

    private fun Char.isXmlUnsafeControlChar(): Boolean = code < 0x20 && this != '\t' && this != '\n' && this != '\r'

    private const val CONTROL_PICTURES_BASE = 0x2400

    /**
     * Buckets [graphics] by the text row (0-based, matching a [de.rholambdapi.hypp.Node]'s
     * `lines` index) each is positioned at, so a caller can interleave a row's graphics into its
     * output right before that row's line. A `y` past the last row (or negative, on malformed
     * input) clamps to the nearest end rather than being silently dropped.
     */
    fun graphicsByRow(graphics: List<Graphic>, lineCount: Int): Map<Int, List<Graphic>> =
        graphics.groupBy { it.y.coerceIn(0, lineCount) }

    /**
     * Renders every [Graphic.Line]/[Graphic.Box]/[Graphic.RoundedBox] in [graphics] as inline SVG
     * (see [VectorGraphicSvg] for the bit-to-visual mapping, sourced from the HCP compiler's own
     * command reference). Returns null when [graphics] holds only [Graphic.Image]s, which render
     * themselves. Callers must also emit [VectorGraphicSvg.sharedDefs] once per document/file.
     */
    fun vectorGraphicMarkup(graphics: List<Graphic>): String? {
        val svg = buildString {
            for (graphic in graphics) when (graphic) {
                is Graphic.Line -> append(graphic.toVectorGraphic().toSvg())
                is Graphic.Box -> append(graphic.toVectorGraphic().toSvg())
                is Graphic.RoundedBox -> append(graphic.toVectorGraphic().toSvg())
                is Graphic.Image -> {}
            }
        }
        return svg.ifEmpty { null }
    }
}
