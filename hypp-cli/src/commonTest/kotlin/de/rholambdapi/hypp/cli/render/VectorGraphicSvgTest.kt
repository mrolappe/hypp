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
}
