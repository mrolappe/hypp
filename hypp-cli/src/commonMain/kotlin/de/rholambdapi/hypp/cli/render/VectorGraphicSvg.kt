package de.rholambdapi.hypp.cli.render

import kotlin.math.abs

/**
 * Renders a [VectorGraphic] as inline SVG, sized in `ch`/`em` so it scales exactly with the
 * monospace character grid ([HTML_BODY_STYLE]) — no character-cell-to-pixel constant needed.
 * `mix-blend-mode:multiply` reproduces the format's documented "OR mode" compositing (HCP's "box"
 * command reference: objects are translucent and never obscure what's underneath) — ink only ever
 * gets added, never erased, so a filled box drawn over text doesn't blot it out.
 */
object VectorGraphicSvg {
    fun VectorGraphic.toSvg(): String = when (this) {
        is VectorGraphic.Line -> toSvg()
        is VectorGraphic.Box -> toSvg()
    }

    /**
     * A hidden container for the 9 fill-density patterns + 2 arrowhead markers, referenced by
     * `url(#...)` from every graphic's own inline `<svg>` elsewhere in the same document — emit
     * this once per HTML/XHTML file that contains any non-image graphic.
     */
    fun sharedDefs(): String = buildString {
        append("""<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>""")
        for (level in 0..8) append(fillPatternDef(level))
        append(ARROW_START_MARKER)
        append(ARROW_END_MARKER)
        append("</defs></svg>")
    }

    fun VectorGraphic.Box.toSvg(): String {
        val w = widthCells.coerceAtLeast(1)
        val h = heightCells.coerceAtLeast(1)
        val strokeWidth = 0.08
        val pad = strokeWidth / 2
        return buildString {
            // viewBox is padded by strokeWidth on top of the box's nominal w x h footprint (the
            // <svg> width/height stay w x h) so the outer half of the centered stroke isn't
            // clipped by SVG's default viewBox clipping.
            append("<svg viewBox=\"0 0 ${w + strokeWidth} ${h + strokeWidth}\" width=\"${w}ch\" height=\"${h}em\" style=\"mix-blend-mode:multiply\">")
            append("<rect x=\"$pad\" y=\"$pad\" width=\"$w\" height=\"$h\" rx=\"$cornerRadiusCells\" ry=\"$cornerRadiusCells\"")
            append(" fill=\"url(#fill-$fillLevel)\" stroke=\"black\" stroke-width=\"$strokeWidth\"/>")
            append("</svg>")
        }
    }

    fun VectorGraphic.Line.toSvg(): String {
        val absDx = abs(dx)
        val w = absDx.coerceAtLeast(1) // clamped only for the viewBox/width so dx=0 isn't a degenerate zero-width viewBox
        val h = dy.coerceAtLeast(0)
        val viewH = h.coerceAtLeast(1)
        val x1 = 0
        val y1 = if (dx >= 0) 0 else h
        val x2 = absDx // unclamped: dx=0 must keep x2==x1 so the line stays vertical, not a 1-unit diagonal
        val y2 = if (dx >= 0) h else 0
        return buildString {
            append("<svg viewBox=\"0 0 $w $viewH\" width=\"${w}ch\" height=\"${viewH}em\" style=\"mix-blend-mode:multiply\">")
            append("<line x1=\"$x1\" y1=\"$y1\" x2=\"$x2\" y2=\"$y2\" stroke=\"black\" stroke-width=\"0.08\"")
            if (dash != null) append(" stroke-dasharray=\"${dash.joinToString(",")}\"")
            if (arrowAtStart) append(" marker-start=\"url(#arrow-start)\"")
            if (arrowAtEnd) append(" marker-end=\"url(#arrow-end)\"")
            append("/></svg>")
        }
    }

    /**
     * 9-level hollow-to-solid density gradient (this session's by-eye confirmation of the HCP
     * "Füllmuster" demo page), realized as an ordered (Bayer 4x4) dither over an 8-cell-per-tile
     * grid so density steps look evenly distributed rather than filling row-by-row.
     */
    private fun fillPatternDef(level: Int): String {
        val threshold = fillLevelThreshold(level)
        val cellSize = 0.5 / 4
        return buildString {
            append("<pattern id=\"fill-$level\" width=\"0.5\" height=\"0.5\" patternUnits=\"userSpaceOnUse\">")
            for (i in BAYER_4X4.indices) {
                if (BAYER_4X4[i] >= threshold) continue
                val cx = (i % 4) * cellSize
                val cy = (i / 4) * cellSize
                append("<rect x=\"$cx\" y=\"$cy\" width=\"$cellSize\" height=\"$cellSize\" fill=\"black\"/>")
            }
            append("</pattern>")
        }
    }

    // markerUnits="userSpaceOnUse" pins the marker to absolute viewBox units instead of the SVG
    // default (markerUnits="strokeWidth"), which scaled these to an imperceptible 0.32 units
    // against the 0.08 line stroke-width regardless of line length.
    private const val ARROW_START_MARKER =
        """<marker id="arrow-start" viewBox="0 0 10 10" refX="1" refY="5" markerWidth="0.8" markerHeight="0.8" markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 Z" fill="black"/></marker>"""

    private const val ARROW_END_MARKER =
        """<marker id="arrow-end" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="0.8" markerHeight="0.8" markerUnits="userSpaceOnUse" orient="auto"><path d="M0,0 L10,5 L0,10 Z" fill="black"/></marker>"""
}
