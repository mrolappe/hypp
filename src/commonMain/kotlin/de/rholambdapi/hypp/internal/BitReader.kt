package de.rholambdapi.hypp.internal

/**
 * MSB-first bit reader over `bytes[fromIndex until toIndex]`.
 *
 * Reads beyond the region yield zero bits rather than failing: an lh5 encoder pads the
 * final byte, and the decoder legitimately peeks past the last symbol it needs. The
 * [overrun] flag records that it happened, so a caller can tell a padded tail apart
 * from a stream that ran off the end because it was being decoded wrongly.
 */
internal class BitReader(
    private val bytes: ByteArray,
    private val fromIndex: Int = 0,
    private val toIndex: Int = bytes.size,
) {
    private var bitPos = 0

    /** Bits consumed so far, relative to `fromIndex`. */
    val bitPosition: Int get() = bitPos

    /** True once any bit beyond `toIndex` has been read. */
    var overrun: Boolean = false
        private set

    private fun bitAt(offset: Int): Int {
        val absolute = bitPos + offset
        val index = fromIndex + (absolute ushr 3)
        if (index >= toIndex) return 0
        return (bytes[index].toInt() ushr (7 - (absolute and 7))) and 1
    }

    /** The next [count] bits, without consuming them. */
    fun peek(count: Int): Int {
        var value = 0
        for (i in 0 until count) value = (value shl 1) or bitAt(i)
        return value
    }

    fun skip(count: Int) {
        bitPos += count
        if (fromIndex + ((bitPos + 7) ushr 3) > toIndex) overrun = true
    }

    fun read(count: Int): Int {
        val value = peek(count)
        skip(count)
        return value
    }

    fun readBit(): Int {
        val value = bitAt(0)
        skip(1)
        return value
    }
}
