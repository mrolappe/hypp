package de.rholambdapi.hypp

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The phase-7 integration consumer: an HTML renderer that reaches a document only through the
 * public API, extending [hyp2text]'s walk with styled spans and images embedded as `data:` URIs
 * — also the artefact used for the by-eye cross-check against `hypview.cgi` (see `doc/PLAN.md`).
 */
fun hyp2html(document: HypDocument): String = buildString {
    appendLine("<!doctype html><html><body>")
    for (node in document.nodes) {
        append("<h2>").append(escapeHtml(node.name)).appendLine("</h2>")
        for (graphic in node.graphics) {
            if (graphic !is Graphic.Image) continue
            val image = document.image(graphic.imageIndex) ?: continue
            append("<img width=\"").append(image.width).append("\" height=\"").append(image.height)
            append("\" src=\"").append(dataUri(image)).appendLine("\">")
        }
        for (line in node.lines) {
            append("<p>")
            for (span in line.spans) append(renderSpan(span))
            appendLine("</p>")
        }
    }
    appendLine("</body></html>")
}

private fun renderSpan(span: Span): String = buildString {
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
        if (style.foreground != TextStyle.Normal.foreground) append("color:rgb(${style.foreground.red},${style.foreground.green},${style.foreground.blue});")
        if (style.background != TextStyle.Normal.background) append("background-color:rgb(${style.background.red},${style.background.green},${style.background.blue});")
    }
    append(if (css.isEmpty()) open else "<span style=\"$css\">$open</span>")
}

private fun escapeHtml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

@OptIn(ExperimentalEncodingApi::class)
private fun dataUri(image: ImageNode): String = "data:image/bmp;base64," + Base64.encode(bmp(image))

/** A minimal uncompressed 24-bit BMP encoder — no compression library needed for a by-eye check. */
private fun bmp(image: ImageNode): ByteArray {
    val rgba = image.toRgba()
    val rowSize = (image.width * 3 + 3) / 4 * 4
    val pixelDataSize = rowSize * image.height
    val out = ByteArray(54 + pixelDataSize)

    fun le16(at: Int, v: Int) {
        out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte()
    }
    fun le32(at: Int, v: Int) {
        out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte(); out[at + 2] = (v shr 16).toByte(); out[at + 3] = (v shr 24).toByte()
    }

    out[0] = 'B'.code.toByte(); out[1] = 'M'.code.toByte()
    le32(2, out.size)
    le32(10, 54)
    le32(14, 40)
    le32(18, image.width)
    le32(22, image.height)
    le16(26, 1)
    le16(28, 24)
    le32(34, pixelDataSize)

    var offset = 54
    for (y in image.height - 1 downTo 0) {
        for (x in 0 until image.width) {
            val i = (y * image.width + x) * 4
            out[offset] = rgba[i + 2]; out[offset + 1] = rgba[i + 1]; out[offset + 2] = rgba[i]
            offset += 3
        }
        offset += rowSize - image.width * 3
    }
    return out
}
