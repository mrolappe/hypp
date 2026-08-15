package de.rholambdapi.hypp

/**
 * One `@tree`/`@endtree` object-table entry (prologue item e, escape `0x31`) — not
 * corpus-evidenced (no vendored file uses `@tree`), implemented from the prose spec only; see
 * `doc/format-notes.md`.
 */
data class ObjectTableEntry(val lineNumber: Int, val tree: Int, val obj: Int, val pageIndex: Int)
