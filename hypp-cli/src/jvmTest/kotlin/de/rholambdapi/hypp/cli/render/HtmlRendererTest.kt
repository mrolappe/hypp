package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.ImageNode
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [textattr]'s node has no image, so the image-URI path is exercised separately with a synthetic
 * single-node document/image built directly from the public model (no vendored fixture among the
 * six carries an image).
 */
class HtmlRendererTest {
    @Test
    fun rendersTextattrStructurally() {
        val document = Corpus.open("textattr")
        val html = HtmlRenderer().render(document)

        assertTrue(html.startsWith("<!doctype html><html><head><meta charset=\"utf-8\"></head><body>"))
        assertTrue(html.trim().endsWith("</body></html>"))

        for (node in document.nodes) {
            val expectedH2 = "<h2 id=\"${node.index.value}\">${HtmlSpans.escapeHtml(node.name)}</h2>"
            assertTrue(html.contains(expectedH2), "missing h2 for ${node.name}")
            for (line in node.lines) {
                val expectedP = "<p>" + line.spans.joinToString("") { HtmlSpans.renderSpan(it) } + "</p>"
                assertTrue(html.contains(expectedP), "expected to find $expectedP")
            }
        }

        val expectedPCount = document.nodes.sumOf { it.lines.size }
        assertEquals(expectedPCount, Regex("<p>").findAll(html).count())
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun rendersImageAsPngDataUri() {
        val imageIndex = NodeIndex(1)
        val image = ImageNode(
            index = imageIndex,
            name = "pic",
            width = 2,
            height = 2,
            planeCount = 1,
            planePresent = 1,
            planeFilled = 0,
            planeData = byteArrayOf(0xC0.toByte(), 0, 0xC0.toByte(), 0),
        )
        val graphic = Graphic.Image(imageIndex, x = 1, y = 0, width = 0, height = 0, ditherMask = null)
        val node = Node(
            index = NodeIndex(0),
            name = "Pictures",
            kind = NodeKind.TEXT,
            windowTitle = null,
            graphics = listOf(graphic),
            crossReferences = emptyList(),
            dataBlocks = emptyList(),
            objectTable = emptyList(),
            lines = listOf(Line(listOf(Span("caption", TextStyle.Normal)))),
        )
        val document = HypDocument(
            header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
            extendedHeaders = emptyList(),
            entries = emptyList(),
            charset = HypCharset.Default,
            nodes = listOf(node),
            images = listOf(image),
            diagnostics = emptyList(),
        )

        val html = HtmlRenderer().render(document)

        val expectedDataUri = "data:image/png;base64," + Base64.encode(StoredPngEncoder.encode(image))
        assertTrue(html.contains("<img width=\"2\" height=\"2\" src=\"$expectedDataUri\">"))
        assertTrue(html.contains("<p>caption</p>"))
    }
}
