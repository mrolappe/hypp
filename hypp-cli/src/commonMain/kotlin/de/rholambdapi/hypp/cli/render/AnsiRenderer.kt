package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

private val RESET = "${27.toChar()}[0m"

object AnsiRenderer : Renderer {
    override fun render(document: HypDocument): String = buildString {
        for (node in document.nodes) {
            appendLine(node.name)
            for (line in styledLines(node)) {
                for (segment in line.segments) append(segment.sgr).append(segment.text).append(RESET)
                appendLine()
            }
        }
    }
}
