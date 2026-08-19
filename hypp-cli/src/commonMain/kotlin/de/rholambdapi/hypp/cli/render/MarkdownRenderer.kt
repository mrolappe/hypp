package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

object MarkdownRenderer : Renderer {
    private val syntax = MarkupSyntax(
        boldOpen = "**",
        boldClose = "**",
        italicOpen = "*",
        italicClose = "*",
        underlineOpen = "<u>",
        underlineClose = "</u>",
        link = { label, target -> "[$label](#$target)" },
        heading = { level, text -> "#".repeat(level) + " " + text },
        escape = { text ->
            text.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("<", "\\<")
                .replace(">", "\\>")
        }
    )

    override fun render(document: HypDocument): String = renderMarkup(document, syntax)
}
