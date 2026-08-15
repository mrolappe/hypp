package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HypCharsetTest {
    @Test
    fun atariStDecodesAccentedLatinLetters() {
        // "Übung" has no capital-U-diaeresis in the ST high range under this
        // byte, so spell out individually-verified bytes instead: ä ö ü ß Ä Ö Ü
        val bytes = byteArrayOf(0x84.toByte(), 0x94.toByte(), 0x81.toByte(), 0x9E.toByte(), 0x8E.toByte(), 0x99.toByte(), 0x9A.toByte())
        assertEquals("äöüßÄÖÜ", HypCharset.AtariSt.decode(bytes))
    }

    @Test
    fun atariStAsciiRangePassesThrough() {
        val bytes = "Grosse Bahnhofstrasse 12".encodeToByteArray()
        assertEquals("Grosse Bahnhofstrasse 12", HypCharset.AtariSt.decode(bytes))
    }

    @Test
    fun sameByteDecodesDifferentlyUnderAtariStAndLatin1() {
        // 0xE4 is Sigma under Atari ST but 'ä' under Latin-1 — the whole
        // reason charset selection has to happen before text is readable.
        val byte = byteArrayOf(0xE4.toByte())
        assertEquals("Σ", HypCharset.AtariSt.decode(byte))
        assertEquals("ä", HypCharset.Latin1.decode(byte))
    }

    @Test
    fun utf8DecodesMultiByteSequences() {
        val bytes = "café".encodeToByteArray() // UTF-8: 'é' = 0xC3 0xA9
        assertEquals("café", HypCharset.Utf8.decode(bytes))
    }

    @Test
    fun byNameResolvesKnownAliasesCaseInsensitively() {
        assertEquals(HypCharset.AtariSt, HypCharset.byName("atarist"))
        assertEquals(HypCharset.AtariSt, HypCharset.byName("ATARIST"))
        assertEquals(HypCharset.AtariSt, HypCharset.byName("tos"))
        assertEquals(HypCharset.Latin1, HypCharset.byName("latin1"))
        assertEquals(HypCharset.Latin1, HypCharset.byName("ISO-8859-1"))
        assertEquals(HypCharset.Utf8, HypCharset.byName("utf8"))
        assertEquals(HypCharset.Utf8, HypCharset.byName("UTF-8"))
    }

    @Test
    fun byNameReturnsNullForUnsupportedCharset() {
        assertNull(HypCharset.byName("koi8-r"))
        assertNull(HypCharset.byName("bogus"))
    }
}
