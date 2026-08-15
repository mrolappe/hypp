package de.rholambdapi.hypp

/**
 * An extended header record (`id`, raw `data`). Semantic variants (charset,
 * language, ...) are added as more of the format is implemented; every id is
 * captured here regardless, so unknown ids are skipped without loss.
 */
sealed interface ExtendedHeader {
    val id: Int

    data class Unknown(override val id: Int, val data: ByteArray) : ExtendedHeader {
        override fun equals(other: Any?): Boolean =
            other is Unknown && id == other.id && data.contentEquals(other.data)

        override fun hashCode(): Int = 31 * id + data.contentHashCode()
    }

    /** `@charset` — id 30, a NUL-terminated charset descriptor string (e.g. "atarist"). */
    data class Charset(val name: String) : ExtendedHeader {
        override val id: Int get() = ID

        companion object {
            const val ID = 30
        }
    }
}
