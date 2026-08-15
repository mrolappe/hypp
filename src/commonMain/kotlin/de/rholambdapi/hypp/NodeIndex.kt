package de.rholambdapi.hypp

/** A 0-based index into [HypDocument.entries] / [HypDocument.nodes], as carried by the file's base-255 encoding. */
@kotlin.jvm.JvmInline
value class NodeIndex(val value: Int) {
    init {
        require(value >= 0) { "NodeIndex must be non-negative, was $value" }
    }
}
