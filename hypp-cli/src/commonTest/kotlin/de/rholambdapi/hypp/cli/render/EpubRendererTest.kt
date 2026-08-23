package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.ExtendedHeader
import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypColor
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.ImageNode
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Link
import de.rholambdapi.hypp.LinkKind
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubRendererTest {
    // Same default colours as TextStyle.Normal (black on white), so setting only the attribute
    // bits below doesn't accidentally also trigger HtmlSpans' colour-span wrapping.
    private fun style(attrBits: Int) = TextStyle(attrBits or (HypColor.BLACK.ordinal shl 8) or (HypColor.WHITE.ordinal shl 12))
    private val bold = 1

    private fun node(
        index: Int,
        name: String,
        lines: List<Line> = emptyList(),
        graphics: List<Graphic> = emptyList(),
    ) = Node(
        index = NodeIndex(index),
        name = name,
        kind = NodeKind.TEXT,
        windowTitle = null,
        graphics = graphics,
        crossReferences = emptyList(),
        dataBlocks = emptyList(),
        objectTable = emptyList(),
        lines = lines,
    )

    private fun document(
        nodes: List<Node>,
        images: List<ImageNode> = emptyList(),
        extendedHeaders: List<ExtendedHeader> = emptyList(),
    ) = HypDocument(
        header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
        extendedHeaders = extendedHeaders,
        entries = emptyList(),
        charset = HypCharset.Default,
        nodes = nodes,
        images = images,
        diagnostics = emptyList(),
    )

    private fun image(index: Int, width: Int = 2, height: Int = 2) = ImageNode(
        index = NodeIndex(index),
        name = "pic",
        width = width,
        height = height,
        planeCount = 1,
        planePresent = 1,
        planeFilled = 0,
        planeData = byteArrayOf(0xC0.toByte(), 0, 0xC0.toByte(), 0),
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
        assertTrue(xhtml.contains("<p style=\"margin:0\"><b>bold</b></p>"), xhtml)
    }

    @Test
    fun internalLinkCrossesToTheTargetNodesOwnFile() {
        val link = Link(kind = LinkKind.LINK, target = NodeIndex(1), lineNumber = null, label = "Next")
        val doc = document(
            listOf(
                node(0, "Home", listOf(Line(listOf(Span("Next", TextStyle.Normal, link))))),
                node(1, "Second"),
            ),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(xhtml.contains("<a href=\"node-1.xhtml\">Next</a>"), xhtml)
        assertFalse(xhtml.contains("href=\"#1\""), "link must not be a same-page fragment: $xhtml")
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

    @Test
    fun embedsReferencedImageAsSeparateFileWithManifestEntry() {
        val pic = image(1)
        val graphic = Graphic.Image(pic.index, x = 1, y = 0, width = 0, height = 0, ditherMask = null)
        val doc = document(listOf(node(0, "Home", graphics = listOf(graphic))), images = listOf(pic))

        val files = EpubRenderer().render(doc)

        val imageFile = files.single { it.path == "OEBPS/images/img-1.png" }
        assertEquals(StoredPngEncoder.encode(pic).toList(), imageFile.bytes.toList())

        val xhtml = files.single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()
        assertTrue(xhtml.contains("<img width=\"2\" height=\"2\" src=\"images/img-1.png\"/>"), xhtml)

        val opf = files.single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()
        assertTrue(opf.contains("""<item id="img-1" href="images/img-1.png" media-type="image/png"/>"""))
    }

    @Test
    fun dedupesTheSameImageReferencedFromTwoNodes() {
        val pic = image(1)
        val graphic = Graphic.Image(pic.index, x = 1, y = 0, width = 0, height = 0, ditherMask = null)
        val doc = document(
            listOf(node(0, "Home", graphics = listOf(graphic)), node(1, "Again", graphics = listOf(graphic))),
            images = listOf(pic),
        )

        val files = EpubRenderer().render(doc)

        assertEquals(1, files.count { it.path == "OEBPS/images/img-1.png" })
    }

    @Test
    fun unreferencedImagesAreNotEmbedded() {
        val doc = document(listOf(node(0, "Home")), images = listOf(image(1)))

        val files = EpubRenderer().render(doc)

        assertFalse(files.any { it.path.startsWith("OEBPS/images/") })
        val opf = files.single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()
        assertFalse(opf.contains("img-1"))
    }

    @Test
    fun lineGraphicIsPositionedAtItsRealXAndY() {
        val rule = Graphic.Line(x = 5, y = 1, width = 40, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val doc = document(
            listOf(
                node(
                    0,
                    "Introduction",
                    lines = listOf(
                        Line(listOf(Span("Title", TextStyle.Normal))),
                        Line(listOf(Span("But why hypertext?", TextStyle.Normal))),
                    ),
                    graphics = listOf(rule),
                ),
            ),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(xhtml.contains("<div style=\"position:absolute;z-index:1;top:1em;left:5ch\">"), xhtml)
    }

    @Test
    fun lineGraphicWithXZeroIsCentered() {
        val rule = Graphic.Line(x = 0, y = 1, width = 40, height = 1, arrowAtStart = true, arrowAtEnd = false, lineStyle = 0)
        val doc = document(
            listOf(
                node(
                    0,
                    "Introduction",
                    lines = listOf(Line(listOf(Span("Title", TextStyle.Normal))), Line(emptyList())),
                    graphics = listOf(rule),
                ),
            ),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(
            xhtml.contains("<div style=\"position:absolute;z-index:1;top:1em;left:50%;transform:translateX(-50%)\">"),
            xhtml,
        )
    }

    @Test
    fun consecutiveTextRowsShareOneParagraphSeparatedByNewline() {
        val doc = document(
            listOf(
                node(
                    0,
                    "Home",
                    lines = listOf(
                        Line(listOf(Span("First", TextStyle.Normal))),
                        Line(listOf(Span("Second", TextStyle.Normal))),
                    ),
                ),
            ),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(xhtml.contains("<p style=\"margin:0\">First\nSecond</p>"), xhtml)
    }

    @Test
    fun controlCharacterInNodeTextDoesNotBreakXhtml() {
        val doc = document(
            listOf(node(0, "Home", listOf(Line(listOf(Span("Control - ${Char(0x03)} !", TextStyle.Normal)))))),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertFalse(xhtml.contains(Char(0x03).toString()), xhtml)
        assertTrue(xhtml.contains("Control - ${Char(0x2403)} !"), xhtml)
    }

    @Test
    fun titleAndAuthorAreDerivedFromTheDocumentsExtendedHeaders() {
        val doc = document(
            listOf(node(0, "Home")),
            extendedHeaders = listOf(ExtendedHeader.Database("My Book"), ExtendedHeader.Author("Jane & Doe")),
        )

        val opf = EpubRenderer().render(doc).single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()

        assertTrue(opf.contains("<dc:title>My Book</dc:title>"), opf)
        assertTrue(opf.contains("<dc:creator>Jane &amp; Doe</dc:creator>"), opf)
    }

    @Test
    fun missingTitleHeaderFallsBackToTheFirstNodesName() {
        val doc = document(listOf(node(0, "Main Page")))

        val opf = EpubRenderer().render(doc).single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()

        assertTrue(opf.contains("<dc:title>Main Page</dc:title>"), opf)
    }

    @Test
    fun missingAuthorHeaderOmitsDcCreator() {
        val doc = document(listOf(node(0, "Home")))

        val opf = EpubRenderer().render(doc).single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()

        assertFalse(opf.contains("dc:creator"), opf)
    }

    @Test
    fun emptyDocumentFallsBackToAGenericTitle() {
        val opf = EpubRenderer().render(document(emptyList())).single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()

        assertTrue(opf.contains("<dc:title>hypp export</dc:title>"), opf)
    }
}
