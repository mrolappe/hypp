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
        // A quote block, not a `[fn:N]` footnote: the popup belongs at the link site (as it does in
        // the other two dialects), and a footnote would instead need a definition parked at the end
        // of the document plus a unique-N allocator — state this stateless per-span walk does not
        // have, for an out-of-line result no other renderer produces.
        popup = { label, content -> "*$label*\n\n#+BEGIN_QUOTE\n$content\n#+END_QUOTE\n" },
        stub = { label, description -> "*$label* /($description)/" },
        // Org resolves a `[[#id]]` fuzzy link against a heading's :CUSTOM_ID: property, so the
        // property drawer right after the heading line is what makes the link above resolve.
        heading = { level, text, index ->
            "*".repeat(level) + " " + text + "\n:PROPERTIES:\n:CUSTOM_ID: $index\n:END:"
        },
        escape = { text ->
            // Org has no general escape character. Emphasis instead only *opens* at a line start or
            // after whitespace/opening punctuation, so a backslash in front of a marker in one of
            // those positions is what stops it opening; a marker that can only close is inert on
            // its own. Escaping the leading character alone was not enough once composed strings
            // (a stub's description) started flowing through here — the marker is never first.
            // ponytail: approximates org-emphasis-regexp's pre-char set with start-or-after-space;
            // widen to its full set (`-–—('"{`) if a real file turns one up.
            buildString(text.length) {
                for ((position, char) in text.withIndex()) {
                    if (char in "*/_=~+" && (position == 0 || text[position - 1] == ' ')) append('\\')
                    append(char)
                }
            }
        }
    )

    override fun render(document: HypDocument): String = renderMarkup(document, syntax)
}
