package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VectorGraphicTest {
    @Test
    fun lineCarriesArrowFlagsAndSignedRunLengths() {
        val line = Graphic.Line(x = 5, y = 0, width = -10, height = 3, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)

        val vector = line.toVectorGraphic()

        assertEquals(-10, vector.dx)
        assertEquals(2, vector.dy) // height is a 1-based row count (like Box); dy is the endpoint delta within it
        assertEquals(true, vector.arrowAtStart)
        assertEquals(false, vector.arrowAtEnd)
    }

    @Test
    fun singleRowLineIsPerfectlyFlat() {
        // height=1 means "1 row tall", same as a Box's height=1 — the slope endpoint must be
        // dy=0, not dy=1, or a purely horizontal separator renders as a visible diagonal.
        val line = Graphic.Line(x = 0, y = 0, width = 40, height = 1, arrowAtStart = false, arrowAtEnd = false, lineStyle = 0)
        assertEquals(0, line.toVectorGraphic().dy)
    }

    @Test
    fun lineStyleZeroAndOneAreBothSolid() {
        assertNull(Graphic.Line(0, 0, 1, 0, false, false, lineStyle = 0).toVectorGraphic().dash)
        assertNull(Graphic.Line(0, 0, 1, 0, false, false, lineStyle = 1).toVectorGraphic().dash)
    }

    @Test
    fun everyNamedLineStyleMapsToADistinctDashPattern() {
        val dashes = (2..7).map { style -> Graphic.Line(0, 0, 1, 0, false, false, lineStyle = style).toVectorGraphic().dash }
        assertEquals(6, dashes.map { requireNotNull(it) }.toSet().size, "styles 2-7 must be visually distinct: $dashes")
    }

    @Test
    fun boxHasNoCornerRadiusAndClampsFillPatternIntoThe0To8Range() {
        val vector = Graphic.Box(x = 0, y = 0, width = 10, height = 5, fillPattern = 2).toVectorGraphic()
        assertEquals(10, vector.widthCells)
        assertEquals(5, vector.heightCells)
        assertEquals(0.0, vector.cornerRadiusCells)
        assertEquals(2, vector.fillLevel)

        assertEquals(0, Graphic.Box(0, 0, 1, 1, fillPattern = -3).toVectorGraphic().fillLevel)
        assertEquals(8, Graphic.Box(0, 0, 1, 1, fillPattern = 99).toVectorGraphic().fillLevel)
    }

    @Test
    fun roundedBoxHasAPositiveCornerRadius() {
        val vector = Graphic.RoundedBox(x = 0, y = 0, width = 10, height = 5, fillPattern = 1).toVectorGraphic()
        assertEquals(10, vector.widthCells)
        assertEquals(1, vector.fillLevel)
        assertTrue(vector.cornerRadiusCells > 0.0)
    }

    @Test
    fun shortRoundedBoxCornerRadiusIsClampedToHalfHeightNotAFixedConstant() {
        // A fixed 1.0 radius exceeds height/2 on short boxes, so SVG clamps `ry` to a stadium shape.
        val height1 = Graphic.RoundedBox(x = 0, y = 0, width = 10, height = 1, fillPattern = 0).toVectorGraphic()
        assertTrue(height1.cornerRadiusCells <= 1 / 2.0, "radius ${height1.cornerRadiusCells} exceeds height/2 for height=1")

        val height2 = Graphic.RoundedBox(x = 0, y = 0, width = 10, height = 2, fillPattern = 0).toVectorGraphic()
        assertTrue(height2.cornerRadiusCells <= 2 / 2.0, "radius ${height2.cornerRadiusCells} exceeds height/2 for height=2")
    }
}
