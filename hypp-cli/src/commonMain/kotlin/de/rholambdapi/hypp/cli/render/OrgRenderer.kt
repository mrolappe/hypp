package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

object OrgRenderer : Renderer {
    private val syntax = MarkupSyntax(
        boldOpen = "*",
        boldClose = "*",
        italicOpen = "/",
        italicClose = "/",
        underlineOpen = "_",
        underlineClose = "_",
        link = { label, target -> "[[#$target][$label]]" },
        heading = { level, text -> "*".repeat(level) + " " + text },
        escape = { text ->
            // ponytail: word-boundary emphasis detection simplified to leading-char escaping only;
            // proper implementation would check word boundaries per Org spec if needed
            if (text.isNotEmpty() && text[0] in "*/_=~+") {
                "\\" + text
            } else {
                text
            }
        }
    )

    override fun render(document: HypDocument): String = renderMarkup(document, syntax)
}
