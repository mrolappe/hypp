package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

object AsciiDocRenderer : Renderer {
    private val syntax = MarkupSyntax(
        boldOpen = "*",
        boldClose = "*",
        italicOpen = "_",
        italicClose = "_",
        underlineOpen = "[.underline]#",
        underlineClose = "#",
        link = { label, target -> "link:#$target[$label]" },
        heading = { level, text -> "=".repeat(level) + " " + text },
        escape = { text ->
            text.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("#", "\\#")
                .replace("`", "\\`")
                .replace("+", "\\+")
        }
    )

    override fun render(document: HypDocument): String = renderMarkup(document, syntax)
}
