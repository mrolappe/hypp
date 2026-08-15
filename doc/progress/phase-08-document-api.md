# Phase 8 — Document API

**State: green.**

## Completed

- `HypDocument.entry(index)` / `node(index)` / `image(index)` — O(1) accessors
  (the latter two backed by lazily-built `Map<NodeIndex, _>`s) replacing the
  linear `firstOrNull { it.index == ... }` scans used ad hoc until now (e.g.
  `hyp2html`'s image lookup, updated to use `document.image(...)`).
  `entry(index)` is the general-purpose one: it resolves *any* index-table
  entry, including the five types with no data region (external ref, system,
  rexx script/command, quit, close) that `node`/`image` correctly return null
  for.
- `ExtendedHeader.Default` — id 2 (`@default`), a NUL-terminated node name.
  `HypDocument.defaultNode: NodeIndex?` resolves that name against `entries`;
  null when the header is absent (the common case — neither vendored real
  document sets it) or when the name doesn't match any entry.
- `TocEntry` (new file) + `HypDocument.tableOfContents(): TocEntry` — a tree
  rooted at `NodeIndex(0)`, built by grouping `entries` on `IndexEntry.toc`
  (each entry's own `toc` names its parent; the format's fixed root is always
  index 0). Cycle-safe: a global `visited` set means an index can only be
  placed in the tree once, so a `toc` cycle that never reaches the root
  (malformed/hostile input) is silently orphaned rather than recursed into
  forever.

## Decisions

- **`IndexEntry.toc` is `@toc`'s "Contents button" jump target, not a literal
  parent-pointer field** — confirmed from `hcpcmds.ui`'s `@toc` documentation:
  it defaults to 0 ("the physically first page") and is only overridden by an
  explicit `@toc <name>` in the source, used "to create and manage several
  tables of contents". Grouping entries by this field still produces the
  correct nesting tree in practice (verified against `st-guide_orig_en.hyp`'s
  real structure: index 5 "Symbol bar" groups indices 16–28, and within that
  index 27 "Extra popup" further groups 29–36) because a group's own contents
  page *is* its parent in the tree sense. `next`/`prev` were **not** needed for
  either grouping or sibling order — within a `toc` group, `entries` table
  order already matches next/prev order in every corpus file checked; they
  turned out to encode the separate "Page >" / "Page <" reading chain
  (confirmed via `hcpcmds.ui`'s `@next`/`@prev` docs), not tree structure.
- **Extended header id 9 (`HYP_EXTH_TREEHEADER`) is not parsed into its own
  semantic variant.** Its payload is a bit-vector of which pages have an
  explicit `@title` — but that's exactly what `Node.windowTitle != null`
  already tells us once a node is parsed, which hypp always does eagerly.
  The header exists so an index-only reader (one that hasn't decompressed
  every node) can answer the same question without doing so; hypp has no such
  mode, so it's redundant here. It still round-trips correctly as
  `ExtendedHeader.Unknown(9, ...)` via the existing skip-unknown path — no
  data is lost, just not specially interpreted. Revisit if a lazy/index-only
  reading mode is ever added.
- **No new `Diagnostic` for a dangling or cyclic `toc` reference.** Neither
  case has any corpus evidence, `tableOfContents()` already can't crash on
  either (out-of-range parents just never get a child list; the `visited`
  guard defuses cycles), and nothing currently consumes such a diagnostic.
  Deferred, not designed out — `IndexEntry.toc` is preserved raw, so a future
  diagnostic can be added without a model change.
- **`DocumentTest.kt` gained a small hand-rolled `.hyp` builder** (header +
  index table + extended headers, all node bodies deliberately empty so every
  entry's derived `compressedLength` is 0 and no data region is needed at
  all) for the two cases neither vendored corpus file exercises: an `@default`
  header present, and a `toc` cycle. Real corpus data (`st-guide_orig_en.hyp`)
  is used for the nesting test itself, since it already contains a genuine
  multi-level `@toc` structure.

## Tests added

All green on `jvm`/`wasmJs`/`wasmWasi` — 65 tests in the suite now (was 58):
+7 `DocumentTest`.

`DocumentTest.kt`:

- `entry`/`node`/`image` return the right thing for the right entry type, and
  `null` uniformly out of range.
- `linkattr.hyp`'s "Exit" link target (index 9, a type-7 quit dummy entry,
  already asserted resolving its *label* in `TextTest`) resolves via
  `entry()` — `node()`/`image()` correctly return null for it, since quit
  entries carry no data.
- `defaultNode` is null for both real documents (neither sets `@default`);
  a synthetic document with the header present resolves it; a synthetic
  document with the header naming a nonexistent page yields null rather than
  failing.
- `st-guide_orig_en.hyp`'s real `@toc` grouping nests exactly as the format
  intends: "Symbol bar" → 13 children including "Extra popup" → its own 8
  children; a leaf has no children of its own.
- A synthetic two-entry mutual `toc` cycle (neither pointing at the root) is
  silently orphaned rather than looping.

## Remaining

- Phase 9 (JS façade) is next per the plan; `entry`/`node`/`image`/
  `defaultNode`/`tableOfContents()` are the shape it needs to flatten for
  `wasmJs` export.
