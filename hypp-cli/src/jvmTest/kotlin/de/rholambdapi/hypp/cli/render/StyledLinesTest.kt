package de.rholambdapi.hypp.cli.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural check of [styledLines] against real fixtures rather than hand-picked text, so a bug
 * in the node-to-StyledLine mapping shows up regardless of the fixture's exact wording.
 */
class StyledLinesTest {
    private fun assertStructurallyConsistent(documentName: String) {
        val document = Corpus.open(documentName)
        assertTrue(document.nodes.isNotEmpty(), "$documentName must have at least one node")
        for (node in document.nodes) {
            val lines = styledLines(node)
            assertEquals(node.lines.size, lines.size, "line count mismatch for node ${node.name}")
            node.lines.zip(lines).forEach { (line, styledLine) ->
                assertEquals(line.spans.size, styledLine.segments.size, "span count mismatch")
                line.spans.zip(styledLine.segments).forEach { (span, segment) ->
                    assertEquals(span.text, segment.text)
                    assertEquals(AnsiStyle.sgrFor(span.style), segment.sgr)
                }
            }
        }
    }

    @Test
    fun textattrIsStructurallyConsistent() = assertStructurallyConsistent("textattr")

    @Test
    fun colorsIsStructurallyConsistent() = assertStructurallyConsistent("colors")
}
