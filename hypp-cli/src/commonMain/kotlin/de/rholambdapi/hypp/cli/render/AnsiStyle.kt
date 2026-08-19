package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypColor
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.TextStyle

private val ESC = 27.toChar()

/** SGR mapping: bold/italic/underline plus an 8-colour fg/bg fallback (no ANSI light/outline/shadow). */
object AnsiStyle {
    fun sgrFor(style: TextStyle): String {
        val codes = ArrayList<Int>(5)
        if (style.isBold) codes += 1
        if (style.isItalic) codes += 3
        if (style.isUnderlined) codes += 4
        if (style.foreground != TextStyle.Normal.foreground) codes += 30 + ansiBase(style.foreground)
        if (style.background != TextStyle.Normal.background) codes += 40 + ansiBase(style.background)
        return "$ESC[" + codes.joinToString(";") + "m"
    }

    /** Nearest ANSI-name match; DARK_* variants share their non-dark counterpart's base digit. */
    private fun ansiBase(color: HypColor): Int = when (color) {
        HypColor.BLACK, HypColor.DARK_GRAY -> 0
        HypColor.RED, HypColor.DARK_RED -> 1
        HypColor.GREEN, HypColor.DARK_GREEN -> 2
        HypColor.YELLOW, HypColor.DARK_YELLOW -> 3
        HypColor.BLUE, HypColor.DARK_BLUE -> 4
        HypColor.MAGENTA, HypColor.DARK_MAGENTA -> 5
        HypColor.CYAN, HypColor.DARK_CYAN -> 6
        HypColor.WHITE, HypColor.LIGHT_GRAY -> 7
    }
}

/** One run of text sharing one SGR escape. */
data class StyledSegment(val text: String, val sgr: String)

/** One node line, ANSI-styled but not yet flattened — a reusable intermediate for a future TUI. */
data class StyledLine(val segments: List<StyledSegment>)

fun styledLines(node: Node): List<StyledLine> =
    node.lines.map { line -> StyledLine(line.spans.map { StyledSegment(it.text, AnsiStyle.sgrFor(it.style)) }) }
