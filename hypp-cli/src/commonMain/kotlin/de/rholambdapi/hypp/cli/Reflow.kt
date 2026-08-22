package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle

private const val BULLET_MARKER = '·' // '·', this format's own bullet glyph (see doc/LEARNINGS.md)

private fun Line.isBreak(): Boolean = text.isBlank() || text.trimStart().startsWith(BULLET_MARKER)

/**
 * Joins hard-wrapped source lines back into flowing paragraphs: a blank line or a bullet-marked
 * line ("· ...") never merges with a neighbor, so list structure survives; every other run of
 * consecutive lines is joined into one [Line] with a plain space [Span] between each pair.
 */
internal fun reflowLines(lines: List<Line>): List<Line> {
    val result = mutableListOf<Line>()
    var paragraph: MutableList<Span>? = null

    fun flush() {
        paragraph?.let { result += Line(it) }
        paragraph = null
    }

    for (line in lines) {
        if (line.isBreak()) {
            flush()
            result += line
            continue
        }
        val spans = paragraph
        if (spans == null) {
            paragraph = line.spans.toMutableList()
        } else {
            spans += Span(" ", TextStyle.Normal)
            spans += line.spans
        }
    }
    flush()
    return result
}

/** Applies [reflowLines] to every node's text, leaving graphics and everything else untouched. */
fun reflow(document: HypDocument): HypDocument = HypDocument(
    header = document.header,
    extendedHeaders = document.extendedHeaders,
    entries = document.entries,
    charset = document.charset,
    nodes = document.nodes.map { node ->
        Node(
            index = node.index,
            name = node.name,
            kind = node.kind,
            windowTitle = node.windowTitle,
            graphics = node.graphics,
            crossReferences = node.crossReferences,
            dataBlocks = node.dataBlocks,
            objectTable = node.objectTable,
            lines = reflowLines(node.lines),
        )
    },
    images = document.images,
    diagnostics = document.diagnostics,
)
