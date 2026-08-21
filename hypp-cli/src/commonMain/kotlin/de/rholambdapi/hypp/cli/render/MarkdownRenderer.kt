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
        // GFM/CommonMark headings auto-slug their anchor from the heading text, not an arbitrary
        // id, so `[label](#0)` needs an explicit raw-HTML anchor (permitted inline in both
        // dialects) rather than relying on the heading's own generated fragment.
        heading = { level, text, index -> "<a id=\"$index\"></a>\n" + "#".repeat(level) + " " + text },
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
