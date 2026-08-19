package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypColor
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubRendererTest {
    // Same default colours as TextStyle.Normal (black on white), so setting only the attribute
    // bits below doesn't accidentally also trigger HtmlSpans' colour-span wrapping.
    private fun style(attrBits: Int) = TextStyle(attrBits or (HypColor.BLACK.ordinal shl 8) or (HypColor.WHITE.ordinal shl 12))
    private val bold = 1

    private fun node(index: Int, name: String, lines: List<Line> = emptyList()) = Node(
        index = NodeIndex(index),
        name = name,
        kind = NodeKind.TEXT,
        windowTitle = null,
        graphics = emptyList(),
        crossReferences = emptyList(),
        dataBlocks = emptyList(),
        objectTable = emptyList(),
        lines = lines,
    )

    private fun document(nodes: List<Node>) = HypDocument(
        header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
        extendedHeaders = emptyList(),
        entries = emptyList(),
        charset = HypCharset.Default,
        nodes = nodes,
        images = emptyList(),
        diagnostics = emptyList(),
    )

    @Test
    fun producesExpectedManifest() {
        val doc = document(
            listOf(
                node(0, "Home", listOf(Line(listOf(Span("hi", TextStyle.Normal))))),
                node(1, "A & B", listOf(Line(listOf(Span("bold", style(bold)))))),
            ),
        )

        val files = EpubRenderer().render(doc)

        assertEquals(
            setOf(
                "mimetype",
                "META-INF/container.xml",
                "OEBPS/content.opf",
                "OEBPS/nav.xhtml",
                "OEBPS/node-0.xhtml",
                "OEBPS/node-1.xhtml",
            ),
            files.map { it.path }.toSet(),
        )
        assertEquals(6, files.size)
    }

    @Test
    fun mimetypeIsStoredLiteral() {
        val mimetype = EpubRenderer().render(document(emptyList())).single { it.path == "mimetype" }
        assertEquals("application/epub+zip", mimetype.bytes.decodeToString())
    }

    @Test
    fun containerXmlPointsAtPackageDocument() {
        val container = EpubRenderer().render(document(emptyList()))
            .single { it.path == "META-INF/container.xml" }.bytes.decodeToString()
        assertTrue(container.contains("""full-path="OEBPS/content.opf""""))
    }

    @Test
    fun nodeXhtmlHasEscapedNameAndRenderedSpans() {
        val doc = document(listOf(node(0, "A & B", listOf(Line(listOf(Span("bold", style(bold))))))))

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(xhtml.contains("<title>A &amp; B</title>"), xhtml)
        assertTrue(xhtml.contains("<h1>A &amp; B</h1>"), xhtml)
        assertTrue(xhtml.contains("<p><b>bold</b></p>"), xhtml)
    }

    @Test
    fun navListsEveryNode() {
        val doc = document(listOf(node(0, "Home"), node(1, "A & B")))

        val nav = EpubRenderer().render(doc).single { it.path == "OEBPS/nav.xhtml" }.bytes.decodeToString()

        assertTrue(nav.contains("<li><a href=\"node-0.xhtml\">Home</a></li>"))
        assertTrue(nav.contains("<li><a href=\"node-1.xhtml\">A &amp; B</a></li>"))
    }

    @Test
    fun opfManifestAndSpineListEveryNodeInDocumentOrder() {
        val doc = document(listOf(node(0, "Home"), node(1, "Second")))

        val opf = EpubRenderer().render(doc).single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()

        assertTrue(opf.contains("""<item id="node-0" href="node-0.xhtml" media-type="application/xhtml+xml"/>"""))
        assertTrue(opf.contains("""<item id="node-1" href="node-1.xhtml" media-type="application/xhtml+xml"/>"""))
        assertTrue(opf.contains("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""))

        val spineOrder = Regex("itemref idref=\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
        assertEquals(listOf("node-0", "node-1"), spineOrder)
    }
}
