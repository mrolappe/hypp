package de.rholambdapi.hypp

/**
 * A node in the document's table of contents, derived from [IndexEntry.toc] (`@toc`'s "Contents"
 * jump target — see `doc/format-notes.md`): [children] are every entry whose own `toc` names
 * [index], in index-table order. The root, returned by [HypDocument.tableOfContents], is always
 * [NodeIndex] 0 — the format's fixed "physically first page" default.
 */
data class TocEntry(val index: NodeIndex, val children: List<TocEntry>)
