package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContainerTest {
    @Test
    fun textattrHeaderAndIndexTable() {
        val outcome = HypDocument.open(TestCorpus.textattr)
        val doc = assertIs<OpenOutcome.Success>(outcome).document

        assertEquals(34, doc.header.itableSize)
        assertEquals(2, doc.header.itableCount)

        assertEquals(1, doc.entries.size, "the EOF sentinel must not be exposed as an entry")
        val main = doc.entries[0]
        assertEquals(20, main.len)
        assertEquals(0, main.type)
        assertEquals(110, main.seek)
        assertEquals(174, main.compDiff)
        assertEquals("Main", main.name)
        assertEquals(119, main.compressedLength, "derived from seek[1] - seek[0] = 229 - 110")
    }

    @Test
    fun emptyHypYieldsZeroRealNodes() {
        val outcome = HypDocument.open(TestCorpus.empty)
        val doc = assertIs<OpenOutcome.Success>(outcome).document

        assertEquals(14, doc.header.itableSize)
        assertEquals(1, doc.header.itableCount, "just the EOF sentinel")
        assertTrue(doc.entries.isEmpty())
    }

    @Test
    fun invalidMagicIsAFailure() {
        val bytes = TestCorpus.textattr.copyOf()
        bytes[0] = 'X'.code.toByte()
        val outcome = HypDocument.open(bytes)
        assertEquals(OpenOutcome.Failure(OpenFailure.InvalidMagic), outcome)
    }

    @Test
    fun realDocumentEveryEntryInBounds() {
        val bytes = TestCorpus.hcpOrigEn
        val outcome = HypDocument.open(bytes)
        val doc = assertIs<OpenOutcome.Success>(outcome).document

        assertTrue(doc.entries.isNotEmpty())
        for (entry in doc.entries) {
            assertTrue(entry.seek in 0..bytes.size, "seek ${entry.seek} out of bounds for ${entry.name}")
            assertTrue(entry.compressedLength >= 0, "negative compressed length for ${entry.name}")
        }
    }
}
