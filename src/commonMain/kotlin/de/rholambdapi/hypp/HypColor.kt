package de.rholambdapi.hypp

/**
 * A colour of the format's fixed 16-entry palette, as selected by the `0xa5`/`0xa6` foreground
 * and background escapes. The wire value is a raw palette index, which is exactly this enum's
 * ordinal — see `doc/format-notes.md` for how that encoding was established.
 */
enum class HypColor {
    WHITE, BLACK, RED, GREEN, BLUE, CYAN, YELLOW, MAGENTA,
    LIGHT_GRAY, DARK_GRAY, DARK_RED, DARK_GREEN, DARK_BLUE, DARK_CYAN, DARK_YELLOW, DARK_MAGENTA;

    companion object {
        /** The palette colour at [index], or null if the file names an index outside the palette. */
        fun byIndex(index: Int): HypColor? = entries.getOrNull(index)
    }
}
