package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Node

private const val MIMETYPE = "mimetype"
private const val CONTAINER_XML = "META-INF/container.xml"
private const val CONTENT_OPF = "OEBPS/content.opf"
private const val NAV_XHTML = "OEBPS/nav.xhtml"

private fun xhtmlPath(node: Node) = "OEBPS/node-${node.index.value}.xhtml"

/**
 * Produces a minimal EPUB3 file set — no graphics yet, [imageEncoder] is a placeholder for a
 * future step that embeds [de.rholambdapi.hypp.Graphic.Image]s into the XHTML bodies. Zipping
 * (mimetype stored uncompressed, per the EPUB spec) is the caller's concern, not this renderer's.
 */
class EpubRenderer(private val imageEncoder: ImageEncoder = StoredPngEncoder) : ArchiveRenderer {
    override fun render(document: HypDocument): List<RenderedFile> {
        val files = mutableListOf<RenderedFile>()
        files += RenderedFile(MIMETYPE, "application/epub+zip".encodeToByteArray())
        files += RenderedFile(CONTAINER_XML, containerXml().encodeToByteArray())
        for (node in document.nodes) {
            files += RenderedFile(xhtmlPath(node), nodeXhtml(node).encodeToByteArray())
        }
        files += RenderedFile(NAV_XHTML, navXhtml(document.nodes).encodeToByteArray())
        files += RenderedFile(CONTENT_OPF, contentOpf(document.nodes).encodeToByteArray())
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

    private fun nodeXhtml(node: Node): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<html xmlns="http://www.w3.org/1999/xhtml">""")
        val name = HtmlSpans.escapeHtml(node.name)
        appendLine("<head><title>$name</title></head>")
        appendLine("<body>")
        appendLine("<h1>$name</h1>")
        for (line in node.lines) {
            append("<p>")
            for (span in line.spans) append(HtmlSpans.renderSpan(span))
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

    private fun contentOpf(nodes: List<Node>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0">""")
        appendLine("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
        appendLine("""    <dc:identifier id="bookid">urn:uuid:hypp-export</dc:identifier>""")
        appendLine("""    <dc:title>hypp export</dc:title>""")
        appendLine("""    <dc:language>en</dc:language>""")
        appendLine("  </metadata>")
        appendLine("  <manifest>")
        appendLine("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        for (node in nodes) {
            val id = "node-${node.index.value}"
            appendLine("""    <item id="$id" href="$id.xhtml" media-type="application/xhtml+xml"/>""")
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
