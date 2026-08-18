package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [IndexEntry.name] for a TYPE_EXTERNAL_REF entry packs "<filename>/<nodename>" into one
 * string, but real corpus files contain two anomalies with no "/" at all ("Options",
 * "command extern") — for those the whole string is the node name. See
 * doc/PLAN-12-19.md, "Resolved: what TYPE_EXTERNAL_REF.name actually contains".
 */
class IndexEntryTest {
    private fun entry(name: String) =
        IndexEntry(len = 0, type = IndexEntry.TYPE_EXTERNAL_REF, seek = 0, compDiff = 0, next = 0, prev = 0, toc = 0, name = name, compressedLength = 0)

    @Test
    fun splitsOnTheFirstSlashIntoFileNameAndNodeName() {
        assertEquals(ExternalRef("ST-GUIDE.HYP", "See also"), entry("ST-GUIDE.HYP/See also").externalRef())
        assertEquals(ExternalRef("reflink.hyp", "REF-format"), entry("reflink.hyp/REF-format").externalRef())
    }

    @Test
    fun splitsOnlyOnTheFirstSlashWhenTheNodeNameContainsMore() {
        assertEquals(ExternalRef("a.hyp", "b/c"), entry("a.hyp/b/c").externalRef())
    }

    @Test
    fun withNoSlashTheWholeStringIsTheNodeNameAndFileNameIsNull() {
        val ref = entry("Options").externalRef()
        assertNull(ref.fileName)
        assertEquals("Options", ref.nodeName)
    }

    @Test
    fun realCorpusExternalRefsSplitSanelyWithExactlyTwoFileLessAnomalies() {
        val doc = HypDocument.open(TestCorpus.hcpOrigEn).let {
            assertIs<OpenOutcome.Success>(it)
            it.document
        }
        val refs = doc.entries.filter { it.type == IndexEntry.TYPE_EXTERNAL_REF }
        // The plan doc (doc/PLAN-12-19.md) recorded 18 type-2 entries; the actual fixture
        // has 16 (14 with a "/" plus the 2 known anomalies) — see doc/format-notes.md.
        assertEquals(16, refs.size)

        val split = refs.map { it.externalRef() }
        val withFile = split.filter { it.fileName != null }
        val withoutFile = split.filter { it.fileName == null }
        assertEquals(14, withFile.size)
        assertEquals(setOf("Options", "command extern"), withoutFile.map { it.nodeName }.toSet())
    }
}
