package de.rholambdapi.hypp

/** Where a [NodeIndex] leads, collapsing [HypDocument.entry]/[HypDocument.node]/[HypDocument.image] into one dispatch. */
sealed interface ResolvedTarget {
    data class ToNode(val node: Node) : ResolvedTarget
    data class ToImage(val image: ImageNode) : ResolvedTarget
    data class ToExternalRef(val ref: ExternalRef) : ResolvedTarget
    data class ToSystemAction(val entry: IndexEntry) : ResolvedTarget
    data object Missing : ResolvedTarget
}

/**
 * Resolves [target] to the variant matching its [IndexEntry.type], or [ResolvedTarget.Missing] if
 * out of range — or if the entry's own object never parsed. A malformed file can carry an entry
 * typed as internal/popup/image whose compressed data fails to decompress
 * ([Diagnostic.DecompressionFailed]), which leaves it in [HypDocument.entries] with no [Node]/
 * [ImageNode] behind it; that is [ResolvedTarget.Missing] too, not a crash for every caller to
 * guard against separately.
 */
fun HypDocument.resolve(target: NodeIndex): ResolvedTarget {
    val entry = entry(target) ?: return ResolvedTarget.Missing
    return when (entry.type) {
        IndexEntry.TYPE_INTERNAL, IndexEntry.TYPE_POPUP ->
            node(target)?.let(ResolvedTarget::ToNode) ?: ResolvedTarget.Missing
        IndexEntry.TYPE_IMAGE -> image(target)?.let(ResolvedTarget::ToImage) ?: ResolvedTarget.Missing
        IndexEntry.TYPE_EXTERNAL_REF -> ResolvedTarget.ToExternalRef(entry.externalRef())
        else -> ResolvedTarget.ToSystemAction(entry)
    }
}
