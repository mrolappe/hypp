package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.ExternalRef
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Link
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.ResolvedTarget
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.resolve

/**
 * The token strings and formatting hooks that distinguish one plain-text markup dialect from
 * another, so Markdown/AsciiDoc/Org-mode differ only by their [MarkupSyntax] value and share
 * [renderMarkup]'s walk.
 *
 * [escape] is applied by [renderMarkup] to everything that comes out of the source file — span
 * text, link labels, heading text, and a stub's description — so every hook here receives text
 * that is already escaped for this dialect and must inline it as-is. The tokens this type supplies
 * are never escaped.
 *
 * Three of the hooks cover the three things a link's target can turn out to be, which
 * [renderMarkup] decides by resolving it (the raw index alone cannot tell them apart):
 *
 * - [link] — an ordinary node, which has a section of its own to jump to. It gets the target's
 *   entry index rather than a name: there is no cross-document name lookup at this layer, so a
 *   target can only be rendered as an anchor/fragment id. [heading] also gets the node's own index,
 *   so a dialect can emit a matching anchor (an explicit id, block attribute, or property drawer)
 *   for those fragments to resolve to.
 * - [popup] — a [NodeKind.POPUP] node, which ST-Guide shows in a transient window over the current
 *   page and which therefore gets no section of its own (see [renderMarkup]). None of these three
 *   dialects has an interactive-popup primitive, so each renders `content` — the popup's own lines,
 *   already rendered and newline-separated — inline at the link site using its native
 *   admonition/block-quote construct.
 * - [stub] — an external ref or a system action, neither of which has any page in this document to
 *   link to (see [HtmlSpans.stubContent] for the same three cases in HTML). `description` says what
 *   the target was; the label is emitted as plain text, never as a link, since the fragment would
 *   be dead.
 */
data class MarkupSyntax(
    val boldOpen: String, val boldClose: String,
    val italicOpen: String, val italicClose: String,
    val underlineOpen: String, val underlineClose: String,
    val link: (label: String, target: Int) -> String,
    val popup: (label: String, content: String) -> String,
    val stub: (label: String, description: String) -> String,
    val heading: (level: Int, text: String, index: Int) -> String,
    val escape: (text: String) -> String,
)

/**
 * Renders every node as a level-2 heading (matching `hyp2html`'s per-node `<h2>`) followed by its
 * lines, one per output line, with a blank line between nodes.
 *
 * Popup nodes are skipped here — like [HtmlRenderer] they are emitted at the link site instead
 * (via [MarkupSyntax.popup]), because ST-Guide shows them over the current page, never as a page
 * of their own.
 */
fun renderMarkup(document: HypDocument, syntax: MarkupSyntax): String =
    document.nodes.filterNot { it.kind == NodeKind.POPUP }.joinToString("\n\n") { node ->
        val heading = syntax.heading(2, syntax.escape(node.name), node.index.value)
        (listOf(heading) + node.renderLines(document, syntax, insidePopup = false)).joinToString("\n")
    }

private fun Node.renderLines(document: HypDocument, syntax: MarkupSyntax, insidePopup: Boolean): List<String> =
    lines.map { line -> line.spans.joinToString("") { renderSpan(it, document, syntax, insidePopup) } }

/** Bold outermost, then italic, then underline — the same nesting order as `hyp2html`. */
private fun renderSpan(span: Span, document: HypDocument, syntax: MarkupSyntax, insidePopup: Boolean): String {
    val link = span.link
    if (link != null) return renderLink(link, document, syntax, insidePopup)
    var out = syntax.escape(span.text)
    val style = span.style
    if (style.isUnderlined) out = syntax.underlineOpen + out + syntax.underlineClose
    if (style.isItalic) out = syntax.italicOpen + out + syntax.italicClose
    if (style.isBold) out = syntax.boldOpen + out + syntax.boldClose
    return out
}

/**
 * [insidePopup] is the recursion firebreak: a popup's content is rendered by this same walk, so two
 * popups linking to each other would otherwise inline each other forever. Inside one, a further
 * popup degrades to its plain label. Stubs need no such guard — they render no nested content.
 */
private fun renderLink(link: Link, document: HypDocument, syntax: MarkupSyntax, insidePopup: Boolean): String {
    val label = syntax.escape(link.label)
    return when (val resolved = document.resolve(link.target)) {
        is ResolvedTarget.ToNode ->
            when {
                resolved.node.kind != NodeKind.POPUP -> syntax.link(label, link.target.value)
                insidePopup -> label
                else -> syntax.popup(
                    label,
                    resolved.node.renderLines(document, syntax, insidePopup = true).joinToString("\n"),
                )
            }
        is ResolvedTarget.ToExternalRef -> syntax.stub(label, syntax.escape(resolved.ref.describe()))
        is ResolvedTarget.ToSystemAction ->
            syntax.stub(label, syntax.escape("viewer action, not available in this document: ${resolved.entry.name}"))
        else -> syntax.link(label, link.target.value)
    }
}

/**
 * Same two fields [HtmlSpans.stubContent] describes, worded for an inline parenthetical rather than
 * a block of its own. Nothing resolves an external ref yet (no multi-file input, no `.REF` lookup),
 * so naming the target is all a single-document render can honestly do.
 */
private fun ExternalRef.describe(): String =
    "external reference: " + if (fileName == null) nodeName else "$fileName/$nodeName"
