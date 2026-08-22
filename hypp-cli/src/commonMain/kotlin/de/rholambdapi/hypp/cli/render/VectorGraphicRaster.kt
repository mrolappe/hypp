package de.rholambdapi.hypp.cli.render

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pixels per character cell for [VectorGraphicRaster] — arbitrary but simple; the format gives no font metric to derive one from. */
data class CellMetrics(val width: Int = 8, val height: Int = 16)

/** A row-major RGBA pixel buffer, starting fully transparent. */
class RgbaBitmap(val width: Int, val height: Int) {
    val pixels = ByteArray(width * height * 4)

    fun setBlack(x: Int, y: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val i = (y * width + x) * 4
        pixels[i + 3] = 0xFF.toByte() // r/g/b stay 0 (black); alpha opaque
    }
}

/**
 * Rasterizes a [VectorGraphic] to plain black-on-transparent pixels, reusing [StoredPngEncoder]'s
 * `encodeRgba` for PNG bytes — the same terminus [de.rholambdapi.hypp.ImageNode] encoding goes
 * through, so a future consumer that needs real pixels (rather than [VectorGraphicSvg]'s inline
 * SVG, the path the current HTML/EPUB renderers use) doesn't duplicate PNG-encoding logic.
 *
 * ponytail: no anti-aliasing, corner rounding and line thickness are single-radius/single-width
 * approximations — good enough for a small decorative shape, not a general 2D graphics engine.
 * Upgrade to a proper scanline/AA rasterizer if a consumer needs higher fidelity.
 */
object VectorGraphicRaster {
    fun VectorGraphic.rasterize(cellPx: CellMetrics = CellMetrics()): RgbaBitmap = when (this) {
        is VectorGraphic.Box -> rasterizeBox(this, cellPx)
        is VectorGraphic.Line -> rasterizeLine(this, cellPx)
    }

    fun encodePng(bitmap: RgbaBitmap): ByteArray = StoredPngEncoder.encodeRgba(bitmap.width, bitmap.height, bitmap.pixels)

    private const val BORDER_PX = 1

    private fun rasterizeBox(box: VectorGraphic.Box, cellPx: CellMetrics): RgbaBitmap {
        val w = (box.widthCells * cellPx.width).coerceAtLeast(1)
        val h = (box.heightCells * cellPx.height).coerceAtLeast(1)
        val bitmap = RgbaBitmap(w, h)
        val r = box.cornerRadiusCells * cellPx.width
        for (y in 0 until h) for (x in 0 until w) {
            if (!insideRoundedRect(x, y, w, h, r)) continue
            val onBorder = x < BORDER_PX || y < BORDER_PX || x >= w - BORDER_PX || y >= h - BORDER_PX
            if (onBorder || fillPatternHit(x, y, box.fillLevel)) bitmap.setBlack(x, y)
        }
        return bitmap
    }

    private fun insideRoundedRect(x: Int, y: Int, w: Int, h: Int, r: Double): Boolean {
        if (r <= 0.0) return true
        val px = x + 0.5
        val py = y + 0.5
        val cx = when {
            px < r -> r
            px > w - r -> w - r
            else -> return true
        }
        val cy = when {
            py < r -> r
            py > h - r -> h - r
            else -> return true
        }
        val dx = px - cx
        val dy = py - cy
        return dx * dx + dy * dy <= r * r
    }

    private fun fillPatternHit(x: Int, y: Int, level: Int): Boolean {
        if (level <= 0) return false
        val threshold = fillLevelThreshold(level)
        return BAYER_4X4[(y % 4) * 4 + (x % 4)] < threshold
    }

    private fun rasterizeLine(line: VectorGraphic.Line, cellPx: CellMetrics): RgbaBitmap {
        val w = (abs(line.dx) * cellPx.width).coerceAtLeast(1)
        val h = (max(line.dy, 0) * cellPx.height).coerceAtLeast(1)
        val bitmap = RgbaBitmap(w, h)
        val x1 = 0.0
        val y1 = if (line.dx >= 0) 0.0 else h.toDouble()
        val x2 = w.toDouble()
        val y2 = if (line.dx >= 0) h.toDouble() else 0.0

        val length = max(abs(x2 - x1), abs(y2 - y1))
        val steps = length.toInt().coerceAtLeast(1)
        val dashPx = line.dash?.map { it * cellPx.width / 4.0 }
        var traveled = 0.0
        var dashIndex = 0
        var dashRemaining = dashPx?.getOrNull(0) ?: Double.MAX_VALUE

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val x = (x1 + (x2 - x1) * t)
            val y = (y1 + (y2 - y1) * t)
            val drawing = dashPx == null || (dashIndex % 2 == 0)
            if (drawing) plotThick(bitmap, x, y)

            if (dashPx != null) {
                traveled += length / steps
                dashRemaining -= length / steps
                while (dashRemaining <= 0) {
                    dashIndex++
                    dashRemaining += dashPx[dashIndex % dashPx.size]
                }
            }
        }
        if (line.arrowAtStart) plotArrow(bitmap, x1, y1, x2 - x1, y2 - y1, reverse = true)
        if (line.arrowAtEnd) plotArrow(bitmap, x2, y2, x2 - x1, y2 - y1, reverse = false)
        return bitmap
    }

    private fun plotThick(bitmap: RgbaBitmap, x: Double, y: Double) {
        val cx = x.toInt()
        val cy = y.toInt()
        for (ox in -1..1) for (oy in -1..1) bitmap.setBlack(cx + ox, cy + oy)
    }

    /** A small filled triangle pointing along the line's direction, at one end. */
    private fun plotArrow(bitmap: RgbaBitmap, atX: Double, atY: Double, dirX: Double, dirY: Double, reverse: Boolean) {
        val len = max(kotlin.math.hypot(dirX, dirY), 1e-6)
        val sign = if (reverse) 1.0 else -1.0
        val ux = sign * dirX / len
        val uy = sign * dirY / len
        val size = min(bitmap.width, bitmap.height).coerceAtLeast(4) / 4.0
        val perpX = -uy
        val perpY = ux
        val tipX = atX
        val tipY = atY
        val baseX = atX + ux * size
        val baseY = atY + uy * size
        for (t in 0..10) {
            val f = t / 10.0
            val bx = baseX + perpX * size * 0.5 * f
            val by = baseY + perpY * size * 0.5 * f
            val bx2 = baseX - perpX * size * 0.5 * f
            val by2 = baseY - perpY * size * 0.5 * f
            plotThick(bitmap, tipX + (bx - tipX) * f, tipY + (by - tipY) * f)
            plotThick(bitmap, tipX + (bx2 - tipX) * f, tipY + (by2 - tipY) * f)
        }
    }
}
