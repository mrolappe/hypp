package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.ImageNode
import de.rholambdapi.hypp.Node

private const val MIMETYPE = "mimetype"
private const val CONTAINER_XML = "META-INF/container.xml"
private const val CONTENT_OPF = "OEBPS/content.opf"
private const val NAV_XHTML = "OEBPS/nav.xhtml"

private fun xhtmlPath(node: Node) = "OEBPS/node-${node.index.value}.xhtml"
private fun imageId(image: ImageNode) = "img-${image.index.value}"
private fun imageHref(image: ImageNode) = "images/${imageId(image)}.png"

/**
 * Produces a minimal EPUB3 file set. Images referenced by any node's [Graphic.Image] are encoded
 * once each (via [imageEncoder]) as separate `OEBPS/images/img-<index>.png` files with their own
 * manifest entry — the spec-idiomatic, best-e-reader-compatibility shape, rather than inlining
 * them as XHTML data URIs. Zipping (mimetype stored uncompressed, per the EPUB spec) is the
 * caller's concern, not this renderer's.
 */
class EpubRenderer(private val imageEncoder: ImageEncoder = StoredPngEncoder) : ArchiveRenderer {
    override fun render(document: HypDocument): List<RenderedFile> {
        val referencedIndices = document.nodes.flatMap { it.graphics }
            .filterIsInstance<Graphic.Image>()
            .mapTo(mutableSetOf()) { it.imageIndex }
        val images = document.images.filter { it.index in referencedIndices }

        val files = mutableListOf<RenderedFile>()
        files += RenderedFile(MIMETYPE, "application/epub+zip".encodeToByteArray())
        files += RenderedFile(CONTAINER_XML, containerXml().encodeToByteArray())
        for (node in document.nodes) {
            files += RenderedFile(xhtmlPath(node), nodeXhtml(node, document).encodeToByteArray())
        }
        for (image in images) {
            files += RenderedFile("OEBPS/${imageHref(image)}", imageEncoder.encode(image))
        }
        files += RenderedFile(NAV_XHTML, navXhtml(document.nodes).encodeToByteArray())
        files += RenderedFile(CONTENT_OPF, contentOpf(document, images).encodeToByteArray())
        return files
    }

    private fun containerXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$CONTENT_OPF" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun nodeXhtml(node: Node, document: HypDocument): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<html xmlns="http://www.w3.org/1999/xhtml">""")
        val name = HtmlSpans.escapeHtml(node.name)
        appendLine("<head><title>$name</title></head>")
        appendLine("<body>")
        appendLine("<h1>$name</h1>")
        for (graphic in node.graphics) {
            if (graphic !is Graphic.Image) continue
            val image = document.image(graphic.imageIndex) ?: continue
            append("<img width=\"").append(image.width).append("\" height=\"").append(image.height)
            append("\" src=\"").append(imageHref(image)).appendLine("\"/>")
        }
        for (line in node.lines) {
            append("<p>")
            // Each node is its own XHTML document here, unlike HtmlRenderer's single page, so an
            // internal link must cross files (node-<target>.xhtml) rather than jump to a same-page
            // fragment that nothing in this file would define anyway.
            for (span in line.spans) append(HtmlSpans.renderSpan(span) { target -> "node-${target.value}.xhtml" })
            appendLine("</p>")
        }
        appendLine("</body>")
        append("</html>")
    }

    private fun navXhtml(nodes: List<Node>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">""",
        )
        appendLine("<head><title>Table of Contents</title></head>")
        appendLine("<body>")
        appendLine("""<nav epub:type="toc"><ol>""")
        for (node in nodes) {
            appendLine("<li><a href=\"node-${node.index.value}.xhtml\">${HtmlSpans.escapeHtml(node.name)}</a></li>")
        }
        appendLine("</ol></nav>")
        appendLine("</body>")
        append("</html>")
    }

    private fun contentOpf(document: HypDocument, images: List<ImageNode>): String = buildString {
        val nodes = document.nodes
        val title = document.title ?: nodes.firstOrNull()?.name ?: "hypp export"
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0">""")
        appendLine("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
        appendLine("""    <dc:identifier id="bookid">urn:uuid:hypp-export</dc:identifier>""")
        appendLine("    <dc:title>${HtmlSpans.escapeHtml(title)}</dc:title>")
        document.author?.let { appendLine("    <dc:creator>${HtmlSpans.escapeHtml(it)}</dc:creator>") }
        appendLine("""    <dc:language>en</dc:language>""")
        appendLine("  </metadata>")
        appendLine("  <manifest>")
        appendLine("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        for (node in nodes) {
            val id = "node-${node.index.value}"
            appendLine("""    <item id="$id" href="$id.xhtml" media-type="application/xhtml+xml"/>""")
        }
        for (image in images) {
            appendLine("""    <item id="${imageId(image)}" href="${imageHref(image)}" media-type="image/png"/>""")
        }
        appendLine("  </manifest>")
        appendLine("  <spine>")
        for (node in nodes) {
            appendLine("""    <itemref idref="node-${node.index.value}"/>""")
        }
        appendLine("  </spine>")
        append("</package>")
    }
}
