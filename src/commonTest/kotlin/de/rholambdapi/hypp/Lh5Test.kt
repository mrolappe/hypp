package de.rholambdapi.hypp

import de.rholambdapi.hypp.internal.Lh5
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Node objects in the data region are `-lh5-` streams: LZSS over an 8 KiB window with
 * a maximum match of 256 bytes, coded with two per-block Huffman trees.
 *
 * `Lh5.decompress` returns null unless it produced exactly the requested number of
 * bytes without reading past the end of the compressed region — so asserting a
 * non-null result of the derived length is a real check, not a tautology about a
 * pre-sized output array.
 */
class Lh5Test {
    @Test
    fun textattrMainNodeDecompressesToItsDerivedLength() {
        val bytes = TestCorpus.textattr
        val doc = assertIs<OpenOutcome.Success>(HypDocument.open(bytes)).document
        val main = doc.entries.single()

        assertEquals(119, main.compressedLength)
        assertEquals(174, main.compDiff)
        assertEquals(293, main.uncompressedLength, "119 compressed + compDiff 174")

        val data = Lh5.decompress(bytes, main.seek, main.compressedLength, main.uncompressedLength)
        assertNotNull(data, "the lh5 stream at seek=110 must decode cleanly")
        assertEquals(293, data.size)
        assertEquals(0, data[data.size - 1].toInt(), "node text lines are NUL-terminated")
    }

    @Test
    fun truncatedStreamIsRejectedRatherThanReturningPartialData() {
        val bytes = TestCorpus.textattr
        val doc = assertIs<OpenOutcome.Success>(HypDocument.open(bytes)).document
        val main = doc.entries.single()

        assertNull(Lh5.decompress(bytes, main.seek, main.compressedLength / 2, main.uncompressedLength))
    }

    /** 124 index entries: 96 internal + 8 popup + 2 image carry data; 16 type-2, 1 type-5, 1 type-7 do not. */
    @Test
    fun everyDataBearingNodeInHcpOrigEnDecompresses() =
        assertWholeCorpusDecompresses(TestCorpus.hcpOrigEn, expectedNodes = 106, expectedImages = 2)

    /** 101 index entries: 59 internal + 4 popup + 15 image carry data; 22 type-2 and 1 type-4 do not. */
    @Test
    fun everyDataBearingNodeInStGuideOrigEnDecompresses() =
        assertWholeCorpusDecompresses(TestCorpus.stGuideOrigEn, expectedNodes = 78, expectedImages = 15)

    private fun assertWholeCorpusDecompresses(bytes: ByteArray, expectedNodes: Int, expectedImages: Int) {
        val doc = assertIs<OpenOutcome.Success>(HypDocument.open(bytes)).document
        var decompressed = 0
        var images = 0
        for (entry in doc.entries) {
            if (!entry.hasData) continue
            val data = Lh5.decompress(bytes, entry.seek, entry.compressedLength, entry.uncompressedLength)
            assertNotNull(data, "failed to decompress node '${entry.name}' (type ${entry.type})")
            assertEquals(entry.uncompressedLength, data.size, "node '${entry.name}'")
            decompressed++
            if (entry.isImage) images++
        }
        assertEquals(expectedNodes, decompressed)
        // Image entries reach their uncompressed length through the `next` overload, so they
        // are the only ones that exercise that rule end-to-end.
        assertEquals(expectedImages, images)
    }
}
