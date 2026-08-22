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
    /**
     * Rows with no graphic between them share one `<p>`, joined by `\n` (relying on
     * [HTML_BODY_STYLE]'s `white-space:pre-wrap`) instead of one `<p>` per row — mirrors
     * [HtmlRenderer]'s grouping rather than asserting a fixed one-`<p>`-per-row shape.
     */
    private fun expectedParagraphs(node: de.rholambdapi.hypp.Node): List<String> {
        val graphicRows = node.graphics.mapTo(mutableSetOf()) { it.y.coerceIn(0, node.lines.size) }
        val paragraphs = mutableListOf<MutableList<String>>()
        node.lines.forEachIndexed { index, line ->
            val text = line.spans.joinToString("") { HtmlSpans.renderSpan(it) }
            if (index in graphicRows || paragraphs.isEmpty()) paragraphs += mutableListOf(text) else paragraphs.last() += text
        }
        return paragraphs.map { "<p>" + it.joinToString("\n") + "</p>" }
    }

    @Test
    fun rendersTextattrStructurally() {
        val document = Corpus.open("textattr")
        val html = HtmlRenderer().render(document)

        assertTrue(html.startsWith("<!doctype html><html><head><meta charset=\"utf-8\"><style>body{$HTML_BODY_STYLE}</style></head><body>"))
        assertTrue(html.trim().endsWith("</body></html>"))

        var expectedPCount = 0
        for (node in document.nodes) {
            val expectedH2 = "<h2 id=\"${node.index.value}\">${HtmlSpans.escapeHtml(node.name)}</h2>"
            assertTrue(html.contains(expectedH2), "missing h2 for ${node.name}")
            for (expectedP in expectedParagraphs(node)) {
                assertTrue(html.contains(expectedP), "expected to find $expectedP")
                expectedPCount++
            }
        }

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
