package de.rholambdapi.hypp

/**
 * A type-3 image object's decoded data-region header (`hypfmt.ui`'s "Format of an image
 * object"): `width:u16, height:u16, planeCount:u8, planePresent:u8, planeFilled:u8, filler:u8`,
 * followed by one bitplane per bit set in [planePresent], each stored as `ceil(width/16)*2`
 * bytes per row (word-aligned, MSB-first pixel per bit), planes concatenated in ascending plane
 * order — not word-interleaved. Confirmed against `image.hyp`/`limage.hyp`/`limage2.hyp`: their
 * declared plane data length equals `ceil(width/16)*2*height` exactly in all four images. Every
 * vendored image is single-plane, so the multi-plane concatenation order and the "present vs.
 * filled vs. neither" combination below are implemented literally from the prose spec, not
 * corpus-confirmed — see `doc/format-notes.md`.
 */
class ImageNode(
    val index: NodeIndex,
    val name: String,
    val width: Int,
    val height: Int,
    val planeCount: Int,
    val planePresent: Int,
    val planeFilled: Int,
    private val planeData: ByteArray,
) {
    /** One palette index per pixel, row-major, plane 0 contributing the low bit. Lazy + memoised. */
    val pixels: ByteArray by lazy { decodePixels() }

    fun toRgba(palette: Palette = Palette.AtariSt): ByteArray {
        val out = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val color = palette.colorAt(pixels[i].toInt() and 0xFF)
            out[i * 4] = color.red.toByte()
            out[i * 4 + 1] = color.green.toByte()
            out[i * 4 + 2] = color.blue.toByte()
            out[i * 4 + 3] = 0xFF.toByte()
        }
        return out
    }

    private fun decodePixels(): ByteArray {
        val rowBytes = (width + 15) / 16 * 2
        val planeSize = rowBytes * height
        val out = ByteArray(width * height)
        var offset = 0
        for (plane in 0 until planeCount) {
            val present = (planePresent shr plane) and 1 == 1
            val filled = (planeFilled shr plane) and 1 == 1
            if (!present && !filled) continue
            for (y in 0 until height) {
                for (byteIndex in 0 until rowBytes) {
                    val byte = if (present) planeData[offset + y * rowBytes + byteIndex].toInt() and 0xFF else 0xFF
                    for (bit in 0 until 8) {
                        val x = byteIndex * 8 + bit
                        if (x >= width) break
                        if ((byte shr (7 - bit)) and 1 == 1) {
                            val pixel = y * width + x
                            out[pixel] = (out[pixel].toInt() or (1 shl plane)).toByte()
                        }
                    }
                }
            }
            if (present) offset += planeSize
        }
        return out
    }
}

/** Parses a decompressed type-3 object's data into an [ImageNode], or null if it's too short to hold the header. */
internal fun parseImage(index: NodeIndex, name: String, data: ByteArray, diagnostics: MutableList<Diagnostic>): ImageNode? {
    if (data.size < 8) {
        diagnostics += Diagnostic.NodeDataOverrun(index)
        return null
    }
    fun u8(at: Int) = data[at].toInt() and 0xFF
    fun u16(at: Int) = (u8(at) shl 8) or u8(at + 1)
    val width = u16(0)
    val height = u16(2)
    val planeCount = u8(4)
    val planePresent = u8(5)
    val planeFilled = u8(6)
    return ImageNode(
        index = index,
        name = name,
        width = width,
        height = height,
        planeCount = planeCount,
        planePresent = planePresent,
        planeFilled = planeFilled,
        planeData = data.copyOfRange(8, data.size),
    )
}
