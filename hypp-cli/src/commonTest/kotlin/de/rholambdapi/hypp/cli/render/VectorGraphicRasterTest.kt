package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.cli.render.VectorGraphicRaster.rasterize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorGraphicRasterTest {
    private val cellPx = CellMetrics(width = 8, height = 16)

    private fun RgbaBitmap.isOpaque(x: Int, y: Int): Boolean = pixels[(y * width + x) * 4 + 3] != 0.toByte()

    @Test
    fun boxBitmapIsSizedByCellsTimesCellMetrics() {
        val box = VectorGraphic.Box(widthCells = 3, heightCells = 2, cornerRadiusCells = 0.0, fillLevel = 0)
        val bitmap = box.rasterize(cellPx)
        assertEquals(24, bitmap.width)
        assertEquals(32, bitmap.height)
    }

    @Test
    fun hollowBoxHasAnOutlineButATransparentCenter() {
        val box = VectorGraphic.Box(widthCells = 4, heightCells = 4, cornerRadiusCells = 0.0, fillLevel = 0)
        val bitmap = box.rasterize(cellPx)
        assertTrue(bitmap.isOpaque(0, 0), "top-left border pixel should be black")
        assertFalse(bitmap.isOpaque(bitmap.width / 2, bitmap.height / 2), "center of a hollow box should stay transparent")
    }

    @Test
    fun solidBoxFillsEveryInteriorPixel() {
        val box = VectorGraphic.Box(widthCells = 4, heightCells = 4, cornerRadiusCells = 0.0, fillLevel = 8)
        val bitmap = box.rasterize(cellPx)
        assertTrue(bitmap.isOpaque(bitmap.width / 2, bitmap.height / 2), "fill level 8 should be fully solid")
    }

    @Test
    fun roundedBoxCornerPixelStaysOutsideTheRoundedCorner() {
        val plain = VectorGraphic.Box(widthCells = 4, heightCells = 4, cornerRadiusCells = 0.0, fillLevel = 8).rasterize(cellPx)
        val rounded = VectorGraphic.Box(widthCells = 4, heightCells = 4, cornerRadiusCells = 1.0, fillLevel = 8).rasterize(cellPx)
        assertTrue(plain.isOpaque(0, 0), "plain box corner is filled")
        assertFalse(rounded.isOpaque(0, 0), "rounded box corner should be cut off")
    }

    @Test
    fun horizontalLineBitmapIsOnePixelCellTallAndSpansItsWidth() {
        val line = VectorGraphic.Line(dx = 5, dy = 0, arrowAtStart = false, arrowAtEnd = false, dash = null)
        val bitmap = line.rasterize(cellPx)
        assertEquals(40, bitmap.width)
        assertTrue(bitmap.isOpaque(bitmap.width / 2, 0))
    }

    @Test
    fun rasterizedBoxEncodesToAValidPngSignature() {
        val box = VectorGraphic.Box(widthCells = 2, heightCells = 2, cornerRadiusCells = 0.0, fillLevel = 5)
        val png = VectorGraphicRaster.encodePng(box.rasterize(cellPx))
        val signature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertEquals(signature.toList(), png.take(4))
    }
}
