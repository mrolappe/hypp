package de.rholambdapi.hypp

/**
 * A colour of the format's fixed 16-entry palette, as selected by the `0xa5`/`0xa6` foreground
 * and background escapes. The wire value is a raw palette index, which is exactly this enum's
 * ordinal — see `doc/format-notes.md` for how that encoding was established.
 *
 * [red]/[green]/[blue] (0..255) follow the standard Atari ST/GEM default palette convention —
 * full-intensity primaries, half-intensity "dark" variants — not corpus-verified (the fixture
 * that confirmed the index-to-name mapping has no rendering oracle for the actual RGB values);
 * see `doc/format-notes.md`.
 */
enum class HypColor(val red: Int, val green: Int, val blue: Int) {
    WHITE(255, 255, 255), BLACK(0, 0, 0), RED(255, 0, 0), GREEN(0, 255, 0),
    BLUE(0, 0, 255), CYAN(0, 255, 255), YELLOW(255, 255, 0), MAGENTA(255, 0, 255),
    LIGHT_GRAY(192, 192, 192), DARK_GRAY(128, 128, 128), DARK_RED(128, 0, 0), DARK_GREEN(0, 128, 0),
    DARK_BLUE(0, 0, 128), DARK_CYAN(0, 128, 128), DARK_YELLOW(128, 128, 0), DARK_MAGENTA(128, 0, 128);

    companion object {
        /** The palette colour at [index], or null if the file names an index outside the palette. */
        fun byIndex(index: Int): HypColor? = entries.getOrNull(index)
    }
}
