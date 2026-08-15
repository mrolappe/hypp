package de.rholambdapi.hypp

/** Whether the source wrote `@{... link ...}` (escapes `0x24`/`0x25`) or `alink` (`0x26`/`0x27`). */
enum class LinkKind { LINK, ALINK }

/**
 * A hypertext reference carried by one [Span]. [target] indexes [HypDocument.entries] — never a
 * `Node` reference, so the model stays an acyclic value tree — and may name an entry of any type,
 * including an external reference, a system/REXX action or a quit/close dummy.
 *
 * [label] is the text the link displays, which is also its span's text: a link never straddles a
 * style change. [lineNumber] is present only for the `LINE` escape variants (`0x25`/`0x27`).
 */
data class Link(
    val kind: LinkKind,
    val target: NodeIndex,
    val lineNumber: Int?,
    val label: String,
)
