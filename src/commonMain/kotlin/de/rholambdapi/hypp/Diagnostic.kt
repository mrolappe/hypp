package de.rholambdapi.hypp

/**
 * Something the parser noticed that isn't fatal to opening the document. A
 * total parse never fails on account of a diagnostic — see [OpenOutcome].
 */
sealed interface Diagnostic {
    /** An `@charset` name (extended header id 30) not among v1's supported set. */
    data class UnsupportedCharset(val name: String) : Diagnostic

    /** An internal/popup node's `-lh5-` object failed to decompress; the node is omitted from [HypDocument.nodes]. */
    data class DecompressionFailed(val index: NodeIndex) : Diagnostic

    /** A node's prologue record ran past the end of its (decompressed) data; parsing of that node stopped there. */
    data class NodeDataOverrun(val index: NodeIndex) : Diagnostic

    /** A node carried more than the spec's documented maximum of 12 cross-reference blocks. */
    data class CrossReferenceLimitExceeded(val index: NodeIndex, val count: Int) : Diagnostic
}
