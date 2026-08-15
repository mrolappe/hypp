package de.rholambdapi.hypp.internal

/** Sequential big-endian reader over a HYP file's bytes. */
internal class ByteReader(private val bytes: ByteArray, private var pos: Int = 0) {
    val position: Int get() = pos

    fun readU8(): Int = bytes[pos++].toInt() and 0xFF

    fun readU16(): Int {
        val hi = readU8()
        val lo = readU8()
        return (hi shl 8) or lo
    }

    fun readU32(): Int {
        val hi = readU16()
        val lo = readU16()
        return (hi shl 16) or lo
    }

    fun readBytes(count: Int): ByteArray {
        val result = bytes.copyOfRange(pos, pos + count)
        pos += count
        return result
    }
}

/** Decodes raw index-entry name bytes: Latin-1 mapping, truncated at the first NUL. */
internal fun ByteArray.decodeName(): String {
    val end = indexOf(0).let { if (it == -1) size else it }
    return buildString(end) {
        for (i in 0 until end) append((this@decodeName[i].toInt() and 0xFF).toChar())
    }
}
