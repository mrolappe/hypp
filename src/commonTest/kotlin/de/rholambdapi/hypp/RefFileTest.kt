package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val ID_FILE = 0
private const val ID_NODE = 1
private const val ID_ALIAS = 2
private const val ID_LABEL = 3
private const val ID_DATABASE = 4

private fun be16(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())

private fun be32(value: Int) =
    byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

/** id, length-of-string-field, NUL-terminated string, plus (id 3 only) 2 extra bytes of line number. */
private fun entry(id: Int, name: String, lineNumber: Int? = null): ByteArray {
    val string = name.encodeToByteArray() + 0
    return byteArrayOf(id.toByte(), string.size.toByte()) + string +
        (lineNumber?.let { be16(it) } ?: ByteArray(0))
}

private fun module(vararg entries: ByteArray): ByteArray {
    val body = entries.fold(ByteArray(0)) { acc, e -> acc + e }
    return be32(body.size) + be32(entries.size) + body
}

private fun refBytes(vararg modules: ByteArray): ByteArray =
    "HREF".encodeToByteArray() + modules.fold(ByteArray(0)) { acc, m -> acc + m } + ByteArray(8)

private fun parsed(bytes: ByteArray): RefFile =
    assertIs<RefParseOutcome.Success>(RefFile.parse(bytes)).refFile

class RefFileTest {
    @Test
    fun singleModuleWithOneFileAndTwoNodes() {
        val ref = parsed(
            refBytes(
                module(
                    entry(ID_FILE, "ST-GUIDE"),
                    entry(ID_NODE, "Main"),
                    entry(ID_NODE, "See also"),
                ),
            ),
        )

        assertEquals(1, ref.modules.size)
        assertEquals(
            listOf(
                RefEntry.FileName("ST-GUIDE"),
                RefEntry.NodeName("Main"),
                RefEntry.NodeName("See also"),
            ),
            ref.modules[0].entries,
        )
        assertEquals(
            listOf(RefFileCatalog(fileName = "ST-GUIDE", nodeNames = listOf("Main", "See also"))),
            ref.modules[0].files(),
        )
    }

    @Test
    fun multipleModulesEachWithTheirOwnFile() {
        val ref = parsed(
            refBytes(
                module(entry(ID_FILE, "ST-GUIDE.HYP"), entry(ID_NODE, "Main")),
                module(entry(ID_FILE, "reflink.hyp"), entry(ID_NODE, "REF-format"), entry(ID_NODE, "Index")),
            ),
        )

        assertEquals(2, ref.modules.size)
        assertEquals(
            listOf(RefFileCatalog(fileName = "ST-GUIDE.HYP", nodeNames = listOf("Main"))),
            ref.modules[0].files(),
        )
        assertEquals(
            listOf(RefFileCatalog(fileName = "reflink.hyp", nodeNames = listOf("REF-format", "Index"))),
            ref.modules[1].files(),
        )
    }

    @Test
    fun labelEntryCarriesItsLineNumber() {
        val ref = parsed(
            refBytes(module(entry(ID_FILE, "ST-GUIDE"), entry(ID_LABEL, "syntax", lineNumber = 4242))),
        )

        assertEquals(
            listOf(RefEntry.FileName("ST-GUIDE"), RefEntry.LabelName("syntax", 4242)),
            ref.modules[0].entries,
        )
        assertEquals(
            listOf(RefFileCatalog(fileName = "ST-GUIDE", labels = listOf(RefEntry.LabelName("syntax", 4242)))),
            ref.modules[0].files(),
        )
    }

    @Test
    fun aliasAndDatabaseEntriesGroupUnderThePrecedingFile() {
        val ref = parsed(
            refBytes(
                module(
                    entry(ID_FILE, "ST-GUIDE"),
                    entry(ID_NODE, "Main"),
                    entry(ID_ALIAS, "Start"),
                    entry(ID_DATABASE, "ST-Guide Reference"),
                    entry(ID_FILE, "other.hyp"),
                    entry(ID_NODE, "Top"),
                ),
            ),
        )

        assertEquals(
            listOf(
                RefFileCatalog(
                    fileName = "ST-GUIDE",
                    nodeNames = listOf("Main"),
                    aliasNames = listOf("Start"),
                    databaseName = "ST-Guide Reference",
                ),
                RefFileCatalog(fileName = "other.hyp", nodeNames = listOf("Top")),
            ),
            ref.modules[0].files(),
        )
    }

    @Test
    fun entriesBeforeAnyFileEntryHaveNoOwnerAndAreDropped() {
        val ref = parsed(
            refBytes(module(entry(ID_NODE, "orphan"), entry(ID_FILE, "ST-GUIDE"), entry(ID_NODE, "Main"))),
        )

        assertEquals(3, ref.modules[0].entries.size, "the raw entry list keeps everything")
        assertEquals(
            listOf(RefFileCatalog(fileName = "ST-GUIDE", nodeNames = listOf("Main"))),
            ref.modules[0].files(),
        )
    }

    @Test
    fun terminatorOnlyFileHasNoModules() {
        assertEquals(RefFile(emptyList()), parsed(refBytes()))
    }

    @Test
    fun emptyModuleIsNotATerminator() {
        // A zero-entry module is only distinguishable from the terminator by its position:
        // the terminator ends the file, so anything after it is unreachable.
        val ref = parsed(refBytes(module(entry(ID_FILE, "a")), module()))
        assertEquals(2, ref.modules.size)
        assertEquals(emptyList(), ref.modules[1].entries)
    }

    @Test
    fun findIsCaseInsensitive() {
        val ref = parsed(refBytes(module(entry(ID_FILE, "ST-GUIDE"), entry(ID_NODE, "See also"))))

        assertEquals(
            RefFileCatalog(fileName = "ST-GUIDE", nodeNames = listOf("See also")),
            ref.find("st-guide", "SEE ALSO"),
        )
    }

    @Test
    fun findToleratesHypSuffixInEitherDirection() {
        val withSuffix = parsed(refBytes(module(entry(ID_FILE, "ST-GUIDE.HYP"), entry(ID_NODE, "Main"))))
        val withoutSuffix = parsed(refBytes(module(entry(ID_FILE, "ST-GUIDE"), entry(ID_NODE, "Main"))))

        assertEquals("ST-GUIDE.HYP", withSuffix.find("ST-GUIDE", "Main")?.fileName)
        assertEquals("ST-GUIDE", withoutSuffix.find("st-guide.hyp", "Main")?.fileName)
    }

    @Test
    fun findMatchesAliasNamesToo() {
        val ref = parsed(
            refBytes(module(entry(ID_FILE, "ST-GUIDE"), entry(ID_NODE, "Main"), entry(ID_ALIAS, "Start"))),
        )

        assertEquals("ST-GUIDE", ref.find("ST-GUIDE", "start")?.fileName)
    }

    @Test
    fun findSearchesAcrossModulesAndReturnsNullWhenAbsent() {
        val ref = parsed(
            refBytes(
                module(entry(ID_FILE, "a.hyp"), entry(ID_NODE, "Main")),
                module(entry(ID_FILE, "b.hyp"), entry(ID_NODE, "Deep")),
            ),
        )

        assertEquals("b.hyp", ref.find("b", "Deep")?.fileName)
        assertNull(ref.find("a.hyp", "Deep"), "node must belong to the named file")
        assertNull(ref.find("c.hyp", "Main"))
    }

    @Test
    fun invalidMagicIsAFailure() {
        assertEquals(
            RefParseOutcome.Failure(RefParseFailure.InvalidMagic),
            RefFile.parse("HYPX".encodeToByteArray() + ByteArray(8)),
        )
        assertEquals(RefParseOutcome.Failure(RefParseFailure.InvalidMagic), RefFile.parse(ByteArray(2)))
    }

    @Test
    fun truncatedModuleHeaderIsAFailure() {
        val bytes = "HREF".encodeToByteArray() + byteArrayOf(0, 0, 0)
        assertEquals(RefParseOutcome.Failure(RefParseFailure.Truncated), RefFile.parse(bytes))
    }

    @Test
    fun moduleLengthBeyondTheBufferIsAFailure() {
        val bytes = "HREF".encodeToByteArray() + be32(0x0100_0000) + be32(1)
        assertEquals(RefParseOutcome.Failure(RefParseFailure.Truncated), RefFile.parse(bytes))
    }

    @Test
    fun entryRunningPastTheModuleEndIsAFailure() {
        // Declares one entry whose string field is longer than the module body it lives in.
        val body = byteArrayOf(ID_NODE.toByte(), 40) + "short".encodeToByteArray() + 0
        val bytes = "HREF".encodeToByteArray() + be32(body.size) + be32(1) + body + ByteArray(8)
        assertEquals(RefParseOutcome.Failure(RefParseFailure.Truncated), RefFile.parse(bytes))
    }

    @Test
    fun moduleDeclaringMoreEntriesThanItHoldsIsAFailure() {
        val body = entry(ID_FILE, "a")
        val bytes = "HREF".encodeToByteArray() + be32(body.size) + be32(9) + body + ByteArray(8)
        assertEquals(RefParseOutcome.Failure(RefParseFailure.Truncated), RefFile.parse(bytes))
    }

    @Test
    fun labelWithoutItsLineNumberIsAFailure() {
        val body = byteArrayOf(ID_LABEL.toByte(), 2) + "x".encodeToByteArray() + 0
        val bytes = "HREF".encodeToByteArray() + be32(body.size) + be32(1) + body + ByteArray(8)
        assertEquals(RefParseOutcome.Failure(RefParseFailure.Truncated), RefFile.parse(bytes))
    }

    @Test
    fun unknownEntryIdIsAFailure() {
        val bytes = "HREF".encodeToByteArray() + module(entry(77, "what")) + ByteArray(8)
        assertEquals(RefParseOutcome.Failure(RefParseFailure.UnknownEntryId(77)), RefFile.parse(bytes))
    }

    @Test
    fun fileEndingWithoutATerminatorStillParses() {
        // The terminator is the documented end-of-file marker, but the .HYP side of this
        // format has an equally documented sentinel that real files omit (doc/LEARNINGS.md),
        // so running out of bytes exactly at a module boundary is accepted rather than rejected.
        val bytes = "HREF".encodeToByteArray() + module(entry(ID_FILE, "a.hyp"), entry(ID_NODE, "Main"))
        assertEquals(listOf("a.hyp"), parsed(bytes).modules.flatMap { m -> m.files().map { it.fileName } })
    }

    @Test
    fun bytesAfterTheTerminatorAreIgnored() {
        val bytes = refBytes(module(entry(ID_FILE, "a.hyp"))) + "trailing junk".encodeToByteArray()
        assertEquals(1, parsed(bytes).modules.size)
    }
}
