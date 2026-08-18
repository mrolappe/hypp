package de.rholambdapi.hypp

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val ID_FILE = 0
private const val ID_NODE = 1
private const val ID_ALIAS = 2
private const val ID_LABEL = 3
private const val ID_DATABASE = 4

private fun be16(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())

private fun be32(value: Int) =
    byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

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

private fun parseSuccess(bytes: ByteArray): RefFile =
    assertIs<RefParseOutcome.Success>(RefFile.parse(bytes)).refFile

/**
 * Generator for random RefEntry sequences. Produces plausible mixed sequences of file/node/alias/label/database entries.
 */
private class RefEntryGenerator(private val random: Random) {
    private val safeCharacters = ('a'..'z') + ('A'..'Z') + ('0'..'9') + setOf('-', '_', ' ', '.')

    private fun randomString(maxLength: Int = 20): String {
        val length = random.nextInt(1, maxLength + 1)
        return (1..length).map { safeCharacters[random.nextInt(safeCharacters.size)] }.joinToString("")
    }

    fun generateEntries(): List<RefEntry> {
        val entries = mutableListOf<RefEntry>()
        val entryCount = random.nextInt(1, 8) // 1-7 entries per module

        // Always start with a file entry so subsequent entries have an owner
        entries.add(RefEntry.FileName(randomString(15)))

        repeat(entryCount - 1) {
            val entryType = random.nextInt(5) // 0=file, 1=node, 2=alias, 3=label, 4=database
            entries.add(
                when (entryType) {
                    0 -> RefEntry.FileName(randomString(15))
                    1 -> RefEntry.NodeName(randomString(20))
                    2 -> RefEntry.AliasName(randomString(20))
                    3 -> RefEntry.LabelName(randomString(15), random.nextInt(1, 10000))
                    else -> RefEntry.DatabaseName(randomString(20))
                }
            )
        }
        return entries
    }
}

class RefFilePropertyTest {
    @Test
    fun roundTripWithRandomEntriesAcross200Seeds() {
        (1..200).forEach { seed ->
            val random = Random(seed)
            val generator = RefEntryGenerator(random)

            val moduleCount = random.nextInt(1, 6) // 1-5 modules
            val generatedModules = (1..moduleCount).map { RefModule(generator.generateEntries()) }
            val generatedFile = RefFile(generatedModules)

            // Encode to bytes per spec
            val moduleBytes = generatedModules.map { module ->
                val entryBytes = module.entries.map { entry ->
                    when (entry) {
                        is RefEntry.FileName -> entry(ID_FILE, entry.name)
                        is RefEntry.NodeName -> entry(ID_NODE, entry.name)
                        is RefEntry.AliasName -> entry(ID_ALIAS, entry.name)
                        is RefEntry.LabelName -> entry(ID_LABEL, entry.name, entry.lineNumber)
                        is RefEntry.DatabaseName -> entry(ID_DATABASE, entry.name)
                    }
                }
                module(*entryBytes.toTypedArray())
            }.toTypedArray()
            val bytes = refBytes(*moduleBytes)

            // Parse back
            val parsed = parseSuccess(bytes)

            // Assert round-trip equality at the module/entry level
            assertEquals(generatedFile.modules.size, parsed.modules.size, "seed=$seed: module count mismatch")
            generatedFile.modules.indices.forEach { moduleIndex ->
                val expected = generatedFile.modules[moduleIndex]
                val actual = parsed.modules[moduleIndex]
                assertEquals(
                    expected.entries.size, actual.entries.size,
                    "seed=$seed: module $moduleIndex entry count mismatch"
                )
                expected.entries.indices.forEach { entryIndex ->
                    val expectedEntry = expected.entries[entryIndex]
                    val actualEntry = actual.entries[entryIndex]
                    assertEquals(expectedEntry, actualEntry, "seed=$seed: module $moduleIndex entry $entryIndex mismatch")
                }
            }
        }
    }

    @Test
    fun emptyFileParses() {
        val bytes = refBytes()
        val parsed = parseSuccess(bytes)
        assertEquals(0, parsed.modules.size)
    }

    @Test
    fun singleModuleWithOneEntryRoundTrips() {
        val original = RefFile(
            listOf(
                RefModule(
                    listOf(
                        RefEntry.FileName("test"),
                        RefEntry.NodeName("Main"),
                    )
                )
            )
        )

        val bytes = refBytes(
            module(
                entry(ID_FILE, "test"),
                entry(ID_NODE, "Main"),
            )
        )

        val parsed = parseSuccess(bytes)
        assertEquals(original, parsed)
    }
}
