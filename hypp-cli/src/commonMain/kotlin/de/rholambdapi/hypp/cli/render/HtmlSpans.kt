package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle

/**
 * Ported from `hyp2html`'s span renderer in `hypp`'s own `commonTest` (`Hyp2Html.kt`) — the exact
 * same rules, shared by every renderer that needs HTML-shaped span markup.
 */
object HtmlSpans {
    fun renderSpan(span: Span): String = buildString {
        val link = span.link
        val text = escapeHtml(span.text)
        if (link != null) {
            append("<a href=\"#").append(link.target.value).append("\">").append(text).append("</a>")
            return@buildString
        }
        val style = span.style
        var open = text
        if (style.isBold) open = "<b>$open</b>"
        if (style.isItalic) open = "<i>$open</i>"
        if (style.isUnderlined) open = "<u>$open</u>"
        val css = buildString {
            if (style.foreground != TextStyle.Normal.foreground) {
                append("color:rgb(${style.foreground.red},${style.foreground.green},${style.foreground.blue});")
            }
            if (style.background != TextStyle.Normal.background) {
                append("background-color:rgb(${style.background.red},${style.background.green},${style.background.blue});")
            }
        }
        append(if (css.isEmpty()) open else "<span style=\"$css\">$open</span>")
    }

    fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
