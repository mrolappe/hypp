package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.ImageNode
import de.rholambdapi.hypp.NodeIndex
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [StoredPngEncoder] writes a real, spec-valid PNG using only RFC-1951 "stored" (uncompressed)
 * deflate blocks (decision 10, `doc/PLAN-12-19.md`). `javax.imageio.ImageIO` is the cheapest
 * available independent oracle to prove the bytes actually decode as PNG — a real reader either
 * accepts the file or it doesn't.
 */
class StoredPngEncoderTest {
    /** A 3x2 checkerboard-ish bitmap, single bitplane, MSB-first per word-aligned row. */
    private fun syntheticImage(): ImageNode {
        val rowBytes = 2 // ceil(3/16)*2
        val planeData = byteArrayOf(
            0b10100000.toByte(), 0x00, // row0: pixels 1,0,1
            0b01000000.toByte(), 0x00, // row1: pixels 0,1,0
        )
        return ImageNode(
            index = NodeIndex(0), name = "synthetic", width = 3, height = 2,
            planeCount = 1, planePresent = 1, planeFilled = 0, planeData = planeData,
        )
    }

    @Test
    fun startsWithThePngSignature() {
        val png = StoredPngEncoder.encode(syntheticImage())
        val signature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals(signature.toList(), png.copyOfRange(0, 8).toList())
    }

    @Test
    fun roundTripsPixelsThroughImageIo() {
        val image = syntheticImage()
        val expectedRgba = image.toRgba()

        val png = StoredPngEncoder.encode(image)
        val decoded = ImageIO.read(ByteArrayInputStream(png))
        assertNotNull(decoded, "ImageIO must recognise the encoded bytes as a valid PNG")
        assertEquals(image.width, decoded.width)
        assertEquals(image.height, decoded.height)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = decoded.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val i = (y * image.width + x) * 4
                assertEquals(expectedRgba[i].toInt() and 0xFF, r, "red mismatch at ($x,$y)")
                assertEquals(expectedRgba[i + 1].toInt() and 0xFF, g, "green mismatch at ($x,$y)")
                assertEquals(expectedRgba[i + 2].toInt() and 0xFF, b, "blue mismatch at ($x,$y)")
                assertEquals(expectedRgba[i + 3].toInt() and 0xFF, a, "alpha mismatch at ($x,$y)")
            }
        }
    }

    @Test
    fun roundTripsAOneByOnePixelImage() {
        val image = ImageNode(
            index = NodeIndex(1), name = "dot", width = 1, height = 1,
            planeCount = 1, planePresent = 1, planeFilled = 0,
            planeData = byteArrayOf(0b10000000.toByte(), 0x00),
        )
        val png = StoredPngEncoder.encode(image)
        val decoded = ImageIO.read(ByteArrayInputStream(png))
        assertNotNull(decoded)
        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
    }
}
