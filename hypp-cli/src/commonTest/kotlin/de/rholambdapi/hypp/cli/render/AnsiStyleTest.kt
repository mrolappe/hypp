package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypColor
import de.rholambdapi.hypp.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

private val ESC = 27.toChar()

class AnsiStyleTest {
    private val csi = "$ESC["

    // TextStyle's public contract (KDoc on TextStyle.kt): bits 0-5 attribute vector, bits 8-11
    // foreground colour index, bits 12-15 background colour index.
    private fun style(attrBits: Int = 0, fg: HypColor = HypColor.BLACK, bg: HypColor = HypColor.WHITE): TextStyle =
        TextStyle(attrBits or (fg.ordinal shl 8) or (bg.ordinal shl 12))

    private val bold = 1
    private val italic = 4
    private val underlined = 8

    @Test
    fun normalStyleHasNoCodes() {
        assertEquals("${csi}m", AnsiStyle.sgrFor(TextStyle.Normal))
    }

    @Test
    fun boldOnly() {
        assertEquals("${csi}1m", AnsiStyle.sgrFor(style(bold)))
    }

    @Test
    fun italicOnly() {
        assertEquals("${csi}3m", AnsiStyle.sgrFor(style(italic)))
    }

    @Test
    fun underlinedOnly() {
        assertEquals("${csi}4m", AnsiStyle.sgrFor(style(underlined)))
    }

    @Test
    fun boldItalicUnderlinedCombined() {
        assertEquals("${csi}1;3;4m", AnsiStyle.sgrFor(style(bold or italic or underlined)))
    }

    @Test
    fun foregroundColorDiffersFromNormal() {
        assertEquals("${csi}31m", AnsiStyle.sgrFor(style(fg = HypColor.RED)))
    }

    @Test
    fun backgroundColorDiffersFromNormal() {
        assertEquals("${csi}41m", AnsiStyle.sgrFor(style(bg = HypColor.RED)))
    }

    @Test
    fun darkVariantMapsToSameSgrDigitAsBaseColor() {
        // Same base digit (31), but still emitted independently since DARK_RED != Normal's
        // BLACK foreground (comparison is on the HypColor itself, mirroring HtmlSpans).
        assertEquals(AnsiStyle.sgrFor(style(fg = HypColor.RED)), AnsiStyle.sgrFor(style(fg = HypColor.DARK_RED)))
    }

    @Test
    fun lightGrayMapsToWhiteCode() {
        // Both differ from Normal's BLACK foreground, so both emit a code — the same digit (37).
        assertEquals(AnsiStyle.sgrFor(style(fg = HypColor.WHITE)), AnsiStyle.sgrFor(style(fg = HypColor.LIGHT_GRAY)))
    }

    @Test
    fun darkGrayForegroundUsesBlacksSgrDigit() {
        // "Differs from Normal" is checked on the HypColor itself, so DARK_GRAY (a different
        // HypColor than Normal's BLACK foreground) still emits a code — just black's digit (30).
        assertEquals("${csi}30m", AnsiStyle.sgrFor(style(fg = HypColor.DARK_GRAY)))
    }

    @Test
    fun combinedAttributesAndColors() {
        assertEquals("${csi}1;3;4;31m", AnsiStyle.sgrFor(style(bold or italic or underlined, fg = HypColor.RED)))
    }
}
