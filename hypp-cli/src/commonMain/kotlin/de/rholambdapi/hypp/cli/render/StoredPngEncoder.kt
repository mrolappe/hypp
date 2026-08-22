package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.ImageNode

/**
 * A real, spec-valid PNG encoder using only RFC-1951 "stored" (uncompressed) deflate blocks — no
 * LZ77/Huffman, no platform zip/deflate library, so it works in `commonMain` on every target
 * (decision 10, `doc/PLAN-12-19.md`). CRC-32 and Adler-32 are hand-rolled for the same reason.
 */
object StoredPngEncoder : ImageEncoder {
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
        0x0D, 0x0A, 0x1A, 0x0A,
    )

    override fun encode(image: ImageNode): ByteArray = encodeRgba(image.width, image.height, image.toRgba())

    /** Encodes a raw RGBA pixel buffer (row-major, 4 bytes/pixel) as PNG bytes — the reusable seam for any caller that isn't an [ImageNode]. */
    fun encodeRgba(width: Int, height: Int, rgba: ByteArray): ByteArray {
        val raw = rawRows(width, height, rgba)
        val idatData = zlibStream(raw)
        return PNG_SIGNATURE +
            chunk("IHDR", ihdrData(width, height)) +
            chunk("IDAT", idatData) +
            chunk("IEND", ByteArray(0))
    }

    /** Filter-type byte (0x00, "None") followed by each row's RGBA bytes, per the PNG spec. */
    private fun rawRows(width: Int, height: Int, rgba: ByteArray): ByteArray {
        val rowBytes = width * 4
        val raw = ByteArray(height * (1 + rowBytes))
        for (y in 0 until height) {
            val dst = y * (1 + rowBytes)
            raw[dst] = 0
            rgba.copyInto(raw, dst + 1, y * rowBytes, y * rowBytes + rowBytes)
        }
        return raw
    }

    private fun ihdrData(width: Int, height: Int): ByteArray {
        val data = ByteArray(13)
        writeU32BE(data, 0, width)
        writeU32BE(data, 4, height)
        data[8] = 8 // bit depth
        data[9] = 6 // colour type: truecolor + alpha
        data[10] = 0 // compression method
        data[11] = 0 // filter method
        data[12] = 0 // interlace method
        return data
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = ByteArray(4) { type[it].code.toByte() }
        val crcInput = typeBytes + data
        val out = ByteArray(4 + crcInput.size + 4)
        writeU32BE(out, 0, data.size)
        crcInput.copyInto(out, 4)
        writeU32BE(out, 4 + crcInput.size, crc32(crcInput))
        return out
    }

    /** zlib stream: 2-byte header, deflate payload packed as stored blocks, big-endian Adler-32. */
    private fun zlibStream(raw: ByteArray): ByteArray {
        val deflate = storedBlocks(raw)
        val out = ByteArray(2 + deflate.size + 4)
        out[0] = 0x78
        out[1] = 0x01
        deflate.copyInto(out, 2)
        writeU32BE(out, 2 + deflate.size, adler32(raw))
        return out
    }

    /**
     * RFC-1951 stored blocks: each is byte-aligned before and after, so its 3-bit header
     * (BFINAL + BTYPE=00), padded to a byte, is just 0x01 (final) or 0x00 (more follow), then
     * LEN (u16 LE), NLEN (LEN's one's complement, u16 LE), then LEN literal bytes. An empty input
     * still needs one (zero-length) block.
     */
    private fun storedBlocks(data: ByteArray): ByteArray {
        val maxLen = 65535
        val chunkCount = if (data.isEmpty()) 1 else (data.size + maxLen - 1) / maxLen
        var totalSize = 0
        for (i in 0 until chunkCount) totalSize += 5 + blockLength(data, i, maxLen)

        val out = ByteArray(totalSize)
        var pos = 0
        for (i in 0 until chunkCount) {
            val start = i * maxLen
            val len = blockLength(data, i, maxLen)
            val isFinal = i == chunkCount - 1
            out[pos] = if (isFinal) 0x01 else 0x00
            val nlen = len.inv() and 0xFFFF
            out[pos + 1] = (len and 0xFF).toByte()
            out[pos + 2] = ((len ushr 8) and 0xFF).toByte()
            out[pos + 3] = (nlen and 0xFF).toByte()
            out[pos + 4] = ((nlen ushr 8) and 0xFF).toByte()
            data.copyInto(out, pos + 5, start, start + len)
            pos += 5 + len
        }
        return out
    }

    private fun blockLength(data: ByteArray, chunkIndex: Int, maxLen: Int): Int =
        minOf(maxLen, data.size - chunkIndex * maxLen)

    private fun writeU32BE(out: ByteArray, at: Int, value: Int) {
        out[at] = ((value ushr 24) and 0xFF).toByte()
        out[at + 1] = ((value ushr 16) and 0xFF).toByte()
        out[at + 2] = ((value ushr 8) and 0xFF).toByte()
        out[at + 3] = (value and 0xFF).toByte()
    }

    private val CRC32_POLYNOMIAL = 0xEDB88320.toInt()

    private val CRC32_TABLE = IntArray(256).also { table ->
        for (n in 0 until 256) {
            var c = n
            repeat(8) { c = if (c and 1 != 0) (CRC32_POLYNOMIAL xor (c ushr 1)) else (c ushr 1) }
            table[n] = c
        }
    }

    /** Standard CRC-32 (IEEE 802.3): polynomial 0xEDB88320 reflected, init/final XOR 0xFFFFFFFF. */
    private fun crc32(data: ByteArray): Int {
        var crc = -1 // 0xFFFFFFFF
        for (byte in data) {
            val index = (crc xor byte.toInt()) and 0xFF
            crc = (crc ushr 8) xor CRC32_TABLE[index]
        }
        return crc.inv()
    }

    /** Standard Adler-32: MOD=65521, a starts at 1, b at 0. */
    private fun adler32(data: ByteArray): Int {
        val mod = 65521
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % mod
            b = (b + a) % mod
        }
        return (b shl 16) or a
    }
}
