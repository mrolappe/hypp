package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeTest {
    private fun open(bytes: ByteArray): HypDocument {
        val outcome = HypDocument.open(bytes)
        assertIs<OpenOutcome.Success>(outcome)
        return outcome.document
    }

    @Test
    fun imageHypPlacesThreeCenteredAndOffsetImages() {
        val doc = open(TestCorpus.image)
        val main = doc.nodes.single { it.name == "Main" }
        val images = main.graphics.map { assertIs<Graphic.Image>(it) }
        assertEquals(
            listOf(
                Triple(0, 1, true),
                Triple(11, 13, false),
                Triple(49, 25, false),
            ),
            images.map { Triple(it.x, it.y, it.centered) },
        )
        // "rtr_logo.img" is entries[1]; every placement references that one image.
        assertTrue(images.all { it.imageIndex == NodeIndex(1) })
        assertTrue(images.all { it.width == 0 && it.height == 0 })
        assertTrue(images.all { it.ditherMask == null })
    }

    @Test
    fun limageHypPlacesLineHeightImages() {
        val doc = open(TestCorpus.limage)
        val main = doc.nodes.single { it.name == "Main" }
        val images = main.graphics.map { assertIs<Graphic.Image>(it) }
        assertEquals(listOf(0 to 1, 11 to 2, 49 to 3), images.map { it.x to it.y })
        assertTrue(images.all { it.width == 1 })
    }

    @Test
    fun limage2HypPlacesImagesFromTwoIndexEntries() {
        val doc = open(TestCorpus.limage2)
        val main = doc.nodes.single { it.name == "Main" }
        val images = main.graphics.map { assertIs<Graphic.Image>(it) }
        assertEquals(
            listOf(
                Triple(NodeIndex(1), 1, 1),
                Triple(NodeIndex(2), 1, 1),
                Triple(NodeIndex(1), 1, 4),
                Triple(NodeIndex(2), 1, 5),
            ),
            images.map { Triple(it.imageIndex, it.x, it.y) },
        )
    }

    @Test
    fun linesHypDrawsBoxesAndLines() {
        val doc = open(TestCorpus.lines)
        val main = doc.nodes.single { it.name == "Main" }

        val boxes = main.graphics.filterIsInstance<Graphic.RoundedBox>() + main.graphics.filterIsInstance<Graphic.Box>()
        assertEquals(4, boxes.size)
        assertEquals(
            listOf(
                Graphic.RoundedBox(x = 32, y = 1, width = 10, height = 5, fillPattern = 2),
                Graphic.RoundedBox(x = 52, y = 1, width = 10, height = 5, fillPattern = 1),
            ),
            main.graphics.filterIsInstance<Graphic.RoundedBox>(),
        )
        assertEquals(
            listOf(
                Graphic.Box(x = 32, y = 7, width = 10, height = 5, fillPattern = 2),
                Graphic.Box(x = 52, y = 7, width = 10, height = 5, fillPattern = 1),
            ),
            main.graphics.filterIsInstance<Graphic.Box>(),
        )

        val lines = main.graphics.filterIsInstance<Graphic.Line>()
        assertEquals(10, lines.size)
        assertEquals(listOf(13, 15, 17, 19, 21, 23, 25, 27, 31, 37), lines.map { it.y })
        // Raw data-byte decomposition per the prose spec's bit0/bit1/rest formula. Whether these
        // flags actually correspond to this fixture's "arrow start/end/both" filename labels isn't
        // corpus-verifiable (no rendering oracle) — only the decomposition arithmetic is asserted.
        assertEquals(
            listOf(
                Triple(true, false, 0), Triple(true, false, 0),
                Triple(true, true, 0), Triple(true, true, 0),
                Triple(false, true, 0), Triple(false, true, 0),
                Triple(false, false, 1), Triple(false, false, 1),
                Triple(true, true, 0), Triple(true, true, 0),
            ),
            lines.map { Triple(it.arrowAtStart, it.arrowAtEnd, it.lineStyle) },
        )
    }

    @Test
    fun hcpMainNodeHasWindowTitleAndCrossReferences() {
        val doc = open(TestCorpus.hcpOrigEn)
        val main = doc.nodes.first()
        assertEquals("Main", main.name)
        assertEquals(NodeKind.TEXT, main.kind)
        assertEquals("Documentation for HCP", main.windowTitle)

        assertEquals(
            listOf(
                CrossReference(NodeIndex(123), " ST-Guide Documentation"),
                CrossReference(NodeIndex(121), " STool Documentation"),
                CrossReference(NodeIndex(122), " RefLink Documentation"),
            ),
            main.crossReferences,
        )

        // One image placeholder plus nine decorative box-drawing lines under the title.
        assertEquals(10, main.graphics.size)
        val image = assertIs<Graphic.Image>(main.graphics.first())
        assertEquals(NodeIndex(105), image.imageIndex)
        assertTrue(image.centered)

        // The prologue ends where the first text-region escape (0x24, link) begins, not at a
        // fixed a-e boundary — this node's title precedes its graphics on the wire.
        assertEquals(listOf(0x20, 0x20, 0x20, 0x20, 0x20, 0x1b), main.textBytes.take(6).map { it.toInt() and 0xFF })
    }

    @Test
    fun everyInternalAndPopupEntryProducesANodeOfMatchingKind() {
        val doc = open(TestCorpus.hcpOrigEn)
        val textOrPopupEntries = doc.entries.filter {
            it.type == IndexEntry.TYPE_INTERNAL || it.type == IndexEntry.TYPE_POPUP
        }
        assertEquals(textOrPopupEntries.size, doc.nodes.size)
        textOrPopupEntries.zip(doc.nodes).forEach { (entry, node) ->
            val expectedKind = if (entry.type == IndexEntry.TYPE_INTERNAL) NodeKind.TEXT else NodeKind.POPUP
            assertEquals(expectedKind, node.kind)
            assertEquals(entry.name, node.name)
        }
    }

    @Test
    fun dithermaskImmediatelyPrecedingAnImageIsAttachedToIt() {
        val diagnostics = ArrayList<Diagnostic>()
        val data = byteArrayOf(
            0x1b, 0x2f, 0x05, 0xAA.toByte(), 0xBB.toByte(), // dithermask, length=5, 2 bytes payload
            0x1b, 0x32, 0x01, 0x01, 0x00, 0x01, 0x01, 0x01, 0x00, // image, index=0, x=0, y=0, w=1, h=0
        )
        val node = parseNode(NodeIndex(0), "n", NodeKind.TEXT, data, diagnostics)
        val image = assertIs<Graphic.Image>(node.graphics.single())
        val ditherMask = assertNotNull(image.ditherMask)
        assertEquals(listOf(0xAA, 0xBB), ditherMask.map { it.toInt() and 0xFF })
        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun dithermaskNotFollowedByAnImageSurfacesAsADataBlock() {
        val diagnostics = ArrayList<Diagnostic>()
        val data = byteArrayOf(
            0x1b, 0x2f, 0x05, 0xAA.toByte(), 0xBB.toByte(),
            0x00, // empty text line, no image follows
        )
        val node = parseNode(NodeIndex(0), "n", NodeKind.TEXT, data, diagnostics)
        assertTrue(node.graphics.isEmpty())
        assertEquals(listOf(DataBlock(0x2f, byteArrayOf(0xAA.toByte(), 0xBB.toByte()))), node.dataBlocks)
    }

    @Test
    fun moreThanTwelveCrossReferencesIsDiagnosed() {
        val diagnostics = ArrayList<Diagnostic>()
        val oneCrossRef = byteArrayOf(0x1b, 0x30, 0x06, 0x01, 0x01, 0x00) // length=6, target=0, empty text
        val data = ByteArray(13 * oneCrossRef.size)
        repeat(13) { oneCrossRef.copyInto(data, it * oneCrossRef.size) }

        val node = parseNode(NodeIndex(0), "n", NodeKind.TEXT, data, diagnostics)
        assertEquals(13, node.crossReferences.size)
        assertEquals(listOf<Diagnostic>(Diagnostic.CrossReferenceLimitExceeded(NodeIndex(0), 13)), diagnostics)
    }

    @Test
    fun truncatedPrologueRecordIsDiagnosedAndStopsParsingThatNode() {
        val diagnostics = ArrayList<Diagnostic>()
        // A window-title escape with no terminating NUL before the data ends.
        val data = byteArrayOf(0x1b, 0x23, 'h'.code.toByte(), 'i'.code.toByte())
        val node = parseNode(NodeIndex(0), "n", NodeKind.TEXT, data, diagnostics)
        assertNull(node.windowTitle)
        assertEquals(listOf<Diagnostic>(Diagnostic.NodeDataOverrun(NodeIndex(0))), diagnostics)
    }
}
