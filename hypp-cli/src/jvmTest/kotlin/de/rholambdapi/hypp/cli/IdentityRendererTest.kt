package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.IndexEntry
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.cli.render.IdentityRenderer
import de.rholambdapi.hypp.cli.render.Renderer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the Renderer/registry skeleton and the jvm Io actuals round-trip end to end,
 * before any real renderer exists (Phase 15).
 */
class IdentityRendererTest {
    private fun entry(index: Int) = IndexEntry(
        len = 0, type = IndexEntry.TYPE_INTERNAL, seek = 0, compDiff = 0,
        next = 0, prev = 0, toc = 0, name = "entry$index", compressedLength = 0,
    )

    private fun node(index: Int) = Node(
        index = NodeIndex(index), name = "entry$index", kind = NodeKind.TEXT, windowTitle = null,
        graphics = emptyList(), crossReferences = emptyList(), dataBlocks = emptyList(),
        objectTable = emptyList(), lines = emptyList(),
    )

    private val document = HypDocument(
        header = Header(itableSize = 0, itableCount = 2, compilerVersion = 3, compilerOs = 2),
        extendedHeaders = emptyList(),
        entries = listOf(entry(0), entry(1)),
        charset = HypCharset.Default,
        nodes = listOf(node(0), node(1)),
        images = emptyList(),
        diagnostics = emptyList(),
    )

    private val registry: Map<String, Renderer> = mapOf("identity" to IdentityRenderer)

    @Test
    fun rendersAndRoundTripsThroughIo() {
        val rendered = registry.getValue("identity").render(document)
        assertEquals("2", rendered)

        val file = File.createTempFile("hypp-cli-identity-test", ".txt")
        try {
            writeBytes(file.path, rendered.encodeToByteArray())
            assertEquals(rendered, readBytes(file.path).decodeToString())
        } finally {
            file.delete()
        }
    }
}
