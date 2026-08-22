package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle

private const val BULLET_MARKER = '·' // '·', this format's own bullet glyph (see doc/LEARNINGS.md)

private fun Line.isBreak(): Boolean = text.isBlank() || text.trimStart().startsWith(BULLET_MARKER)

/** [reflowLines]'s output, plus the original-row -> reflowed-row mapping [reflow] needs to keep a node's row-positioned [Graphic]s aligned with their text. */
private class ReflowedLines(val lines: List<Line>, val rowMap: IntArray)

/**
 * Joins hard-wrapped source lines back into flowing paragraphs: a blank line or a bullet-marked
 * line ("· ...") never merges with a neighbor, so list structure survives; every other run of
 * consecutive lines is joined into one [Line] with a plain space [Span] between each pair.
 * [ReflowedLines.rowMap] records, per original line index, which reflowed line it ended up in —
 * every source line that merged into one paragraph maps to that paragraph's row.
 */
private fun reflowWithRowMap(lines: List<Line>): ReflowedLines {
    val result = mutableListOf<Line>()
    val rowMap = IntArray(lines.size)
    var paragraph: MutableList<Span>? = null
    var paragraphRow = -1

    fun flush() {
        paragraph?.let { result += Line(it) }
        paragraph = null
    }

    lines.forEachIndexed { i, line ->
        if (line.isBreak()) {
            flush()
            rowMap[i] = result.size
            result += line
        } else {
            if (paragraph == null) {
                paragraphRow = result.size
                paragraph = line.spans.toMutableList()
            } else {
                paragraph!! += Span(" ", TextStyle.Normal)
                paragraph!! += line.spans
            }
            rowMap[i] = paragraphRow
        }
    }
    flush()
    return ReflowedLines(result, rowMap)
}

internal fun reflowLines(lines: List<Line>): List<Line> = reflowWithRowMap(lines).lines

/** Carries a graphic's row forward through [rowMap] (see [reflowWithRowMap]), clamping an out-of-range row rather than dropping the graphic. */
private fun Graphic.remappedTo(rowMap: IntArray): Graphic {
    val newY = if (y in rowMap.indices) rowMap[y] else y.coerceIn(0, rowMap.size)
    return when (this) {
        is Graphic.Image -> Graphic.Image(imageIndex, x, newY, width, height, ditherMask)
        is Graphic.Line -> copy(y = newY)
        is Graphic.Box -> copy(y = newY)
        is Graphic.RoundedBox -> copy(y = newY)
    }
}

/**
 * Applies [reflowLines] to every node's text. A node's [Graphic]s are positioned by text row
 * (`y`, in character cells — see `Graphic`'s own kdoc), so joining hard-wrapped lines into fewer,
 * longer paragraphs shifts every row after the join point; each graphic is carried forward to its
 * paragraph's new row via [Graphic.remappedTo] rather than left pointing at stale line numbers.
 */
fun reflow(document: HypDocument): HypDocument = HypDocument(
    header = document.header,
    extendedHeaders = document.extendedHeaders,
    entries = document.entries,
    charset = document.charset,
    nodes = document.nodes.map { node ->
        val reflowed = reflowWithRowMap(node.lines)
        Node(
            index = node.index,
            name = node.name,
            kind = node.kind,
            windowTitle = node.windowTitle,
            graphics = node.graphics.map { it.remappedTo(reflowed.rowMap) },
            crossReferences = node.crossReferences,
            dataBlocks = node.dataBlocks,
            objectTable = node.objectTable,
            lines = reflowed.lines,
        )
    },
    images = document.images,
    diagnostics = document.diagnostics,
)
