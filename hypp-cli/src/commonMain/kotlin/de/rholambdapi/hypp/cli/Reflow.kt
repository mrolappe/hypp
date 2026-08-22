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

private fun mapRow(row: Int, rowMap: IntArray): Int =
    if (row in rowMap.indices) rowMap[row] else row.coerceIn(0, rowMap.size)

/**
 * Carries a graphic's row span forward through [rowMap] (see [reflowWithRowMap]), clamping an
 * out-of-range row rather than dropping the graphic. Every graphic type's `height` is a row
 * *count* (`y` to `y + height - 1` inclusive) — a [Graphic.Line] is no different from a
 * [Graphic.Box]/[Graphic.RoundedBox] here, per [de.rholambdapi.hypp.cli.render.toVectorGraphic].
 * Reflow can merge every original row the graphic spans into one reflowed row, so `height` is
 * recomputed from how far apart the mapped start/end rows land, not copied through unchanged —
 * otherwise a box that now fits in a single reflowed row would still claim its old, now-meaningless
 * row count.
 */
private fun Graphic.remappedTo(rowMap: IntArray): Graphic {
    val newY = mapRow(y, rowMap)
    return when (this) {
        is Graphic.Image -> Graphic.Image(imageIndex, x, newY, width, height, ditherMask)
        is Graphic.Line -> {
            val newEndRow = mapRow(y + (height - 1).coerceAtLeast(0), rowMap).coerceAtLeast(newY)
            copy(y = newY, height = newEndRow - newY + 1)
        }
        is Graphic.Box -> {
            val newEndRow = mapRow(y + (height - 1).coerceAtLeast(0), rowMap).coerceAtLeast(newY)
            copy(y = newY, height = newEndRow - newY + 1)
        }
        is Graphic.RoundedBox -> {
            val newEndRow = mapRow(y + (height - 1).coerceAtLeast(0), rowMap).coerceAtLeast(newY)
            copy(y = newY, height = newEndRow - newY + 1)
        }
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
