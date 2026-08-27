package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.ImageNode
import de.rholambdapi.hypp.IndexEntry
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Link
import de.rholambdapi.hypp.LinkKind
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
    /** Every node renders as exactly one `<p style="margin:0">`, its rows joined by `\n`
     * (relying on [HTML_BODY_STYLE]'s `white-space:pre-wrap`) — graphics no longer split it,
     * since they're positioned overlays now instead of DOM-order-interleaved blocks.
     */
    private fun expectedParagraph(node: de.rholambdapi.hypp.Node): String =
        "<p style=\"margin:0\">" +
            node.lines.joinToString("\n") { line -> line.spans.joinToString("") { HtmlSpans.renderSpan(it) } } +
            "</p>"

    @Test
    fun rendersTextattrStructurally() {
        val document = Corpus.open("textattr")
        val html = HtmlRenderer().render(document)

        assertTrue(html.startsWith("<!doctype html><html><head><meta charset=\"utf-8\"><style>body{$HTML_BODY_STYLE}</style></head><body>"))
        assertTrue(html.trim().endsWith("</body></html>"))

        for (node in document.nodes) {
            val expectedH2 = "<h2 id=\"${node.index.value}\">${HtmlSpans.escapeHtml(node.name)}</h2>"
            assertTrue(html.contains(expectedH2), "missing h2 for ${node.name}")
            assertTrue(html.contains(expectedParagraph(node)), "expected to find paragraph for ${node.name}")
        }

        // No graphic/position bucketing left to get wrong — one <p> per node, always.
        assertEquals(document.nodes.size, Regex("<p style=\"margin:0\">").findAll(html).count())
    }

    @Test
    fun rendersGraphicAsAbsolutelyPositionedOverlay() {
        val line = Graphic.Line(x = 5, y = 0, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val node = Node(
            index = NodeIndex(0),
            name = "Deco",
            kind = NodeKind.TEXT,
            windowTitle = null,
            graphics = listOf(line),
            crossReferences = emptyList(),
            dataBlocks = emptyList(),
            objectTable = emptyList(),
            lines = listOf(Line(listOf(Span("row", TextStyle.Normal)))),
        )
        val document = HypDocument(
            header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
            extendedHeaders = emptyList(),
            entries = emptyList(),
            charset = HypCharset.Default,
            nodes = listOf(node),
            images = emptyList(),
            diagnostics = emptyList(),
        )

        val html = HtmlRenderer().render(document)

        assertTrue(html.contains("<div style=\"position:absolute;z-index:1;top:0em;left:5ch\">"), html)
    }

    @Test
    // Centring is an image-only placement in this format (@line/@box/@rbox carry x in 1-255), so a
    // vector graphic's x is always a plain column — zero included.
    fun rendersAVectorGraphicWithXZeroAtColumnZero() {
        val line = Graphic.Line(x = 0, y = 0, width = 10, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val node = Node(
            index = NodeIndex(0),
            name = "Deco",
            kind = NodeKind.TEXT,
            windowTitle = null,
            graphics = listOf(line),
            crossReferences = emptyList(),
            dataBlocks = emptyList(),
            objectTable = emptyList(),
            lines = listOf(Line(listOf(Span("row", TextStyle.Normal)))),
        )
        val document = HypDocument(
            header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
            extendedHeaders = emptyList(),
            entries = emptyList(),
            charset = HypCharset.Default,
            nodes = listOf(node),
            images = emptyList(),
            diagnostics = emptyList(),
        )

        val html = HtmlRenderer().render(document)

        assertTrue(
            html.contains("<div style=\"position:absolute;z-index:1;top:0em;left:0ch\">"),
            html,
        )
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
        // max-width is the node's own text column ("caption", 7 cells) — an image's pixel size says
        // nothing about how many character cells it may occupy.
        assertTrue(
            html.contains(
                "<img width=\"2\" height=\"2\" style=\"width:auto;height:auto;max-width:7ch\" src=\"$expectedDataUri\">",
            ),
            html,
        )
        assertTrue(html.contains("<p style=\"margin:0\">caption</p>"))
    }

    // --- Popups (bug 8) and external refs (bug 9) ---

    private fun entry(type: Int, name: String) =
        IndexEntry(len = 0, type = type, seek = 0, compDiff = 0, next = 0, prev = 0, toc = 0, name = name, compressedLength = 0)

    private fun textNode(index: Int, name: String, spans: List<Span>) = Node(
        index = NodeIndex(index),
        name = name,
        kind = NodeKind.TEXT,
        windowTitle = null,
        graphics = emptyList(),
        crossReferences = emptyList(),
        dataBlocks = emptyList(),
        objectTable = emptyList(),
        lines = listOf(Line(spans)),
    )

    private fun popupNode(index: Int, name: String, text: String) = Node(
        index = NodeIndex(index),
        name = name,
        kind = NodeKind.POPUP,
        windowTitle = null,
        graphics = emptyList(),
        crossReferences = emptyList(),
        dataBlocks = emptyList(),
        objectTable = emptyList(),
        lines = listOf(Line(listOf(Span(text, TextStyle.Normal)))),
    )

    private fun document(entries: List<IndexEntry>, nodes: List<Node>) = HypDocument(
        header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
        extendedHeaders = emptyList(),
        entries = entries,
        charset = HypCharset.Default,
        nodes = nodes,
        images = emptyList(),
        diagnostics = emptyList(),
    )

    private fun link(target: Int) = Link(kind = LinkKind.LINK, target = NodeIndex(target), lineNumber = null, label = "l")

    @Test
    fun aPopupNodeIsADialogRatherThanAPageSection() {
        val doc = document(
            listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_POPUP, "Pop")),
            listOf(
                textNode(0, "Home", listOf(Span("see", TextStyle.Normal, link(1)))),
                popupNode(1, "Pop", "the popup body"),
            ),
        )

        val html = HtmlRenderer().render(doc)

        assertTrue(html.contains("<dialog id=\"popup-1\">"), html)
        assertTrue(html.contains("the popup body"), html)
        // It must not also appear as an ordinary page section, which is exactly the reported bug.
        assertTrue(!html.contains("<h2 id=\"1\">"), html)
    }

    @Test
    fun aLinkToAPopupOpensItsDialogInsteadOfJumpingToAFragment() {
        val doc = document(
            listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_POPUP, "Pop")),
            listOf(
                textNode(0, "Home", listOf(Span("see", TextStyle.Normal, link(1)))),
                popupNode(1, "Pop", "body"),
            ),
        )

        val html = HtmlRenderer().render(doc)

        assertTrue(
            html.contains(
                "<a href=\"#\" onclick=\"document.getElementById('popup-1').showModal();return false;\">see</a>",
            ),
            html,
        )
        assertTrue(!html.contains("href=\"#1\""), html)
    }

    @Test
    fun anOrdinaryNodeLinkIsStillAPlainFragment() {
        val doc = document(
            listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_INTERNAL, "Next")),
            listOf(
                textNode(0, "Home", listOf(Span("go", TextStyle.Normal, link(1)))),
                textNode(1, "Next", listOf(Span("here", TextStyle.Normal))),
            ),
        )

        val html = HtmlRenderer().render(doc)

        assertTrue(html.contains("<a href=\"#1\">go</a>"), html)
        assertTrue(html.contains("<h2 id=\"1\">Next</h2>"), html)
        assertTrue(!html.contains("showModal"), html)
    }

    @Test
    fun anExternalRefLinkOpensAStubDialogInsteadOfADeadFragment() {
        val doc = document(
            listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_EXTERNAL_REF, "reflink.hyp/Main")),
            listOf(textNode(0, "Home", listOf(Span("RefLink", TextStyle.Normal, link(1))))),
        )

        val html = HtmlRenderer().render(doc)

        assertTrue(
            html.contains(
                "<a href=\"#\" onclick=\"document.getElementById('popup-1').showModal();return false;\">RefLink</a>",
            ),
            html,
        )
        assertTrue(html.contains("<dialog id=\"popup-1\">"), html)
        assertTrue(html.contains("External reference — not included in this document: reflink.hyp/Main"), html)
        assertTrue(!html.contains("href=\"#1\""), html)
    }

    @Test
    fun anExternalRefsNameIsEscapedInsideItsDialog() {
        val doc = document(
            listOf(
                entry(IndexEntry.TYPE_INTERNAL, "Home"),
                entry(IndexEntry.TYPE_EXTERNAL_REF, "<script>alert(1)</script>&evil/x"),
            ),
            listOf(textNode(0, "Home", listOf(Span("bad", TextStyle.Normal, link(1))))),
        )

        val html = HtmlRenderer().render(doc)

        assertTrue(!html.contains("<script>"), html)
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;&amp;evil/x"), html)
    }
}
