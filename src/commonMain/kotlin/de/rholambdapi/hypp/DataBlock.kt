package de.rholambdapi.hypp

/**
 * A "further data block" (prologue item c, escapes `0x28`-`0x2e`) whose semantics this
 * library doesn't otherwise interpret. `0x2f` (the dithermask escape) is excluded — when one
 * immediately precedes an image escape it is attached to that [Graphic.Image] instead, per the
 * prose spec's "immediately precedes its image command"; an orphaned `0x2f` (none in the
 * vendored corpus) surfaces here like any other type.
 */
data class DataBlock(val type: Int, val data: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is DataBlock && type == other.type && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * type + data.contentHashCode()
}
