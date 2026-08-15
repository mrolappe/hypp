# Phase 5 — Node prologue

**State: green.**

## Completed

- `NodeIndex` — a `@JvmInline value class` wrapping a non-negative `Int`,
  the format's 0-based index into `HypDocument.entries`/`nodes`.
- `Graphic` — sealed interface (`Image`, `Line`, `Box`, `RoundedBox`) for
  prologue item a). `x`/`y`/`width`/`height` in character cells, `x == 0`
  meaning centred. `Image` carries `imageIndex` and an optional
  `ditherMask`; `Line` carries `arrowAtStart`/`arrowAtEnd`/`lineStyle`;
  `Box`/`RoundedBox` carry `fillPattern`.
- `CrossReference` — prologue item b), `target: NodeIndex` + `popupText`.
- `DataBlock` — prologue item c) for escapes `0x28`-`0x2e` (and an orphaned
  `0x2f`), captured generically as `(type, data)`.
- `ObjectTableEntry` — prologue item e), the four base-255 fields per the
  prose spec (`@tree`/`@endtree`).
- `Node` — `index`, `name`, `kind` (`NodeKind.TEXT`/`POPUP`), `windowTitle`,
  `graphics`, `crossReferences`, `dataBlocks`, `objectTable`, and
  `textBytes` (the still-undecoded item f remainder — line/span parsing is
  phase 6).
- `parseNode` (internal, `Node.kt`) — parses the prologue as a loop over
  self-identifying `ESC`-tagged records (see Decisions), dispatching by
  type, stopping at the first byte that isn't a recognized prologue escape.
- `HypDocument.nodes: List<Node>` — populated in `open()` for every
  `TYPE_INTERNAL`/`TYPE_POPUP` index entry, decompressing via the existing
  `Lh5.decompress` and calling `parseNode`. Type 3 (image) and the
  data-less types (2, 4-8) are excluded — images are phase 7's job.
- Three new `Diagnostic` variants, all carrying a `NodeIndex` (no generic
  `Location` type introduced — see Decisions):
  `DecompressionFailed` (an entry's `-lh5-` object failed to decompress; the
  node is omitted from `nodes`), `NodeDataOverrun` (a prologue record ran
  past the end of the node's data; parsing of that node's prologue stops
  there, whatever was parsed so far is kept), `CrossReferenceLimitExceeded`
  (more than the spec's documented 12 cross-references on one node).

## Decisions

- **Prologue records are parsed as an unordered, self-identifying set, not
  a fixed a-e sequence.** The prose spec's enumeration order doesn't match
  real files (`hcp_orig_en.hyp`'s first node has its window title before
  its graphics). See `doc/format-notes.md`.
- **No padding byte after a window title**, despite the prose spec's
  "possible fill-byte" wording — empirically never present across 29 real
  examples. See `doc/format-notes.md`.
- **Base-255 decode formula nailed down**: `(hi - 1) * 255 + (lo - 1)` for
  stream bytes `lo, hi` in that order. Confirmed against every base-255
  field the graphics fixtures exercise (image index, Y-offset) and against
  `hcp_orig_en.hyp`'s cross-reference target indices and window title. See
  `doc/format-notes.md`.
- **Dithermask (`0x2f`) and object table (`0x31`) have no corpus
  evidence anywhere in the vendored corpus** — implemented from the prose
  spec only, tested with hand-constructed bytes rather than a real fixture.
  See `doc/format-notes.md`.
- **Line/box/rbox `Data`-byte bit decomposition doesn't match `lines.hyp`'s
  descriptive filenames.** Implemented literally per the prose spec
  (bit0/bit1/rest) rather than reverse-engineered from the labels, which
  don't cleanly correspond to the decoded flags. See `doc/format-notes.md`.
- **No `Diagnostic.location`/`Location` wrapper type introduced.** The
  plan's domain-model sketch has every `Diagnostic` carry a `location`;
  this phase's new variants carry a plain `NodeIndex` field directly
  instead, since a generic `Location` (nodeIndex + byteOffset) has no use
  yet — `UnsupportedCharset` (phase 4) is document-scoped and still has no
  location at all. Revisit if a diagnostic ever needs a byte offset within
  a node.
- **`Node.textBytes` is a raw, undecoded `ByteArray`**, not the plan
  sketch's final `lines: List<Line>` — line/span/escape parsing within the
  text region is phase 6's scope, deliberately not started early.

## Tests added

All green on `jvm`/`wasmJs`/`wasmWasi` (33 tests total in the suite now),
in `NodeTest.kt`:

- Four fixtures from the plan's phase-5 red test — `image.hyp`,
  `limage.hyp`, `limage2.hyp`, `lines.hyp` — each asserted for exact
  `x`/`y`/`width`/`height`/`imageIndex`/`centered`, including
  `lines.hyp`'s two `RoundedBox`, two `Box` and ten `Line` placements with
  exact fields.
- `hcp_orig_en.hyp` node 0 ("Main") — exact `windowTitle`, exact 3-entry
  `crossReferences` list (target index + popup text), graphics count, and
  that `textBytes` starts exactly where the first non-prologue escape
  (`0x24`, link) begins — the integration test against a real document that
  `doc/LEARNINGS.md` (phase 2) says not to skip.
- Every `TYPE_INTERNAL`/`TYPE_POPUP` entry in `hcp_orig_en.hyp` produces a
  `Node` of the matching `kind` and `name`.
- Three hand-constructed-bytes unit tests via direct `parseNode` calls
  (no real fixture exists for these): a dithermask immediately followed by
  an image attaches to it; a dithermask not followed by an image surfaces
  as a `DataBlock`; more than 12 cross-references raises
  `CrossReferenceLimitExceeded`; a truncated window-title record (no NUL
  before the data ends) raises `NodeDataOverrun` and stops parsing cleanly
  rather than throwing.

## Remaining

- `Node.textBytes` is raw bytes — phase 6 replaces it with `lines: List<Line>`
  built from NUL-terminated line splitting, `ESC ESC` literal-escape
  handling, link/alink parsing, and the absolute attribute bit-vector —
  using `document.charset.decode(...)` per phase 4's note.
- The `0xa4` typewriter vs `0xa5`/`0xa6` colour range overlap (noted in the
  plan) is still unresolved — deferred to the wild sweep as planned.
- `windowTitle`/cross-reference popup text are decoded with the same raw
  Latin-1-passthrough `decodeName()` used for index-entry names, not
  `document.charset.decode(...)` — consistent with treating them as
  structural strings like entry names rather than document prose. Revisit
  alongside phase 6 if that turns out to be the wrong call for a non-ASCII
  window title.
