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
        assertEquals(3, vector.dy)
        assertEquals(true, vector.arrowAtStart)
        assertEquals(false, vector.arrowAtEnd)
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
}
