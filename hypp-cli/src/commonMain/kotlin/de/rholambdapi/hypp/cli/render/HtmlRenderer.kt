package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.HypDocument
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Ports `hyp2html` (`hypp`'s own `commonTest/Hyp2Html.kt`) into a real [Renderer], sharing
 * [HtmlSpans] with every other HTML-shaped renderer. The one deviation from the reference is the
 * image format: PNG via [imageEncoder] instead of the reference's hand-rolled BMP, since PNG is
 * what browsers actually want.
 */
class HtmlRenderer(private val imageEncoder: ImageEncoder = StoredPngEncoder) : Renderer {
    @OptIn(ExperimentalEncodingApi::class)
    override fun render(document: HypDocument): String = buildString {
        appendLine("<!doctype html><html><head><meta charset=\"utf-8\"></head><body>")
        for (node in document.nodes) {
            append("<h2 id=\"").append(node.index.value).append("\">")
            append(HtmlSpans.escapeHtml(node.name)).appendLine("</h2>")
            for (graphic in node.graphics) {
                if (graphic !is Graphic.Image) continue
                val image = document.image(graphic.imageIndex) ?: continue
                val dataUri = "data:image/png;base64," + Base64.encode(imageEncoder.encode(image))
                append("<img width=\"").append(image.width).append("\" height=\"").append(image.height)
                append("\" src=\"").append(dataUri).appendLine("\">")
            }
            for (line in node.lines) {
                append("<p>")
                for (span in line.spans) append(HtmlSpans.renderSpan(span))
                appendLine("</p>")
            }
        }
        appendLine("</body></html>")
    }
}
