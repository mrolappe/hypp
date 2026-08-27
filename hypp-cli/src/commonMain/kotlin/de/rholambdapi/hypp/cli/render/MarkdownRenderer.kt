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
        // GFM's alert syntax: a block quote whose first line is the alert type. Rendered inline at
        // the link site, so it opens with the label and a blank line to leave the quote its own block.
        popup = { label, content ->
            "**$label**\n\n> [!NOTE]\n" +
                content.lines().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" } + "\n"
        },
        stub = { label, description -> "**$label** _($description)_" },
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
