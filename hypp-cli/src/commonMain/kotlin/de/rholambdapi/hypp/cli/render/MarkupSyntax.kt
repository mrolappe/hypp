package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Span

/**
 * The token strings and formatting hooks that distinguish one plain-text markup dialect from
 * another, so Markdown/AsciiDoc/Org-mode differ only by their [MarkupSyntax] value and share
 * [renderMarkup]'s walk.
 *
 * [link] gets the target's entry index rather than a name: there is no cross-document name
 * lookup at this layer, so a target can only be rendered as an anchor/fragment id. [escape] is
 * applied to raw span text only — never to the tokens this type supplies, and never to a link's
 * label, which the [link] lambda is free to escape as its dialect requires.
 *
 * [heading] also gets the node's own index, so a dialect can emit a matching anchor (an explicit
 * id, block attribute, or property drawer — whatever that dialect needs) for [link]'s `#<target>`
 * fragments to actually resolve to.
 */
data class MarkupSyntax(
    val boldOpen: String, val boldClose: String,
    val italicOpen: String, val italicClose: String,
    val underlineOpen: String, val underlineClose: String,
    val link: (label: String, target: Int) -> String,
    val heading: (level: Int, text: String, index: Int) -> String,
    val escape: (text: String) -> String,
)

/**
 * Renders every node as a level-2 heading (matching `hyp2html`'s per-node `<h2>`) followed by its
 * lines, one per output line, with a blank line between nodes.
 */
fun renderMarkup(document: HypDocument, syntax: MarkupSyntax): String =
    document.nodes.joinToString("\n\n") { node ->
        val lines = node.lines.map { line -> line.spans.joinToString("") { renderSpan(it, syntax) } }
        (listOf(syntax.heading(2, node.name, node.index.value)) + lines).joinToString("\n")
    }

/** Bold outermost, then italic, then underline — the same nesting order as `hyp2html`. */
private fun renderSpan(span: Span, syntax: MarkupSyntax): String {
    val link = span.link
    if (link != null) return syntax.link(link.label, link.target.value)
    var out = syntax.escape(span.text)
    val style = span.style
    if (style.isUnderlined) out = syntax.underlineOpen + out + syntax.underlineClose
    if (style.isItalic) out = syntax.italicOpen + out + syntax.italicClose
    if (style.isBold) out = syntax.boldOpen + out + syntax.boldClose
    return out
}
