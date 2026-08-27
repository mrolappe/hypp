package de.rholambdapi.hypp

/**
 * One graphic object placed on a node's page (prologue region a). `x`/`y`/`width`/`height`
 * are in character cells, as the format's own prose spec states — confirmed empirically
 * against `image.hyp`/`limage.hyp`/`limage2.hyp`/`lines.hyp`.
 *
 * There is deliberately no `centered` here: `x == 0` means centred for *images only*
 * (`@image`/`@limage`), whose `x` the format defines as 0-255. A `@line`/`@box`/`@rbox`
 * carries `x` in 1-255, so "x is zero" is not a placement mode for them — it is either
 * absent or malformed data. See [Image.centered] and `doc/format-notes.md`.
 */
sealed interface Graphic {
    val x: Int
    val y: Int
    val width: Int
    val height: Int

    /**
     * `imageIndex` is the placed image's index into [HypDocument.entries]. [ditherMask] is the
     * raw payload of a `0x2f` data block (prologue item c, "immediately precedes its image
     * command" per the prose spec) when one preceded this image escape — not corpus-evidenced
     * (no vendored file uses it), see `doc/format-notes.md`.
     *
     * [width] is not a width: for an image it is the flag that separates the format's two image
     * commands (0 = `@image`, 1 = `@limage`, see [isLineImage]), and [height] is always 0.
     */
    class Image(
        val imageIndex: NodeIndex,
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
        val ditherMask: ByteArray?,
    ) : Graphic {
        /**
         * The format's only centring signal, and it exists only for images: both the prose spec
         * ("X == 0 for centered images") and `hyp.h`'s `x_offset` comment ("0 == centered", for
         * `@image`/`@limage` alone) state it, and neither offers a second, more explicit flag.
         */
        val centered: Boolean get() = x == 0

        /**
         * True for `@limage`, the format's *line* image — "images incorporated in this way will
         * be treated by ST-Guide as lines (limage == line image), meaning that text cannot be
         * placed to either the left or the right of them and it isn't necessary to insert blank
         * lines below the image, as ST-Guide will automatically move the following text down"
         * (HCP command reference). A plain `@image` ([isLineImage] false) is the opposite: an
         * overlay drawn on top of the character grid, for which the author leaves blank rows.
         */
        val isLineImage: Boolean get() = width == 1

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

    /**
     * `arrowAtStart`/`arrowAtEnd` and `lineStyle` are the data byte's bit 0, bit 1 and remaining bits.
     * Unlike every other [Graphic], [width] is *signed* (-127..126): it is the `@line` command's
     * x-length, negative meaning the line runs bottom-left to top-right. See `doc/format-notes.md`.
     */
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
