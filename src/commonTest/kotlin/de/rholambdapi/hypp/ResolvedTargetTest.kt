package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `resolve()` collapses the three existing accessors (`entry`/`node`/`image`) into one
 * total dispatch over [IndexEntry] type, so a caller following a link never has to know
 * which of them a given type is served by.
 */
class ResolvedTargetTest {
    private fun entry(index: Int, type: Int) = IndexEntry(
        len = 0, type = type, seek = 0, compDiff = 0, next = 0, prev = 0, toc = 0,
        name = if (type == IndexEntry.TYPE_EXTERNAL_REF) "other.hyp/Target" else "entry$index",
        compressedLength = 0,
    )

    private fun node(index: Int, kind: NodeKind) = Node(
        index = NodeIndex(index), name = "entry$index", kind = kind, windowTitle = null,
        graphics = emptyList(), crossReferences = emptyList(), dataBlocks = emptyList(),
        objectTable = emptyList(), lines = emptyList(),
    )

    private fun image(index: Int) = ImageNode(
        index = NodeIndex(index), name = "entry$index", width = 16, height = 1,
        planeCount = 1, planePresent = 1, planeFilled = 0, planeData = ByteArray(2),
    )

    /** One entry per type, at the index matching its own type constant. */
    private val types = listOf(
        IndexEntry.TYPE_INTERNAL, IndexEntry.TYPE_POPUP, IndexEntry.TYPE_EXTERNAL_REF,
        IndexEntry.TYPE_IMAGE, IndexEntry.TYPE_SYSTEM, IndexEntry.TYPE_REXX_SCRIPT,
        IndexEntry.TYPE_REXX_COMMAND, IndexEntry.TYPE_QUIT, IndexEntry.TYPE_CLOSE,
    )

    private val document = HypDocument(
        header = Header(itableSize = 0, itableCount = types.size, compilerVersion = 3, compilerOs = 2),
        extendedHeaders = emptyList(),
        entries = types.mapIndexed { i, t -> entry(i, t) },
        charset = HypCharset.Default,
        nodes = listOf(node(IndexEntry.TYPE_INTERNAL, NodeKind.TEXT), node(IndexEntry.TYPE_POPUP, NodeKind.POPUP)),
        images = listOf(image(IndexEntry.TYPE_IMAGE)),
        diagnostics = emptyList(),
    )

    @Test
    fun everyEntryTypeResolvesToItsVariant() {
        val expectations: List<Pair<Int, (ResolvedTarget) -> Unit>> = listOf(
            IndexEntry.TYPE_INTERNAL to { r -> assertEquals(NodeKind.TEXT, assertIs<ResolvedTarget.ToNode>(r).node.kind) },
            IndexEntry.TYPE_POPUP to { r -> assertEquals(NodeKind.POPUP, assertIs<ResolvedTarget.ToNode>(r).node.kind) },
            IndexEntry.TYPE_EXTERNAL_REF to { r ->
                assertEquals(ExternalRef("other.hyp", "Target"), assertIs<ResolvedTarget.ToExternalRef>(r).ref)
            },
            IndexEntry.TYPE_IMAGE to { r -> assertEquals(16, assertIs<ResolvedTarget.ToImage>(r).image.width) },
            IndexEntry.TYPE_SYSTEM to { r -> assertEquals(IndexEntry.TYPE_SYSTEM, assertIs<ResolvedTarget.ToSystemAction>(r).entry.type) },
            IndexEntry.TYPE_REXX_SCRIPT to { r -> assertEquals(IndexEntry.TYPE_REXX_SCRIPT, assertIs<ResolvedTarget.ToSystemAction>(r).entry.type) },
            IndexEntry.TYPE_REXX_COMMAND to { r -> assertEquals(IndexEntry.TYPE_REXX_COMMAND, assertIs<ResolvedTarget.ToSystemAction>(r).entry.type) },
            IndexEntry.TYPE_QUIT to { r -> assertEquals(IndexEntry.TYPE_QUIT, assertIs<ResolvedTarget.ToSystemAction>(r).entry.type) },
            IndexEntry.TYPE_CLOSE to { r -> assertEquals(IndexEntry.TYPE_CLOSE, assertIs<ResolvedTarget.ToSystemAction>(r).entry.type) },
        )
        assertEquals(types, expectations.map { it.first }, "every TYPE_* constant must have a case")

        for ((type, assertVariant) in expectations) {
            assertVariant(document.resolve(NodeIndex(type)))
        }
    }

    @Test
    fun outOfRangeIndexIsMissing() {
        assertEquals(ResolvedTarget.Missing, document.resolve(NodeIndex(9999)))
    }
}
