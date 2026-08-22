package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentTest {
    private fun open(bytes: ByteArray): HypDocument {
        val outcome = HypDocument.open(bytes)
        assertIs<OpenOutcome.Success>(outcome)
        return outcome.document
    }

    @Test
    fun entryNodeAndImageAccessorsMatchEntryType() {
        val doc = open(TestCorpus.image)
        // entries[0] Main (internal), entries[1] rtr_logo.img (image)
        assertTrue(doc.node(NodeIndex(0)) != null)
        assertNull(doc.image(NodeIndex(0)), "an internal page is not an image")
        assertTrue(doc.image(NodeIndex(1)) != null)
        assertNull(doc.node(NodeIndex(1)), "an image is not a node")

        assertNull(doc.entry(NodeIndex(9999)), "out of range")
        assertNull(doc.node(NodeIndex(9999)))
        assertNull(doc.image(NodeIndex(9999)))
    }

    @Test
    fun linkToAQuitEntryResolvesViaEntryRatherThanFailing() {
        // linkattr.hyp's "Exit" link (see TextTest) targets index 9, a type-7 quit dummy entry —
        // it has no data region, so node()/image() correctly return null for it.
        val doc = open(TestCorpus.linkattr)
        val quit = NodeIndex(9)
        assertNull(doc.node(quit))
        assertNull(doc.image(quit))
        val entry = doc.entry(quit)
        assertEquals(IndexEntry.TYPE_QUIT, entry?.type)
    }

    @Test
    fun missingDefaultHeaderYieldsNullDefaultNode() {
        assertNull(open(TestCorpus.hcpOrigEn).defaultNode)
        assertNull(open(TestCorpus.stGuideOrigEn).defaultNode)
    }

    @Test
    fun defaultHeaderResolvesToTheNamedNode() {
        val bytes = buildHyp(
            entries = listOf(Entry("Main"), Entry("Page 1")),
            extendedHeaders = listOf(ExtendedHeader.Default.ID to cstring("Page 1")),
        )
        assertEquals(NodeIndex(1), open(bytes).defaultNode)
    }

    @Test
    fun danglingDefaultHeaderYieldsNullDefaultNode() {
        val bytes = buildHyp(
            entries = listOf(Entry("Main")),
            extendedHeaders = listOf(ExtendedHeader.Default.ID to cstring("Nonexistent")),
        )
        assertNull(open(bytes).defaultNode)
    }

    @Test
    fun missingTitleAndAuthorHeadersYieldNull() {
        val doc = open(TestCorpus.textattr)
        assertNull(doc.title)
        assertNull(doc.author)
    }

    @Test
    fun databaseAndAuthorHeadersResolveToTitleAndAuthor() {
        val bytes = buildHyp(
            entries = listOf(Entry("Main")),
            extendedHeaders = listOf(
                ExtendedHeader.Database.ID to cstring("My Book"),
                ExtendedHeader.Author.ID to cstring("Jane Doe"),
            ),
        )
        val doc = open(bytes)
        assertEquals("My Book", doc.title)
        assertEquals("Jane Doe", doc.author)
    }

    @Test
    fun stGuideTitleAndAuthorMatchItsRealExtendedHeaders() {
        val doc = open(TestCorpus.stGuideOrigEn)
        assertEquals("ST-Guide Documentation", doc.title)
        assertTrue(doc.author?.startsWith("H.Weets,C.Wempe,V.Burggr") == true)
        assertTrue(doc.author?.endsWith("f & P.West") == true)
    }

    @Test
    fun stGuideTableOfContentsNestsSymbolBarAndItsExtraPopupSubgroup() {
        // Ground truth from the real index table (see doc/format-notes.md / session notes): index 5
        // "Symbol bar" groups indices 16..28 via @toc, and within that, index 27 "Extra popup"
        // further groups indices 29..36.
        val doc = open(TestCorpus.stGuideOrigEn)
        val toc = doc.tableOfContents()
        assertEquals(NodeIndex(0), toc.index)

        val symbolBar = toc.children.first { it.index == NodeIndex(5) }
        assertEquals((16..28).map(::NodeIndex), symbolBar.children.map { it.index })

        val extraPopup = symbolBar.children.first { it.index == NodeIndex(27) }
        assertEquals((29..36).map(::NodeIndex), extraPopup.children.map { it.index })

        assertEquals(emptyList(), symbolBar.children.first { it.index == NodeIndex(16) }.children, "a leaf nests nothing")
    }

    @Test
    fun tableOfContentsBreaksACycleAwayFromTheRootWithoutLooping() {
        // index1 and index2 point @toc at each other, never at the root — a malformed/hostile
        // input the tree-builder must not recurse into forever.
        val bytes = buildHyp(
            entries = listOf(Entry("Main"), Entry("A", toc = 2), Entry("B", toc = 1), Entry("C")),
        )
        val toc = open(bytes).tableOfContents()
        assertEquals(listOf(NodeIndex(3)), toc.children.map { it.index }, "the cyclic pair is orphaned, not looped into")
    }

    // ---- minimal synthetic .hyp builder: raw (uncompressed) empty-bodied internal pages ----

    private data class Entry(val name: String, val toc: Int = 0)

    private fun cstring(s: String): ByteArray = s.map { it.code.toByte() }.toByteArray() + byteArrayOf(0)

    private fun buildHyp(entries: List<Entry>, extendedHeaders: List<Pair<Int, ByteArray>> = emptyList()): ByteArray {
        val out = ArrayList<Byte>()
        fun u8(v: Int) = out.add(v.toByte())
        fun u16(v: Int) { u8(v ushr 8); u8(v) }
        fun u32(v: Int) { u16(v ushr 16); u16(v) }
        fun bytes(b: ByteArray) = out.addAll(b.toList())

        val entryBytes = entries.map { e ->
            val name = cstring(e.name)
            val body = ArrayList<Byte>()
            fun b8(v: Int) = body.add(v.toByte())
            fun b16(v: Int) { b8(v ushr 8); b8(v) }
            fun b32(v: Int) { b16(v ushr 16); b16(v) }
            b8(0) // len placeholder, patched below
            b8(IndexEntry.TYPE_INTERNAL)
            b32(0) // seek placeholder — every node body is empty, so all seeks coincide
            b16(0) // compDiff
            b16(0) // next (self, unused by tableOfContents)
            b16(0) // prev
            b16(e.toc)
            body.addAll(name.toList())
            if (body.size % 2 != 0) body.add(0)
            body[0] = body.size.toByte()
            body
        }

        val dataStart = 12 + entryBytes.sumOf { it.size } + extendedHeaders.sumOf { 4 + it.second.size } + 4
        // patch each entry's seek to dataStart (all bodies are empty, so every entry's derived
        // compressedLength is 0 regardless of the shared seek value)
        for (e in entryBytes) {
            e[2] = (dataStart ushr 24).toByte(); e[3] = (dataStart ushr 16).toByte()
            e[4] = (dataStart ushr 8).toByte(); e[5] = dataStart.toByte()
        }

        bytes("HDOC".map { it.code.toByte() }.toByteArray())
        u32(entryBytes.sumOf { it.size })
        u16(entries.size)
        u8(3) // compiler version
        u8(2) // compiler OS (Atari)
        entryBytes.forEach { bytes(it.toByteArray()) }
        for ((id, data) in extendedHeaders) {
            u16(id); u16(data.size); bytes(data)
        }
        u16(0); u16(0) // extended-header terminator

        return out.toByteArray()
    }
}
