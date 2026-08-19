package de.rholambdapi.hypp.cli.render

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ZipTest {
    @Test
    fun roundTripsEntriesThroughZipInputStream() {
        val files = listOf(
            RenderedFile("a.txt", "hello".encodeToByteArray()),
            RenderedFile("dir/b.txt", "world".encodeToByteArray()),
            RenderedFile("c.bin", byteArrayOf(0, 1, 2, 3, -1, -2)),
        )

        val bytes = zip(files)

        val readBack = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                readBack[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }

        assertEquals(files.map { it.path }, readBack.keys.toList())
        for (file in files) {
            val actual = readBack[file.path]
            assertNotNull(actual, "missing entry ${file.path}")
            assertEquals(file.bytes.toList(), actual.toList())
        }
    }

    @Test
    fun emptyFileListProducesAnEmptyValidZip() {
        val bytes = zip(emptyList())
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            assertNull(zis.nextEntry)
        }
    }
}
