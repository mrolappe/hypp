package de.rholambdapi.hypp

/** A palette an [ImageNode]'s pixel indices are resolved against. */
class Palette(private val colors: List<HypColor>) {
    /** The colour at [index], or black if the image carries more colours than this palette has. */
    fun colorAt(index: Int): HypColor = colors.getOrElse(index) { HypColor.BLACK }

    companion object {
        /** The format's fixed 16-entry palette — see [HypColor]. */
        val AtariSt = Palette(HypColor.entries)
    }
}
