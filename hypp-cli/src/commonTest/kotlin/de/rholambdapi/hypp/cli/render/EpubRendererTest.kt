package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.ExtendedHeader
import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypColor
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

    private fun entry(type: Int, name: String) =
        IndexEntry(len = 0, type = type, seek = 0, compDiff = 0, next = 0, prev = 0, toc = 0, name = name, compressedLength = 0)

    private fun document(
        nodes: List<Node>,
        images: List<ImageNode> = emptyList(),
        extendedHeaders: List<ExtendedHeader> = emptyList(),
        entries: List<IndexEntry> = emptyList(),
    ) = HypDocument(
        header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
        extendedHeaders = extendedHeaders,
        entries = entries,
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
    fun imageWithXZeroIsCenteredOnTheNodesTextColumn() {
        val pic = image(1)
        val centred = Graphic.Image(pic.index, x = 0, y = 1, width = 0, height = 0, ditherMask = null)
        val doc = document(
            listOf(
                node(
                    0,
                    "Introduction",
                    lines = listOf(Line(listOf(Span("Title text", TextStyle.Normal))), Line(emptyList())),
                    graphics = listOf(centred),
                ),
            ),
            images = listOf(pic),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(
            xhtml.contains(
                "<div style=\"position:absolute;z-index:1;top:1em;left:calc(10ch / 2);transform:translateX(-50%)\">",
            ),
            xhtml,
        )
        assertTrue(xhtml.contains("style=\"width:auto;height:auto;max-width:10ch\""), xhtml)
    }

    @Test
    fun lineGraphicWithXZeroSitsAtColumnZero() {
        // The format's centring signal is `x == 0` on an image; @line/@box/@rbox carry x in 1-255,
        // so a zero there is a column, not a centring request.
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

        assertTrue(xhtml.contains("<div style=\"position:absolute;z-index:1;top:1em;left:0ch\">"), xhtml)
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

    // --- Popups (bug 8) and external refs (bug 9) ---

    private fun popupDocument() = document(
        nodes = listOf(
            node(0, "Home", listOf(Line(listOf(Span("see", TextStyle.Normal, Link(LinkKind.LINK, NodeIndex(1), null, "l")))))),
            popupNode(1, "Pop", "the popup body"),
        ),
        entries = listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_POPUP, "Pop")),
    )

    @Test
    fun aPopupNodeGetsNoPageFileNavEntryOrSpineEntry() {
        val files = EpubRenderer().render(popupDocument())

        assertFalse(files.any { it.path == "OEBPS/node-1.xhtml" }, files.map { it.path }.toString())

        val nav = files.single { it.path == "OEBPS/nav.xhtml" }.bytes.decodeToString()
        assertFalse(nav.contains("node-1.xhtml"), nav)

        val opf = files.single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()
        assertFalse(opf.contains("node-1"), opf)
    }

    @Test
    fun aLinkToAPopupInlinesItsContentAsADisclosure() {
        val xhtml = EpubRenderer().render(popupDocument())
            .single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(xhtml.contains("<details><summary>see</summary>"), xhtml)
        assertTrue(xhtml.contains("the popup body"), xhtml)
        assertFalse(xhtml.contains("node-1.xhtml"), xhtml)
    }

    @Test
    fun popupContentInsideADisclosureIsEscapedExactlyOnce() {
        val doc = document(
            nodes = listOf(
                node(0, "Home", listOf(Line(listOf(Span("see", TextStyle.Normal, Link(LinkKind.LINK, NodeIndex(1), null, "l")))))),
                popupNode(1, "Pop", "a & b"),
            ),
            entries = listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_POPUP, "Pop")),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(xhtml.contains("a &amp; b"), xhtml)
        assertFalse(xhtml.contains("&amp;amp;"), xhtml)
    }

    @Test
    fun anExternalRefLinkInlinesAStubInsteadOfPointingAtAMissingFile() {
        val doc = document(
            nodes = listOf(
                node(
                    0,
                    "Home",
                    listOf(Line(listOf(Span("RefLink", TextStyle.Normal, Link(LinkKind.LINK, NodeIndex(1), null, "l"))))),
                ),
            ),
            entries = listOf(entry(IndexEntry.TYPE_INTERNAL, "Home"), entry(IndexEntry.TYPE_EXTERNAL_REF, "reflink.hyp/Main")),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertTrue(
            xhtml.contains(
                "<details><summary>RefLink</summary>External reference — not included in this document: " +
                    "reflink.hyp/Main</details>",
            ),
            xhtml,
        )
        // The dead href that made Calibre report a missing referenced file.
        assertFalse(xhtml.contains("node-1.xhtml"), xhtml)
    }

    @Test
    fun anExternalRefsNameIsEscapedInsideItsDisclosure() {
        val doc = document(
            nodes = listOf(
                node(0, "Home", listOf(Line(listOf(Span("bad", TextStyle.Normal, Link(LinkKind.LINK, NodeIndex(1), null, "l")))))),
            ),
            entries = listOf(
                entry(IndexEntry.TYPE_INTERNAL, "Home"),
                entry(IndexEntry.TYPE_EXTERNAL_REF, "<script>alert(1)</script>&evil/x"),
            ),
        )

        val xhtml = EpubRenderer().render(doc).single { it.path == "OEBPS/node-0.xhtml" }.bytes.decodeToString()

        assertFalse(xhtml.contains("<script>"), xhtml)
        assertTrue(xhtml.contains("&lt;script&gt;alert(1)&lt;/script&gt;&amp;evil/x"), xhtml)
    }

    @Test
    fun emptyDocumentFallsBackToAGenericTitle() {
        val opf = EpubRenderer().render(document(emptyList())).single { it.path == "OEBPS/content.opf" }.bytes.decodeToString()

        assertTrue(opf.contains("<dc:title>hypp export</dc:title>"), opf)
    }
}
