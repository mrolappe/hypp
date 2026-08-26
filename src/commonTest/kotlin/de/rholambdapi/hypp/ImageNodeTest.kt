package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImageNodeTest {
    private fun open(bytes: ByteArray): HypDocument {
        val outcome = HypDocument.open(bytes)
        assertIs<OpenOutcome.Success>(outcome)
        return outcome.document
    }

    @Test
    fun decodesTheHeaderOfEachVendoredImage() {
        val cases = listOf(
            TestCorpus.image to Triple("rtr_logo.img", 216, 177),
            TestCorpus.limage to Triple("rtr_logo.img", 216, 177),
        )
        for ((bytes, expected) in cases) {
            val document = open(bytes)
            assertEquals(1, document.images.size)
            val image = document.images.single()
            val (name, width, height) = expected
            assertEquals(name, image.name)
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            assertEquals(1, image.planeCount)
            assertEquals(1, image.planePresent)
            assertEquals(width * height, image.pixels.size)
        }

        val limage2 = open(TestCorpus.limage2)
        assertEquals(2, limage2.images.size)
        assertEquals(listOf("select_box.img" to (66 to 32), "title.img" to (82 to 18)), limage2.images.map { it.name to (it.width to it.height) })
    }

    @Test
    fun decodesAPresentPlaneMsbFirstPerByteWithRowPaddingBeyondWidthIgnored() {
        // 9x1, 1 plane, present. rowBytes = ceil(9/16)*2 = 2. Byte0=0b10000000 (pixel 0 set),
        // byte1=0b10000000 (pixel 8 set; the other 7 bits of byte1 are row padding past width=9).
        val header = byteArrayOf(0, 9, 0, 1, 1, 1, 0, 0)
        val plane = byteArrayOf(0b10000000.toByte(), 0b10000000.toByte())
        val diagnostics = mutableListOf<Diagnostic>()
        val image = parseImage(NodeIndex(0), "synthetic", header + plane, diagnostics)!!
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0, 1), image.pixels.map { it.toInt() })
        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun aFilledPlaneExpandsToAllOnesWithoutBeingPresentInTheData() {
        // 4x1, 2 planes: plane 0 present (pixel pattern 1010), plane 1 filled but absent from the data.
        // rowBytes = ceil(4/16)*2 = 2, so the one present plane's row is 2 bytes (the second all padding).
        val header = byteArrayOf(0, 4, 0, 1, 2, 0b01, 0b10, 0)
        val plane0 = byteArrayOf(0b10100000.toByte(), 0)
        val image = parseImage(NodeIndex(0), "synthetic", header + plane0, mutableListOf())!!
        // Plane 1 (bit value 2) is on for every pixel; plane 0 contributes bit value 1 per its data.
        assertEquals(listOf(3, 2, 3, 2), image.pixels.map { it.toInt() })
    }

    @Test
    fun toRgbaResolvesEachPixelThroughThePalette() {
        // rowBytes = ceil(2/16)*2 = 2, so the one row is 2 bytes even though width is 2.
        val header = byteArrayOf(0, 2, 0, 1, 1, 1, 0, 0)
        val plane = byteArrayOf(0b10000000.toByte(), 0)
        val image = parseImage(NodeIndex(0), "synthetic", header + plane, mutableListOf())!!
        val rgba = image.toRgba()
        assertEquals(8, rgba.size)
        // pixel 0 -> index 1 -> HypColor.BLACK; pixel 1 -> index 0 -> HypColor.WHITE
        assertEquals(listOf(0, 0, 0, -1), rgba.copyOfRange(0, 4).map { it.toInt() })
        assertEquals(listOf(-1, -1, -1, -1), rgba.copyOfRange(4, 8).map { it.toInt() })
    }

    @Test
    fun fourPlanePensAreBlockConcatenatedLowPlaneFirstAndResolveThroughTheGemPenMapping() {
        // 4x1, 4 planes, all present. rowBytes = ceil(4/16)*2 = 2, so each plane is 2 bytes and the
        // four planes follow one another whole (not word-interleaved), plane 0 carrying bit value 1.
        // Target pens, left to right: 0 (0000), 4 (0100), 10 (1010), 15 (1111).
        val header = byteArrayOf(0, 4, 0, 1, 4, 0b1111, 0, 0)
        val planes = byteArrayOf(
            0b0001_0000, 0, // plane 0 (bit value 1): pixel 3 only
            0b0011_0000, 0, // plane 1 (bit value 2): pixels 2, 3
            0b0101_0000, 0, // plane 2 (bit value 4): pixels 1, 3
            0b0011_0000, 0, // plane 3 (bit value 8): pixels 2, 3
        )
        val image = parseImage(NodeIndex(0), "synthetic", header + planes, mutableListOf())!!
        assertEquals(listOf(0, 4, 10, 15), image.pixels.map { it.toInt() })
        // Those pens are Atari ST hardware palette registers, not VDI colour indices: 0/4 map to
        // themselves, but 10 is dark green (not VDI 10, dark red) and 15 is black (not dark magenta).
        val expected = listOf(HypColor.WHITE, HypColor.BLUE, HypColor.DARK_GREEN, HypColor.BLACK)
        assertEquals(expected, (0 until 4).map { Palette.forPlaneCount(4).colorAt(image.pixels[it].toInt()) })
        assertEquals(
            expected.flatMap { listOf(it.red, it.green, it.blue, 255) },
            image.toRgba().map { it.toInt() and 0xFF },
        )
    }
}
