package de.rholambdapi.hypp

/**
 * One graphic object placed on a node's page (prologue region a). `x`/`y`/`width`/`height`
 * are in character cells, as the format's own prose spec states — confirmed empirically
 * against `image.hyp`/`limage.hyp`/`limage2.hyp`/`lines.hyp`. `x == 0` means centred.
 */
sealed interface Graphic {
    val x: Int
    val y: Int
    val width: Int
    val height: Int
    val centered: Boolean get() = x == 0

    /**
     * `imageIndex` is the placed image's index into [HypDocument.entries]. `width`/`height`
     * are present on the wire but ignored by the format for images (real files carry 0 for
     * both). [ditherMask] is the raw payload of a `0x2f` data block (prologue item c,
     * "immediately precedes its image command" per the prose spec) when one preceded this
     * image escape — not corpus-evidenced (no vendored file uses it), see `doc/format-notes.md`.
     */
    class Image(
        val imageIndex: NodeIndex,
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
        val ditherMask: ByteArray?,
    ) : Graphic {
        override fun equals(other: Any?): Boolean =
            other is Image && imageIndex == other.imageIndex && x == other.x && y == other.y &&
                width == other.width && height == other.height && ditherMask.contentEquals(other.ditherMask)

        override fun hashCode(): Int {
            var result = imageIndex.hashCode()
            result = 31 * result + x
            result = 31 * result + y
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + (ditherMask?.contentHashCode() ?: 0)
            return result
        }
    }

    /** `arrowAtStart`/`arrowAtEnd` and `lineStyle` are the data byte's bit 0, bit 1 and remaining bits. */
    data class Line(
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
        val arrowAtStart: Boolean,
        val arrowAtEnd: Boolean,
        val lineStyle: Int,
    ) : Graphic

    data class Box(
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
        val fillPattern: Int,
    ) : Graphic

    data class RoundedBox(
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
        val fillPattern: Int,
    ) : Graphic
}
