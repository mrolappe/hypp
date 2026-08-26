package de.rholambdapi.hypp

/** A palette an [ImageNode]'s pixel indices are resolved against. */
class Palette(private val colors: List<HypColor>) {
    /** The colour at [index], or black if the image carries more colours than this palette has. */
    fun colorAt(index: Int): HypColor = colors.getOrElse(index) { HypColor.BLACK }

    companion object {
        /** The format's fixed 16-entry palette in [HypColor]'s own (VDI colour index) order. */
        val AtariSt = Palette(HypColor.entries)

        /**
         * The palette a [planeCount]-plane image's pixel values resolve against. A bitplane pixel
         * value is an Atari ST *hardware palette register* ("pen"), which is not the GEM VDI colour
         * index [HypColor]'s ordinal is — GEM permutes the two, differently per plane count. See
         * `doc/format-notes.md` for the `st-guide_orig_en.hyp` image-77 evidence behind the
         * 4-plane row; the 1-plane identity is confirmed by `image.hyp`, the 2-plane row is the
         * standard GEM table with no corpus case, and 3/5..8 planes fall back to the identity.
         */
        fun forPlaneCount(planeCount: Int): Palette = when (planeCount) {
            2 -> penPalette(0, 2, 3, 1)
            4 -> penPalette(0, 2, 3, 6, 4, 7, 5, 8, 9, 10, 11, 14, 12, 15, 13, 1)
            else -> AtariSt
        }

        private fun penPalette(vararg vdiIndices: Int) = Palette(vdiIndices.map(HypColor.entries::get))
    }
}
