package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Span-by-span assertions for node data item f (text with attributes). Every expected value
 * here was derived by hex-inspecting the decompressed node bytes of the named fixture against
 * the prose spec (`hypfmt.ui`, item f), not from what the parser produces.
 */
class TextTest {
    private fun open(bytes: ByteArray): HypDocument {
        val outcome = HypDocument.open(bytes)
        assertIs<OpenOutcome.Success>(outcome)
        return outcome.document
    }

    private fun style(attributes: Int = 0, fg: HypColor = HypColor.BLACK, bg: HypColor = HypColor.WHITE) =
        TextStyle.Normal.withAttributes(attributes).withForeground(fg).withBackground(bg)

    private fun plain(text: String, attributes: Int = 0) = Span(text, style(attributes))

    // ---- textattr.hyp: the absolute attribute bit-vector, escapes 0x64-0xa3 ----

    @Test
    fun textattrHypAssignsAbsoluteAttributeVectorsSpanBySpan() {
        val main = open(TestCorpus.textattr).nodes.single()
        assertEquals(9, main.lines.size)

        assertEquals(listOf(plain("Dies ist normaler Text.")), main.lines[0].spans)
        // Escape 0x66 = 102, minus 100 -> bit 2 (ghosted/light); 0x64 = 100 -> back to no attributes.
        assertEquals(
            listOf(plain("Dies ist "), plain("heller", 2), plain(" Text.")),
            main.lines[1].spans,
        )
        assertEquals(
            listOf(plain("Dies ist "), plain("fetter", 1), plain(" Text.")),
            main.lines[2].spans,
        )
        assertEquals(
            listOf(plain("Dies ist "), plain("unterstrichener", 8), plain(" Text.")),
            main.lines[3].spans,
        )
        // An attribute escape at the start of a line: the leading indent is its own plain span.
        assertEquals(
            listOf(plain("    "), plain("Unterstrichener", 8), plain(" Text am Zeilenanfang.")),
            main.lines[4].spans,
        )
        // Attribute runs preserve their surrounding whitespace exactly.
        assertEquals(
            listOf(plain("Dies ist "), plain("  auch   unterstrichener  ", 8), plain(" Text.")),
            main.lines[5].spans,
        )
        assertEquals(
            listOf(plain("Dies ist "), plain("kursiver", 4), plain(" Text.")),
            main.lines[6].spans,
        )
        assertEquals(
            listOf(plain("Dies ist "), plain("umrandeter", 16), plain(" Text.")),
            main.lines[7].spans,
        )
        assertEquals(
            listOf(plain("Dies ist "), plain("schattierter", 32), plain(" Text.")),
            main.lines[8].spans,
        )
    }

    @Test
    fun textStyleExposesEachAttributeBit() {
        val main = open(TestCorpus.textattr).nodes.single()
        fun styled(line: Int) = main.lines[line].spans[1].style
        assertTrue(styled(1).isLight)
        assertTrue(styled(2).isBold)
        assertTrue(styled(3).isUnderlined)
        assertTrue(styled(6).isItalic)
        assertTrue(styled(7).isOutlined)
        assertTrue(styled(8).isShadowed)
        assertEquals(TextStyle.Normal, main.lines[0].spans.single().style)
        assertTrue(main.lines[0].spans.single().style.let { !it.isBold && !it.isItalic })
    }

    // ---- colors.hyp: escapes 0xa5 / 0xa6, each with one raw palette-index byte ----

    @Test
    fun colorsHypAssignsForegroundAndBackgroundFromASingleRawPaletteByte() {
        val main = open(TestCorpus.colors).nodes.single()
        // 17 lines, not the 19 a NUL-only split yields: the fg/bg parameter byte for colour
        // index 0 (white) *is* 0x00 and must be consumed as part of the escape. See
        // doc/format-notes.md.
        assertEquals(17, main.lines.size)

        assertEquals(
            listOf(plain("hello "), Span("white world", style(fg = HypColor.WHITE, bg = HypColor.BLACK))),
            main.lines[0].spans,
        )
        assertEquals(listOf(plain("hello black world")), main.lines[1].spans)

        val expectedColors = listOf(
            "red" to HypColor.RED,
            "green" to HypColor.GREEN,
            "blue" to HypColor.BLUE,
            "cyan" to HypColor.CYAN,
            "yellow" to HypColor.YELLOW,
            "magenta" to HypColor.MAGENTA,
            "light gray" to HypColor.LIGHT_GRAY,
            "dark gray" to HypColor.DARK_GRAY,
            "dark red" to HypColor.DARK_RED,
            "dark green" to HypColor.DARK_GREEN,
            "dark blue" to HypColor.DARK_BLUE,
            "dark cyan" to HypColor.DARK_CYAN,
            "dark yellow" to HypColor.DARK_YELLOW,
            "dark magenta" to HypColor.DARK_MAGENTA,
        )
        expectedColors.forEachIndexed { i, (name, color) ->
            assertEquals(
                listOf(plain("hello "), Span("$name world", style(fg = color))),
                main.lines[i + 2].spans,
                "line ${i + 2}",
            )
        }
        assertEquals(Line(emptyList()), main.lines[16])
    }

    @Test
    fun hypColorIndexOrderMatchesTheFormatsPalette() {
        assertEquals(
            listOf(
                HypColor.WHITE, HypColor.BLACK, HypColor.RED, HypColor.GREEN,
                HypColor.BLUE, HypColor.CYAN, HypColor.YELLOW, HypColor.MAGENTA,
                HypColor.LIGHT_GRAY, HypColor.DARK_GRAY, HypColor.DARK_RED, HypColor.DARK_GREEN,
                HypColor.DARK_BLUE, HypColor.DARK_CYAN, HypColor.DARK_YELLOW, HypColor.DARK_MAGENTA,
            ),
            HypColor.entries.toList(),
        )
        assertEquals(HypColor.BLACK, TextStyle.Normal.foreground)
        assertEquals(HypColor.WHITE, TextStyle.Normal.background)
    }

    // ---- linkattr.hyp: escape 0x24 (link), the 32 + n label rule ----

    @Test
    fun linkattrHypResolvesEveryLinkTargetAndLabel() {
        val doc = open(TestCorpus.linkattr)
        val main = doc.nodes.first { it.name == "Main" }
        assertEquals(8, main.lines.size)

        fun link(line: Int) = main.lines[line].spans.single { it.link != null }

        assertEquals(
            listOf(plain("Link to "), Span("internal Page", style(), Link(LinkKind.LINK, NodeIndex(1), null, "internal Page"))),
            main.lines[0].spans,
        )
        assertEquals(Link(LinkKind.LINK, NodeIndex(3), null, "Popup"), link(1).link)
        assertEquals(Link(LinkKind.LINK, NodeIndex(4), null, "external Page"), link(2).link)
        assertEquals(Link(LinkKind.LINK, NodeIndex(5), null, "hello"), link(3).link)
        assertEquals(Link(LinkKind.LINK, NodeIndex(6), null, "REXX command"), link(4).link)
        assertEquals(Link(LinkKind.LINK, NodeIndex(7), null, "REXX script"), link(5).link)
        // A link at the very start of a line, with plain text after it.
        assertEquals(
            listOf(
                Span("Close", style(), Link(LinkKind.LINK, NodeIndex(8), null, "Close")),
                plain(" this window"),
            ),
            main.lines[6].spans,
        )
        assertEquals(
            listOf(
                Span("Exit", style(), Link(LinkKind.LINK, NodeIndex(9), null, "Exit")),
                plain(" quit application"),
            ),
            main.lines[7].spans,
        )

        // The link targets cover every entry type the format has: internal, popup, external
        // reference, system, rexx command/script, close and quit.
        assertEquals(
            listOf(0, 1, 2, 4, 6, 5, 8, 7),
            main.lines.map { doc.entries[it.spans.first { s -> s.link != null }.link!!.target.value].type },
        )
    }

    @Test
    fun linkattrHypStoresItsShortNodesUncompressed() {
        // compDiff == 0, i.e. uncompressed size == compressed size: the object is stored raw,
        // not as an lh5 stream. See doc/format-notes.md.
        val doc = open(TestCorpus.linkattr)
        assertEquals(emptyList(), doc.diagnostics)
        assertEquals(
            listOf("Dies ist Seite 1", "Dies ist Seite 2", "This is a popup."),
            doc.nodes.drop(1).map { it.lines.single().text },
        )
    }

    // ---- hcp_orig_en.hyp: the "length byte == 32 -> use the target's own name" rule ----

    @Test
    fun aLinkLabelLengthOfExactlyThirtyTwoUsesTheTargetNodesName() {
        val doc = open(TestCorpus.hcpOrigEn)
        val main = doc.nodes.first()
        val links = main.lines.flatMap { line -> line.spans.mapNotNull { it.link } }
        assertEquals(
            listOf(
                Link(LinkKind.LINK, NodeIndex(4), null, "Calling HCP"),
                Link(LinkKind.LINK, NodeIndex(5), null, "Options overview"),
                Link(LinkKind.LINK, NodeIndex(39), null, "Commands in a hypertext"),
                Link(LinkKind.LINK, NodeIndex(31), null, "Tasks and properties of the compiler"),
                Link(LinkKind.LINK, NodeIndex(2), null, "Writing a hypertext"),
                Link(LinkKind.LINK, NodeIndex(32), null, "Technical"),
                Link(LinkKind.LINK, NodeIndex(3), null, "File-types"),
                Link(LinkKind.LINK, NodeIndex(120), null, "Legal"),
                Link(LinkKind.LINK, NodeIndex(119), null, "Credits"),
            ),
            links,
        )
        // A link's label is also its span's text, and a link never straddles a style change.
        assertTrue(links.all { l -> main.lines.any { line -> line.spans.any { it.link == l && it.text == l.label } } })
    }

    @Test
    fun aRealDocumentProducesNoUnknownEscapesOrUnterminatedLines() {
        for (bytes in listOf(TestCorpus.hcpOrigEn, TestCorpus.stGuideOrigEn)) {
            val doc = open(bytes)
            assertEquals(emptyList(), doc.diagnostics.filterIsInstance<Diagnostic.UnknownEscape>())
            assertEquals(emptyList(), doc.diagnostics.filterIsInstance<Diagnostic.UnterminatedLine>())
            assertEquals(emptyList(), doc.diagnostics.filterIsInstance<Diagnostic.DanglingNodeReference>())
        }
    }

    // ---- hand-constructed bytes for the cases no vendored fixture exercises ----

    @Test
    fun escEscEmitsALiteralEscapeInsideTheSurroundingRun() {
        val node = parseNode(NodeIndex(0), "n", NodeKind.TEXT, byteArrayOf(0x61, 0x1b, 0x1b, 0x62, 0x00), ArrayList())
        assertEquals(listOf(plain("a\u001bb")), node.lines.single().spans)
    }

    @Test
    fun escape164HasNoVisualEffectAndIsNotADiagnostic() {
        val diagnostics = ArrayList<Diagnostic>()
        val node = parseNode(
            NodeIndex(0), "n", NodeKind.TEXT,
            byteArrayOf(0x61, 0x1b, 0xa4.toByte(), 0x62, 0x00), diagnostics,
        )
        assertEquals(listOf(plain("ab")), node.lines.single().spans)
        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun linkWithLineNumberCarriesABase255LineNumber() {
        val diagnostics = ArrayList<Diagnostic>()
        // ESC 0x27 (alink + line), line = (2-1)*255 + (4-1) = 258, target = 1, length = 32 + 2.
        val data = byteArrayOf(0x1b, 0x27, 0x04, 0x02, 0x02, 0x01, 0x22, 0x68, 0x69, 0x00)
        val node = parseNode(
            NodeIndex(0), "n", NodeKind.TEXT, data, diagnostics,
            entryNames = listOf("Main", "Target"),
        )
        assertEquals(
            Span("hi", TextStyle.Normal, Link(LinkKind.ALINK, NodeIndex(1), 258, "hi")),
            node.lines.single().spans.single(),
        )
        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun aLinkToAnIndexOutsideTheTableIsDiagnosedAndDroppedRatherThanThrowing() {
        val diagnostics = ArrayList<Diagnostic>()
        // ESC 0x24, target = (1-1)*255 + (100-1) = 99, label length = 32 + 1.
        val data = byteArrayOf(0x1b, 0x24, 0x64, 0x01, 0x21, 0x78, 0x00)
        val node = parseNode(NodeIndex(0), "n", NodeKind.TEXT, data, diagnostics, entryNames = listOf("Main"))
        val span = node.lines.single().spans.single()
        assertEquals("x", span.text)
        assertNull(span.link)
        assertEquals(listOf<Diagnostic>(Diagnostic.DanglingNodeReference(NodeIndex(0), 99)), diagnostics)
    }

    @Test
    fun anUnknownTextEscapeIsDiagnosedAndSkipped() {
        val diagnostics = ArrayList<Diagnostic>()
        val node = parseNode(
            NodeIndex(0), "n", NodeKind.TEXT,
            byteArrayOf(0x61, 0x1b, 0x0a, 0x62, 0x00), diagnostics,
        )
        assertEquals(listOf(plain("ab")), node.lines.single().spans)
        assertEquals(listOf<Diagnostic>(Diagnostic.UnknownEscape(NodeIndex(0), 0x0a)), diagnostics)
    }

    @Test
    fun aFinalLineWithoutItsNulTerminatorIsKeptAndDiagnosed() {
        val diagnostics = ArrayList<Diagnostic>()
        val node = parseNode(NodeIndex(0), "n", NodeKind.TEXT, byteArrayOf(0x61, 0x00, 0x62), diagnostics)
        assertEquals(listOf("a", "b"), node.lines.map { it.text })
        assertEquals(listOf<Diagnostic>(Diagnostic.UnterminatedLine(NodeIndex(0))), diagnostics)
    }

    @Test
    fun textIsDecodedThroughTheDocumentsCharset() {
        // Byte 0xe1 is 'β' in the Atari ST character set and 'á' in Latin-1.
        val data = byteArrayOf(0xe1.toByte(), 0x00)
        fun textWith(charset: HypCharset) =
            parseNode(NodeIndex(0), "n", NodeKind.TEXT, data, ArrayList(), charset = charset).lines.single().text
        assertEquals("β", textWith(HypCharset.AtariSt))
        assertEquals("á", textWith(HypCharset.Latin1))
    }
}
