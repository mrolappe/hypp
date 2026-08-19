package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

private val RESET = "${27.toChar()}[0m"

object AnsiRenderer : Renderer {
    override fun render(document: HypDocument): String = buildString {
        for (node in document.nodes) {
            appendLine(sanitize(node.name))
            for (line in styledLines(node)) {
                for (segment in line.segments) append(segment.sgr).append(sanitize(segment.text)).append(RESET)
                appendLine()
            }
        }
    }
}

/**
 * Strips C0 control bytes (including `ESC`) and DEL from document-sourced text before it reaches
 * a real terminal — a `.hyp` file is untrusted input, and an unsanitized node name or span could
 * otherwise inject arbitrary terminal escape sequences into `dump --format ansi`'s output.
 */
private fun sanitize(text: String): String = text.filter { it.code !in 0x00..0x1F && it.code != 0x7F }
