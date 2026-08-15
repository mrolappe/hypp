package de.rholambdapi.hypp

import kotlin.jvm.JvmInline

/**
 * The visual attributes and colours in force for a [Span]. Packed into one Int: bits 0-5 are
 * the format's own absolute attribute bit-vector (escapes `0x64`-`0xa3`, the escape code minus
 * `0x64`), bits 8-11 the foreground [HypColor] index and bits 12-15 the background one.
 *
 * The attribute vector is absolute, not a delta: every attribute escape replaces the whole set.
 */
@JvmInline
value class TextStyle(val bits: Int) {
    /** The format's absolute attribute bit-vector, as written on the wire. */
    val attributes: Int get() = bits and ATTRIBUTE_MASK

    val isBold: Boolean get() = bits and BOLD != 0
    val isLight: Boolean get() = bits and LIGHT != 0
    val isItalic: Boolean get() = bits and ITALIC != 0
    val isUnderlined: Boolean get() = bits and UNDERLINED != 0
    val isOutlined: Boolean get() = bits and OUTLINED != 0
    val isShadowed: Boolean get() = bits and SHADOWED != 0

    val foreground: HypColor get() = HypColor.entries[(bits shr FG_SHIFT) and COLOR_MASK]
    val background: HypColor get() = HypColor.entries[(bits shr BG_SHIFT) and COLOR_MASK]

    internal fun withAttributes(vector: Int) =
        TextStyle((bits and ATTRIBUTE_MASK.inv()) or (vector and ATTRIBUTE_MASK))

    internal fun withForeground(color: HypColor) =
        TextStyle((bits and (COLOR_MASK shl FG_SHIFT).inv()) or (color.ordinal shl FG_SHIFT))

    internal fun withBackground(color: HypColor) =
        TextStyle((bits and (COLOR_MASK shl BG_SHIFT).inv()) or (color.ordinal shl BG_SHIFT))

    override fun toString(): String {
        val parts = ArrayList<String>(3)
        if (isBold) parts += "bold"
        if (isLight) parts += "light"
        if (isItalic) parts += "italic"
        if (isUnderlined) parts += "underlined"
        if (isOutlined) parts += "outlined"
        if (isShadowed) parts += "shadowed"
        parts += "fg=$foreground"
        parts += "bg=$background"
        return "TextStyle(${parts.joinToString(", ")})"
    }

    companion object {
        private const val BOLD = 1
        private const val LIGHT = 2
        private const val ITALIC = 4
        private const val UNDERLINED = 8
        private const val OUTLINED = 16
        private const val SHADOWED = 32
        private const val ATTRIBUTE_MASK = 0x3F
        private const val COLOR_MASK = 0xF
        private const val FG_SHIFT = 8
        private const val BG_SHIFT = 12

        /** No attributes, black on white — the format's documented default colours. */
        val Normal: TextStyle = TextStyle(HypColor.BLACK.ordinal shl FG_SHIFT)
    }
}
