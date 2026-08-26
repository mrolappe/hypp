package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.cli.render.VectorGraphicSvg.toSvg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorGraphicSvgTest {
    @Test
    fun sharedDefsContainsAllNineFillLevelsAndBothArrowMarkers() {
        val defs = VectorGraphicSvg.sharedDefs()
        for (level in 0..8) assertTrue(defs.contains("id=\"fill-$level\""), "missing fill-$level: $defs")
        assertTrue(defs.contains("id=\"arrow-start\""))
        assertTrue(defs.contains("id=\"arrow-end\""))
    }

    @Test
    fun arrowMarkersUseAbsoluteUserSpaceSizingSoTheyDontShrinkWithStrokeWidth() {
        val defs = VectorGraphicSvg.sharedDefs()
        assertEquals(2, Regex("markerUnits=\"userSpaceOnUse\"").findAll(defs).count(), defs)
    }

    @Test
    fun boxSvgSizedInCharacterCellUnitsAndReferencesItsFillLevel() {
        val svg = VectorGraphic.Box(widthCells = 10, heightCells = 5, cornerRadiusCells = 0.0, fillLevel = 3).toSvg()
        assertTrue(svg.contains("width=\"10ch\""), svg)
        assertTrue(svg.contains("height=\"5em\""), svg)
        assertTrue(svg.contains("fill=\"url(#fill-3)\""), svg)
        assertTrue(svg.contains("mix-blend-mode:multiply"), svg)
    }

    @Test
    fun roundedBoxHasANonZeroCornerRadiusAttribute() {
        val svg = VectorGraphic.Box(widthCells = 10, heightCells = 5, cornerRadiusCells = 1.0, fillLevel = 0).toSvg()
        assertTrue(svg.contains("rx=\"1.0\""), svg)
    }

    @Test
    fun boxStrokeIsFullyContainedWithinTheViewBox() {
        val svg = VectorGraphic.Box(widthCells = 10, heightCells = 5, cornerRadiusCells = 0.0, fillLevel = 0).toSvg()
        val (vbMinX, vbMinY, vbW, vbH) = Regex("""viewBox="([^"]+)"""").find(svg)!!.groupValues[1]
            .trim().split(Regex("\\s+")).map { it.toDouble() }
        val rectTag = Regex("""<rect[^>]*/>""").find(svg)!!.value
        fun attr(name: String) = Regex("""\s$name="(-?[\d.]+)"""").find(rectTag)!!.groupValues[1].toDouble()
        val rectX = attr("x")
        val rectY = attr("y")
        val rectW = attr("width")
        val rectH = attr("height")
        val half = attr("stroke-width") / 2
        assertTrue(rectX - half >= vbMinX - 1e-9, "left edge of stroke clipped: $svg")
        assertTrue(rectY - half >= vbMinY - 1e-9, "top edge of stroke clipped: $svg")
        assertTrue(rectX + rectW + half <= vbMinX + vbW + 1e-9, "right edge of stroke clipped: $svg")
        assertTrue(rectY + rectH + half <= vbMinY + vbH + 1e-9, "bottom edge of stroke clipped: $svg")
    }

    @Test
    fun lineWithNoDashIsSolid() {
        val svg = VectorGraphic.Line(dx = 10, dy = 0, arrowAtStart = false, arrowAtEnd = false, dash = null).toSvg()
        assertTrue(svg.contains("<line"), svg)
        assertTrue(!svg.contains("stroke-dasharray"), svg)
    }

    @Test
    fun dashedLineCarriesADasharrayAttribute() {
        val svg = VectorGraphic.Line(dx = 10, dy = 0, arrowAtStart = false, arrowAtEnd = false, dash = listOf(1, 2)).toSvg()
        assertTrue(svg.contains("stroke-dasharray=\"1,2\""), svg)
    }

    @Test
    fun lineWithBothArrowsReferencesBothMarkers() {
        val svg = VectorGraphic.Line(dx = 10, dy = 0, arrowAtStart = true, arrowAtEnd = true, dash = null).toSvg()
        assertTrue(svg.contains("marker-start=\"url(#arrow-start)\""), svg)
        assertTrue(svg.contains("marker-end=\"url(#arrow-end)\""), svg)
    }

    @Test
    fun negativeDxDrawsFromBottomLeftToTopRight() {
        val svg = VectorGraphic.Line(dx = -10, dy = 4, arrowAtStart = false, arrowAtEnd = false, dash = null).toSvg()
        assertTrue(svg.contains("y1=\"4\""), svg)
        assertTrue(svg.contains("y2=\"0\""), svg)
    }

    @Test
    fun zeroDxLineIsTrulyVerticalNotDiagonal() {
        val svg = VectorGraphic.Line(dx = 0, dy = 5, arrowAtStart = false, arrowAtEnd = false, dash = null).toSvg()
        val x1 = Regex("""\sx1="(-?[\d.]+)"""").find(svg)!!.groupValues[1]
        val x2 = Regex("""\sx2="(-?[\d.]+)"""").find(svg)!!.groupValues[1]
        assertEquals(x1, x2, svg)
    }
}
