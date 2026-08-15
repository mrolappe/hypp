package de.rholambdapi.hypp

/**
 * One index-table entry (the type-255 EOF sentinel is not exposed here — see
 * [HypDocument.entries]). [compressedLength] is derived from the seek offset of the
 * following entry, per the container format (there is no length field).
 */
data class IndexEntry(
    val len: Int,
    val type: Int,
    val seek: Int,
    val compDiff: Int,
    val next: Int,
    val prev: Int,
    val toc: Int,
    val name: String,
    val compressedLength: Int,
) {
    val isImage: Boolean get() = type == TYPE_IMAGE

    /**
     * Whether this entry has a compressed object in the data region. Only internal,
     * popup and image nodes do; types 2 and 4–8 exist purely as index entries, so
     * their derived [compressedLength] is meaningless.
     */
    val hasData: Boolean get() = type == TYPE_INTERNAL || type == TYPE_POPUP || type == TYPE_IMAGE

    /**
     * For image entries `next` is overloaded to hold the high bits of the
     * uncompressed size, rather than being a navigation link.
     */
    val uncompressedLength: Int
        get() = if (isImage) compressedLength + (next shl 16) + compDiff
        else compressedLength + compDiff

    companion object {
        const val TYPE_INTERNAL = 0
        const val TYPE_POPUP = 1
        const val TYPE_EXTERNAL_REF = 2
        const val TYPE_IMAGE = 3
        const val TYPE_SYSTEM = 4
        const val TYPE_REXX_SCRIPT = 5
        const val TYPE_REXX_COMMAND = 6
        const val TYPE_QUIT = 7
        const val TYPE_CLOSE = 8
        const val TYPE_EOF = 255
    }
}
