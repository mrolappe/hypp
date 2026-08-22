package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic

/**
 * A [Graphic.Line]/[Graphic.Box]/[Graphic.RoundedBox]'s raw decoded bits turned into
 * rendering-ready values. The mapping is sourced from the HCP compiler's own command reference
 * (`hcp_orig_de.hyp`'s "line"/"box"/"rbox"/"Füllmuster" nodes, viewed via the public `hypview.cgi`
 * mirror — first-party spec documentation, not hypview's implementation):
 * - line style 1-7 are named GEM/VDI polyline styles (solid, long dash, dots, dash-dot, dash,
 *   dash-dot-dot, dotted); 0 (unset) renders the same as 1 (solid).
 * - fill pattern 0-8 is a monotonic hollow-to-solid density gradient (confirmed by eye against the
 *   "Füllmuster" demo page), not a distinct hatch shape per level.
 */
sealed interface VectorGraphic {
    data class Line(
        val dx: Int,
        val dy: Int,
        val arrowAtStart: Boolean,
        val arrowAtEnd: Boolean,
        val dash: List<Int>?,
    ) : VectorGraphic

    data class Box(
        val widthCells: Int,
        val heightCells: Int,
        val cornerRadiusCells: Double,
        val fillLevel: Int,
    ) : VectorGraphic
}

/** Fixed corner radius for [Graphic.RoundedBox] — the format gives no radius parameter to derive one from. */
private const val ROUNDED_BOX_CORNER_RADIUS_CELLS = 1.0

private val LINE_STYLE_DASH: Map<Int, List<Int>?> = mapOf(
    0 to null, // unset - renders the same as solid
    1 to null, // solid
    2 to listOf(6, 3), // long dash
    3 to listOf(1, 2), // dots
    4 to listOf(4, 2, 1, 2), // dash-dot
    5 to listOf(3, 2), // dash
    6 to listOf(4, 2, 1, 2, 1, 2), // dash-dot-dot
    7 to listOf(1, 1), // dotted
)

fun Graphic.Line.toVectorGraphic(): VectorGraphic.Line =
    VectorGraphic.Line(dx = width, dy = height, arrowAtStart = arrowAtStart, arrowAtEnd = arrowAtEnd, dash = LINE_STYLE_DASH[lineStyle])

fun Graphic.Box.toVectorGraphic(): VectorGraphic.Box =
    VectorGraphic.Box(widthCells = width, heightCells = height, cornerRadiusCells = 0.0, fillLevel = fillPattern.coerceIn(0, 8))

fun Graphic.RoundedBox.toVectorGraphic(): VectorGraphic.Box =
    VectorGraphic.Box(widthCells = width, heightCells = height, cornerRadiusCells = ROUNDED_BOX_CORNER_RADIUS_CELLS, fillLevel = fillPattern.coerceIn(0, 8))

/** Ordered (Bayer 4x4) dither matrix shared by [VectorGraphicSvg] and [VectorGraphicRaster] so both fill-density gradients match. */
internal val BAYER_4X4 = intArrayOf(
    0, 8, 2, 10,
    12, 4, 14, 6,
    3, 11, 1, 9,
    15, 7, 13, 5,
)

/** A fill level 0..8 as a threshold against [BAYER_4X4]'s 0..15 range: a cell is filled when its Bayer value is below this. */
internal fun fillLevelThreshold(level: Int): Int = level * 16 / 8
