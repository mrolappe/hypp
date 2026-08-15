package de.rholambdapi.hypp

import de.rholambdapi.hypp.internal.ByteReader
import de.rholambdapi.hypp.internal.decodeName

class HypDocument(
    val header: Header,
    val extendedHeaders: List<ExtendedHeader>,
    val entries: List<IndexEntry>,
) {
    companion object {
        private const val MAGIC = "HDOC"
        private const val ENTRY_FIXED_SIZE = 14

        fun open(bytes: ByteArray): OpenOutcome {
            val reader = ByteReader(bytes)
            val magic = reader.readBytes(4).decodeToString()
            if (magic != MAGIC) return OpenOutcome.Failure(OpenFailure.InvalidMagic)

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
                extendedHeaders += ExtendedHeader.Unknown(id, reader.readBytes(length))
            }

            return OpenOutcome.Success(HypDocument(header, extendedHeaders, entries))
        }
    }
}
