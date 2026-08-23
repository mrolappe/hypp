package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.Node
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
     * Renders [node]'s lines and graphics as one positioned container: all lines as a single
     * `<p style="margin:0">` (row order preserved by a literal `\n` per line — [HTML_BODY_STYLE]'s
     * `white-space:pre-wrap` keeps that meaningful), every [Graphic] as an absolutely positioned
     * sibling `<div>` at `top:<row>em;left:<column>ch`. `line-height:1` on the container plus
     * `top:<row>em` on each graphic is what keeps a graphic aligned with its real text row — a
     * multi-row graphic (`height > 1`) needs no special handling here since its own markup (from
     * [vectorGraphicMarkup]) is already sized `<height>em` tall by [VectorGraphicSvg]. `x == 0`
     * ([Graphic.centered]) renders as `left:50%;transform:translateX(-50%)`, honoring the
     * documented model semantic instead of leaving it unread.
     *
     * [imageTag] renders one [Graphic.Image] as a complete `<img .../>` string, or null if the
     * referenced image can't be resolved — [HtmlRenderer] and [EpubRenderer] each address an image
     * differently (data URI vs. relative href) and own that decision; this function only positions
     * whatever [imageTag] returns.
     */
    fun renderGrid(
        node: Node,
        linkHref: (NodeIndex) -> String = { "#${it.value}" },
        imageTag: (Graphic.Image) -> String?,
    ): String = buildString {
        append("<div style=\"position:relative;line-height:1\"><p style=\"margin:0\">")
        node.lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")
            for (span in line.spans) append(renderSpan(span, linkHref))
        }
        append("</p>")
        for (graphic in node.graphics) {
            val markup = if (graphic is Graphic.Image) imageTag(graphic) else vectorGraphicMarkup(listOf(graphic))
            if (markup == null) continue
            val top = graphic.y.coerceIn(0, node.lines.size)
            val horizontal = if (graphic.centered) "left:50%;transform:translateX(-50%)" else "left:${graphic.x}ch"
            append("<div style=\"position:absolute;z-index:1;top:${top}em;$horizontal\">")
            append(markup)
            append("</div>")
        }
        append("</div>")
    }

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
