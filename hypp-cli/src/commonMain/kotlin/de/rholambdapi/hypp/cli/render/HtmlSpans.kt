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
     * The node's own text-column width in character cells: the longest line it actually has. The
     * `.hyp` format has no stored page width, so this is the only grid width a renderer can honor,
     * and it must be read from the [Node] being rendered — after `--reflow` joined its paragraphs
     * the same node reports the wider, joined-line width, which is exactly what the caller wants.
     */
    fun textColumnWidth(node: Node): Int =
        node.lines.maxOfOrNull { line -> line.spans.sumOf { it.text.length } } ?: 0

    /**
     * The `style` attribute (leading space included, empty when there is nothing to cap) that keeps
     * an `<img>` inside [node]'s text column. An image's stored size is in pixels and says nothing
     * about how many character cells it may occupy — st-guide's 528px banner is wider than the
     * whole 64-cell page it sits on — so cap it at [textColumnWidth]. `height:auto` is needed
     * because the `height` attribute is a presentational hint that would otherwise hold the
     * original pixel height while the width shrinks, distorting the image.
     */
    fun imageSizeStyle(node: Node): String {
        val columnWidth = textColumnWidth(node)
        return if (columnWidth == 0) "" else " style=\"width:auto;height:auto;max-width:${columnWidth}ch\""
    }

    /**
     * Renders [node]'s lines and graphics, honoring the format's two very different image
     * placements (see [Graphic.Image.isLineImage]):
     *
     * - A plain `@image`, and every vector graphic, is an **overlay** drawn on top of the
     *   character grid, on rows the author left blank for it. Those render as today: the lines go
     *   into a single `<p style="margin:0">` (row order preserved by a literal `\n` per line —
     *   [HTML_BODY_STYLE]'s `white-space:pre-wrap` keeps that meaningful) inside a
     *   `position:relative;line-height:1` container, and each graphic becomes an absolutely
     *   positioned sibling `<div>` at `top:<row>em;left:<column>ch`. `line-height:1` plus
     *   `top:<row>em` is what keeps a graphic on its real text row; a multi-row graphic needs no
     *   special handling since [vectorGraphicMarkup] already sizes its markup `<height>em` tall.
     * - An `@limage` is a **line** image: "text cannot be placed to either the left or the right
     *   of them and it isn't necessary to insert blank lines below the image, as ST-Guide will
     *   automatically move the following text down" (HCP command reference). So it is emitted as a
     *   block between two containers, splitting the node's lines at its row — the browser then
     *   pushes the following text down for us. Splitting into per-segment containers is also what
     *   keeps the *overlays* correct: each one is positioned `top:<row - segment start>em` inside
     *   its own segment, so a line image displacing the rows below it displaces its overlays too,
     *   with no font-metric arithmetic anywhere.
     *
     * `x == 0` on an image means centred, and centred means within the node's own text column
     * ([textColumnWidth]) — not within the viewport, which has nothing to do with the grid.
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
        val columnWidth = textColumnWidth(node)
        fun row(graphic: Graphic) = graphic.y.coerceIn(0, node.lines.size)

        val lineImagesByRow = node.graphics.filterIsInstance<Graphic.Image>()
            .filter { it.isLineImage }
            .groupBy(::row)
        val overlays = node.graphics.filter { it !is Graphic.Image || !it.isLineImage }

        var start = 0
        for (breakRow in lineImagesByRow.keys.sorted()) {
            appendSegment(node, start, breakRow, isLastSegment = false, overlays, columnWidth, linkHref, imageTag)
            for (image in lineImagesByRow.getValue(breakRow)) {
                val markup = imageTag(image) ?: continue
                val placement =
                    if (image.centered) "width:${columnWidth}ch;text-align:center" else "margin-left:${image.x}ch"
                append("<div style=\"$placement\">").append(markup).append("</div>")
            }
            start = breakRow
        }
        appendSegment(node, start, node.lines.size, isLastSegment = true, overlays, columnWidth, linkHref, imageTag)
    }

    /**
     * Renders rows `[start, end)` and the overlays sitting on them. [isLastSegment] — which is not
     * the same thing as `end == node.lines.size`, since a line image clamped to the last row makes
     * an earlier segment end there too — is what claims the out-of-range overlays, so a graphic
     * whose `y` is past the final row is drawn once rather than once per segment ending there.
     */
    private fun StringBuilder.appendSegment(
        node: Node,
        start: Int,
        end: Int,
        isLastSegment: Boolean,
        overlays: List<Graphic>,
        columnWidth: Int,
        linkHref: (NodeIndex) -> String,
        imageTag: (Graphic.Image) -> String?,
    ) {
        append("<div style=\"position:relative;line-height:1\"><p style=\"margin:0\">")
        for (index in start until end) {
            if (index > start) append("\n")
            for (span in node.lines[index].spans) append(renderSpan(span, linkHref))
        }
        append("</p>")
        for (graphic in overlays) {
            val row = graphic.y.coerceIn(0, node.lines.size)
            if (row < start || (row >= end && !isLastSegment)) continue
            val markup = if (graphic is Graphic.Image) imageTag(graphic) else vectorGraphicMarkup(listOf(graphic))
            if (markup == null) continue
            val horizontal = if (graphic is Graphic.Image && graphic.centered) {
                "left:calc(${columnWidth}ch / 2);transform:translateX(-50%)"
            } else {
                "left:${graphic.x}ch"
            }
            append("<div style=\"position:absolute;z-index:1;top:${row - start}em;$horizontal\">")
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
