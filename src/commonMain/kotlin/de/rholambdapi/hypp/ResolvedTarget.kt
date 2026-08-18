package de.rholambdapi.hypp

/** Where a [NodeIndex] leads, collapsing [HypDocument.entry]/[HypDocument.node]/[HypDocument.image] into one dispatch. */
sealed interface ResolvedTarget {
    data class ToNode(val node: Node) : ResolvedTarget
    data class ToImage(val image: ImageNode) : ResolvedTarget
    data class ToExternalRef(val ref: ExternalRef) : ResolvedTarget
    data class ToSystemAction(val entry: IndexEntry) : ResolvedTarget
    data object Missing : ResolvedTarget
}

/** Resolves [target] to the variant matching its [IndexEntry.type], or [ResolvedTarget.Missing] if out of range. */
fun HypDocument.resolve(target: NodeIndex): ResolvedTarget {
    val entry = entry(target) ?: return ResolvedTarget.Missing
    return when (entry.type) {
        IndexEntry.TYPE_INTERNAL, IndexEntry.TYPE_POPUP -> ResolvedTarget.ToNode(node(target)!!)
        IndexEntry.TYPE_IMAGE -> ResolvedTarget.ToImage(image(target)!!)
        IndexEntry.TYPE_EXTERNAL_REF -> ResolvedTarget.ToExternalRef(entry.externalRef())
        else -> ResolvedTarget.ToSystemAction(entry)
    }
}
