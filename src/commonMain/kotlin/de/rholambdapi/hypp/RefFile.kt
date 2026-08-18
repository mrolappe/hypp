package de.rholambdapi.hypp

import de.rholambdapi.hypp.internal.decodeName

/**
 * One entry of a `.REF` module. `NodeName`/`AliasName`/`LabelName`/`DatabaseName` belong to
 * whichever `FileName` entry precedes them — see [RefModule.files].
 */
sealed interface RefEntry {
    val name: String

    data class FileName(override val name: String) : RefEntry
    data class NodeName(override val name: String) : RefEntry
    data class AliasName(override val name: String) : RefEntry
    data class LabelName(override val name: String, val lineNumber: Int) : RefEntry
    data class DatabaseName(override val name: String) : RefEntry
}

/** One `FileName` entry with everything the module attributes to it. */
data class RefFileCatalog(
    val fileName: String,
    val nodeNames: List<String> = emptyList(),
    val aliasNames: List<String> = emptyList(),
    val labels: List<RefEntry.LabelName> = emptyList(),
    val databaseName: String? = null,
)

data class RefModule(val entries: List<RefEntry>) {
    /**
     * Groups the flat entry list into per-file catalogues. Entries appearing before the module's
     * first `FileName` have no owner and are dropped here (they remain in [entries]); a second
     * `DatabaseName` for the same file is ignored, since a file has one database name.
     */
    fun files(): List<RefFileCatalog> {
        val catalogs = mutableListOf<RefFileCatalog>()
        for (entry in entries) {
            if (entry is RefEntry.FileName) {
                catalogs += RefFileCatalog(entry.name)
                continue
            }
            val current = catalogs.removeLastOrNull() ?: continue
            catalogs += when (entry) {
                is RefEntry.NodeName -> current.copy(nodeNames = current.nodeNames + entry.name)
                is RefEntry.AliasName -> current.copy(aliasNames = current.aliasNames + entry.name)
                is RefEntry.LabelName -> current.copy(labels = current.labels + entry)
                is RefEntry.DatabaseName -> current.copy(databaseName = current.databaseName ?: entry.name)
                is RefEntry.FileName -> current
            }
        }
        return catalogs
    }
}

data class RefFile(val modules: List<RefModule>) {
    /**
     * Looks up the catalogue for [nodeName] (a node or alias name) in [fileName]. Both comparisons
     * are case-insensitive and tolerate a `.HYP` suffix on either side: `TYPE_EXTERNAL_REF` names
     * and `.REF` file entries disagree about casing and extension in the real corpus.
     */
    fun find(fileName: String, nodeName: String): RefFileCatalog? =
        modules.asSequence()
            .flatMap { it.files() }
            .firstOrNull { catalog ->
                catalog.fileName.matchesFileName(fileName) &&
                    (catalog.nodeNames.any { it.equals(nodeName, ignoreCase = true) } ||
                        catalog.aliasNames.any { it.equals(nodeName, ignoreCase = true) })
            }

    companion object {
        private const val MAGIC = "HREF"
        private const val MODULE_HEADER_SIZE = 8

        private const val ID_FILE = 0
        private const val ID_NODE = 1
        private const val ID_ALIAS = 2
        private const val ID_LABEL = 3
        private const val ID_DATABASE = 4

        /**
         * Parses a `.REF` library. Declared module lengths and entry lengths are checked against
         * what is actually there before any read, so hostile or truncated input yields a
         * [RefParseOutcome.Failure] rather than an exception.
         *
         * A module's declared length bounds its entries; trailing bytes inside a module are
         * skipped. The 8-zero-byte terminator ends the file, and anything after it is ignored —
         * as is a file that simply runs out at a module boundary without one.
         */
        fun parse(bytes: ByteArray): RefParseOutcome {
            if (bytes.size < 4 || bytes.copyOfRange(0, 4).decodeToString() != MAGIC) {
                return RefParseOutcome.Failure(RefParseFailure.InvalidMagic)
            }

            val modules = mutableListOf<RefModule>()
            var pos = 4
            while (pos < bytes.size) {
                if (bytes.size - pos < MODULE_HEADER_SIZE) return truncated
                val length = bytes.u32(pos)
                val count = bytes.u32(pos + 4)
                pos += MODULE_HEADER_SIZE
                if (length == 0 && count == 0) break
                if (length < 0 || count < 0 || length > bytes.size - pos) return truncated

                val end = pos + length
                val entries = mutableListOf<RefEntry>()
                var p = pos
                repeat(count) {
                    if (end - p < 2) return truncated
                    val id = bytes[p].toInt() and 0xFF
                    val stringLength = bytes[p + 1].toInt() and 0xFF
                    p += 2
                    if (stringLength > end - p) return truncated
                    val name = bytes.copyOfRange(p, p + stringLength).decodeName()
                    p += stringLength
                    entries += when (id) {
                        ID_FILE -> RefEntry.FileName(name)
                        ID_NODE -> RefEntry.NodeName(name)
                        ID_ALIAS -> RefEntry.AliasName(name)
                        ID_LABEL -> {
                            if (end - p < 2) return truncated
                            RefEntry.LabelName(name, bytes.u16(p)).also { p += 2 }
                        }
                        ID_DATABASE -> RefEntry.DatabaseName(name)
                        else -> return RefParseOutcome.Failure(RefParseFailure.UnknownEntryId(id))
                    }
                }
                modules += RefModule(entries)
                pos = end
            }
            return RefParseOutcome.Success(RefFile(modules))
        }

        private val truncated = RefParseOutcome.Failure(RefParseFailure.Truncated)
    }
}

sealed interface RefParseOutcome {
    data class Success(val refFile: RefFile) : RefParseOutcome
    data class Failure(val reason: RefParseFailure) : RefParseOutcome
}

sealed interface RefParseFailure {
    data object InvalidMagic : RefParseFailure

    /** A module header, entry, or entry string reaches past the buffer or past its module. */
    data object Truncated : RefParseFailure

    data class UnknownEntryId(val id: Int) : RefParseFailure
}

private fun String.matchesFileName(other: String): Boolean =
    withoutHypSuffix().equals(other.withoutHypSuffix(), ignoreCase = true)

private fun String.withoutHypSuffix(): String =
    if (endsWith(".HYP", ignoreCase = true)) dropLast(4) else this

private fun ByteArray.u16(at: Int): Int = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

private fun ByteArray.u32(at: Int): Int = (u16(at) shl 16) or u16(at + 2)
