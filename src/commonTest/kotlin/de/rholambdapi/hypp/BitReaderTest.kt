package de.rholambdapi.hypp

import de.rholambdapi.hypp.internal.BitReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lh5 bit stream is MSB-first within each byte, crosses byte boundaries freely,
 * and must yield zero bits once the compressed region is exhausted (the encoder pads
 * the final byte, and the decoder routinely peeks ahead of the symbol it is decoding).
 *
 * Expected values below are hand-derived from the literal bit patterns in the comments.
 */
class BitReaderTest {
    // 0xB4 = 1011 0100, 0x2D = 0010 1101
    private val sample = byteArrayOf(0xB4.toByte(), 0x2D)

    @Test
    fun readsMsbFirstAcrossByteBoundaries() {
        val bits = BitReader(sample)
        assertEquals(1, bits.read(1), "first bit of 1011 0100")
        assertEquals(0b011, bits.read(3))
        assertEquals(0b0100, bits.read(4))
        assertEquals(0x2D, bits.read(8), "the whole second byte")
        assertEquals(16, bits.bitPosition)
    }

    @Test
    fun readSpanningTheByteBoundary() {
        val bits = BitReader(sample)
        bits.skip(6)
        // remaining: 00 | 0010 1101
        assertEquals(0b000010, bits.read(6))
        assertEquals(0b1101, bits.read(4))
    }

    @Test
    fun peekDoesNotAdvance() {
        val bits = BitReader(sample)
        assertEquals(0xB4, bits.peek(8))
        assertEquals(0xB4, bits.peek(8))
        assertEquals(0, bits.bitPosition)
        assertEquals(0xB42D, bits.peek(16))
    }

    @Test
    fun readingPastTheEndYieldsZeroBits() {
        val bits = BitReader(sample)
        bits.skip(12)
        // 4 real bits (1101) then zero fill
        assertEquals(0b1101_0000, bits.read(8))
        assertTrue(bits.overrun, "reading beyond the compressed region must be detectable")
        assertEquals(0, bits.read(16))
    }

    @Test
    fun honoursASubRange() {
        // Only the second byte is in range; bit positions are relative to it.
        val bits = BitReader(sample, fromIndex = 1, toIndex = 2)
        assertEquals(0x2D, bits.read(8))
        assertTrue(!bits.overrun)
        assertEquals(0, bits.read(1))
        assertTrue(bits.overrun)
    }

    @Test
    fun readOfZeroBitsIsANoOp() {
        val bits = BitReader(sample)
        assertEquals(0, bits.read(0))
        assertEquals(0, bits.bitPosition)
    }
}
