package de.rholambdapi.hypp

/**
 * A character set a `.HYP` document's text bytes are encoded in. v1 ships the
 * three named in the extended-header `@charset` values seen in the wild:
 * Atari ST (the format's own default), Latin-1 and UTF-8.
 */
sealed interface HypCharset {
    fun decode(bytes: ByteArray): String

    /**
     * The Atari ST character set: ASCII in 0x00-0x7F, a fixed table of
     * accented Latin, Hebrew and Greek letters and symbols in 0x80-0xFF.
     * Table sourced from the public Atari ST character set reference
     * (Wikipedia, "Atari ST character set"), independent of any hypview
     * source — this repo's charset tables are clean-room per the project's
     * spec-sources policy.
     */
    data object AtariSt : HypCharset {
        // Index 0 == byte 0x80. One entry per byte value 0x80-0xFF.
        private val high =
            "ÇüéâäàåçêëèïîìÄÅ" +
            "ÉæÆôöòûùÿÖÜ¢£¥ßƒ" +
            "áíóúñÑªº¿⌐¬½¼¡«»" +
            "ãõØøœŒÀÃÕ¨´†¶©®™" +
            "ĳĲאבגדהוזחטיכלמנ" +
            "סעפצקרשתןךםףץ§∧∞" +
            "αβΓπΣσµτΦΘΩδ∮ϕ∈∩" +
            "≡±≥≤⌠⌡÷≈°•·√ⁿ²³¯"

        override fun decode(bytes: ByteArray): String = buildString(bytes.size) {
            for (byte in bytes) {
                val b = byte.toInt() and 0xFF
                append(if (b < 0x80) b.toChar() else high[b - 0x80])
            }
        }
    }

    /** ISO-8859-1: every byte value is its own Unicode code point. */
    data object Latin1 : HypCharset {
        override fun decode(bytes: ByteArray): String = buildString(bytes.size) {
            for (byte in bytes) append((byte.toInt() and 0xFF).toChar())
        }
    }

    data object Utf8 : HypCharset {
        override fun decode(bytes: ByteArray): String = bytes.decodeToString()
    }

    companion object {
        val Default: HypCharset = AtariSt

        /**
         * Resolves a `@charset` name as written into extended header id 30 —
         * a UDO/`iconv`-style descriptor string, matched case-insensitively.
         * Aliases per the current UDO manual's charset descriptor table
         * (man.udo-open-source.org, "Converting 8-bit characters").
         * Returns null for a name outside v1's supported set.
         */
        fun byName(name: String): HypCharset? = when (name.trim().uppercase()) {
            "ATARI", "ATARIST", "TOS" -> AtariSt
            "ISO-8859-1", "ISO-IR-100", "ISO8859-1", "ISO_8859-1", "LATIN1", "L1", "CSISOLATIN1" -> Latin1
            "UTF-8", "UTF8" -> Utf8
            else -> null
        }
    }
}
