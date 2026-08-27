package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
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
        fun imageTag(node: Node, graphic: Graphic.Image): String? {
            val image = document.image(graphic.imageIndex) ?: return null
            val dataUri = "data:image/png;base64," + Base64.encode(imageEncoder.encode(image))
            return "<img width=\"${image.width}\" height=\"${image.height}\"" +
                "${HtmlSpans.imageSizeStyle(node)} src=\"$dataUri\">"
        }

        // A popup/external-ref target has no page section to jump to; it lives in a <dialog> that
        // only script can open. The index is an Int, so it interpolates into the handler safely.
        val linkMarkup: LinkMarkup = { text, target ->
            if (HtmlSpans.isStubTarget(document, target)) {
                "<a href=\"#\" onclick=\"document.getElementById('popup-${target.value}')" +
                    ".showModal();return false;\">$text</a>"
            } else {
                fragmentLink(text, target)
            }
        }

        appendLine("<!doctype html><html><head><meta charset=\"utf-8\"><style>body{$HTML_BODY_STYLE}</style></head><body>")
        appendLine(VectorGraphicSvg.sharedDefs())
        for (node in document.nodes) {
            // Popup nodes are emitted below as dialogs instead — ST-Guide shows them in a transient
            // window over the current page, never as a page of their own.
            if (node.kind == NodeKind.POPUP) continue
            append("<h2 id=\"").append(node.index.value).append("\">")
            append(HtmlSpans.escapeHtml(node.name)).appendLine("</h2>")

            appendLine(HtmlSpans.renderGrid(node, linkMarkup) { imageTag(node, it) })
        }
        for (index in document.entries.indices) {
            val stub = HtmlSpans.stubContent(document, NodeIndex(index), linkMarkup, ::imageTag) ?: continue
            append("<dialog id=\"popup-$index\">").append(stub)
                .appendLine("<form method=\"dialog\"><button>Close</button></form></dialog>")
        }
        appendLine("</body></html>")
    }
}
