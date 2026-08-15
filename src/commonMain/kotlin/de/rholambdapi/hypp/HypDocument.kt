package de.rholambdapi.hypp

import de.rholambdapi.hypp.internal.ByteReader
import de.rholambdapi.hypp.internal.Lh5
import de.rholambdapi.hypp.internal.decodeName

class HypDocument(
    val header: Header,
    val extendedHeaders: List<ExtendedHeader>,
    val entries: List<IndexEntry>,
    val charset: HypCharset,
    val nodes: List<Node>,
    val images: List<ImageNode>,
    val diagnostics: List<Diagnostic>,
) {
    private val nodesByIndex: Map<NodeIndex, Node> by lazy { nodes.associateBy { it.index } }
    private val imagesByIndex: Map<NodeIndex, ImageNode> by lazy { images.associateBy { it.index } }

    /** Every entry type (including navigation-only ones like external refs or quit) resolves here. */
    fun entry(index: NodeIndex): IndexEntry? = entries.getOrNull(index.value)

    /** Non-null only for internal/popup entries — see [entry] for a target that may be any type. */
    fun node(index: NodeIndex): Node? = nodesByIndex[index]

    /** Non-null only for image entries — see [entry] for a target that may be any type. */
    fun image(index: NodeIndex): ImageNode? = imagesByIndex[index]

    /** The node named by extended header id 2 (`@default`), or null if that header is absent or dangling. */
    val defaultNode: NodeIndex? by lazy {
        val name = extendedHeaders.filterIsInstance<ExtendedHeader.Default>().firstOrNull()?.name
        name?.let { n -> entries.indexOfFirst { it.name == n }.takeIf { it >= 0 }?.let(::NodeIndex) }
    }

    /**
     * The document's table of contents, rooted at [NodeIndex] 0 and nested via [IndexEntry.toc].
     * A `toc` cycle away from the root (malformed input) is broken silently rather than recursing
     * forever: once an index has been placed in the tree it cannot be placed again.
     */
    fun tableOfContents(): TocEntry {
        val childrenOf = HashMap<Int, MutableList<Int>>()
        entries.forEachIndexed { i, e -> if (i != 0) childrenOf.getOrPut(e.toc) { mutableListOf() }.add(i) }
        val visited = HashSet<Int>()
        fun build(i: Int): TocEntry {
            visited += i
            val children = childrenOf[i].orEmpty().filter { it !in visited }
            return TocEntry(NodeIndex(i), children.map(::build))
        }
        return build(0)
    }

    companion object {
        fun open(bytes: ByteArray): OpenOutcome {
            val container = parseContainer(bytes) ?: return OpenOutcome.Failure(OpenFailure.InvalidMagic)
            val (header, extendedHeaders, entries) = container

            val diagnostics = ArrayList<Diagnostic>()
            val charsetName = extendedHeaders.filterIsInstance<ExtendedHeader.Charset>().firstOrNull()?.name
            val charset = when {
                charsetName == null -> HypCharset.Default
                else -> HypCharset.byName(charsetName) ?: run {
                    diagnostics += Diagnostic.UnsupportedCharset(charsetName)
                    HypCharset.Default
                }
            }

            fun decompress(e: IndexEntry, i: Int): ByteArray? {
                val decompressed = decompressEntry(bytes, e)
                if (decompressed == null) diagnostics += Diagnostic.DecompressionFailed(NodeIndex(i))
                return decompressed
            }

            val entryNames = entries.map { it.name }
            val nodes = entries.mapIndexedNotNull { i, e ->
                if (e.type != IndexEntry.TYPE_INTERNAL && e.type != IndexEntry.TYPE_POPUP) return@mapIndexedNotNull null
                val decompressed = decompress(e, i) ?: return@mapIndexedNotNull null
                val kind = if (e.type == IndexEntry.TYPE_INTERNAL) NodeKind.TEXT else NodeKind.POPUP
                parseNode(NodeIndex(i), e.name, kind, decompressed, diagnostics, charset, entryNames)
            }
            val images = entries.mapIndexedNotNull { i, e ->
                if (!e.isImage) return@mapIndexedNotNull null
                val decompressed = decompress(e, i) ?: return@mapIndexedNotNull null
                parseImage(NodeIndex(i), e.name, decompressed, diagnostics)
            }

            return OpenOutcome.Success(HypDocument(header, extendedHeaders, entries, charset, nodes, images, diagnostics))
        }
    }
}

/** The header, index table and extended headers — everything before charset resolution and node parsing. */
internal data class RawContainer(
    val header: Header,
    val extendedHeaders: List<ExtendedHeader>,
    val entries: List<IndexEntry>,
)

private const val MAGIC = "HDOC"
private const val ENTRY_FIXED_SIZE = 14

/**
 * Parses everything in [HypDocument.open] up to (but not including) charset resolution and node/image
 * parsing. Factored out so tooling that needs raw per-entry bytes (the phase-11 wild-sweep task) can
 * reuse the exact same container-decoding logic `open` uses, rather than re-deriving it.
 */
internal fun parseContainer(bytes: ByteArray): RawContainer? {
    // Too short to even hold the magic: reject before any read can run past the end,
    // the boundary case an all-JS caller can trivially hit with arbitrary input.
    if (bytes.size < 4) return null
    val reader = ByteReader(bytes)
    val magic = reader.readBytes(4).decodeToString()
    if (magic != MAGIC) return null

    val header = Header(
        itableSize = reader.readU32(),
        itableCount = reader.readU16(),
        compilerVersion = reader.readU8(),
        compilerOs = reader.readU8(),
    )

    data class RawEntry(
        val len: Int, val type: Int, val seek: Int, val compDiff: Int,
        val next: Int, val prev: Int, val toc: Int, val name: String,
    )

    val rawEntries = ArrayList<RawEntry>(header.itableCount)
    repeat(header.itableCount) {
        val len = reader.readU8()
        val type = reader.readU8()
        val seek = reader.readU32()
        val compDiff = reader.readU16()
        val next = reader.readU16()
        val prev = reader.readU16()
        val toc = reader.readU16()
        val nameLength = len - ENTRY_FIXED_SIZE
        val name = if (nameLength > 0) reader.readBytes(nameLength).decodeName() else ""
        rawEntries += RawEntry(len, type, seek, compDiff, next, prev, toc, name)
    }

    // itableCount includes a trailing type-255 EOF sentinel *when present* — but
    // not every file has one (e.g. hcp_orig_en.hyp ends without one). Either way,
    // the boundary for the last entry's derived length is the next entry's seek,
    // or the file's own length when there is no next entry.
    val entries = rawEntries.mapIndexedNotNull { i, e ->
        if (e.type == IndexEntry.TYPE_EOF) null
        else {
            val nextSeek = if (i + 1 < rawEntries.size) rawEntries[i + 1].seek else bytes.size
            IndexEntry(
                len = e.len, type = e.type, seek = e.seek, compDiff = e.compDiff,
                next = e.next, prev = e.prev, toc = e.toc, name = e.name,
                compressedLength = nextSeek - e.seek,
            )
        }
    }

    // Extended headers: id:u16, length:u16, data[]. The terminator is a full
    // id=0, length=0 pair (4 bytes) — not a bare id=0 — confirmed empirically
    // against textattr.hyp and empty.hyp; see doc/format-notes.md.
    val extendedHeaders = ArrayList<ExtendedHeader>()
    while (true) {
        val id = reader.readU16()
        val length = reader.readU16()
        if (id == 0) break
        val data = reader.readBytes(length)
        extendedHeaders += when (id) {
            ExtendedHeader.Charset.ID -> ExtendedHeader.Charset(data.decodeName())
            ExtendedHeader.Default.ID -> ExtendedHeader.Default(data.decodeName())
            else -> ExtendedHeader.Unknown(id, data)
        }
    }

    return RawContainer(header, extendedHeaders, entries)
}

/**
 * Decompresses one index entry's object, or returns null on failure (a malformed lh5 stream).
 * An object whose uncompressed size equals its compressed size is stored raw — the compiler
 * skips lh5 when it wouldn't help. See `doc/format-notes.md`.
 */
internal fun decompressEntry(bytes: ByteArray, e: IndexEntry): ByteArray? =
    if (e.uncompressedLength == e.compressedLength) bytes.copyOfRange(e.seek, e.seek + e.compressedLength)
    else Lh5.decompress(bytes, e.seek, e.compressedLength, e.uncompressedLength)
