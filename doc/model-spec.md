# hypp model spec

Source of truth for a future Rust port: every type, field, variant tag and invariant the Kotlin
model (`src/commonMain/kotlin/de/rholambdapi/hypp/`) establishes. A Rust port's own golden JSON,
generated the same way, is checked byte-for-byte against `doc/goldens/*.json` — the canonical JSON
schema in the last section is the contract both implementations must produce identically.

Not a copy of the `.HYP` wire-format spec (that's `doc/format-notes.md` plus the prose spec in
`hypfmt.ui`/`hcpcmds.ui`) — this is the *decoded object model* a parser exposes once the wire bytes
are gone.

## Scalar conventions

- **`NodeIndex`** — a non-negative `Int` (0-based), the file's base-255 encoding decoded. Serializes
  as a plain JSON number; there is no wire representation of it as a type, only as a decoded value.
- **Base-255 fields** (`toc`, `next`/`prev` in nav position, cross-reference targets, image indices,
  line numbers, object-table fields) are two bytes on the wire, low digit first, each biased by +1
  to avoid `NUL`: `value = (hi - 1) * 255 + (lo - 1)`. Decoded before it ever reaches the model —
  every `Int` field downstream of this is the decoded value, never the raw bytes.
- **Byte blobs** (`DataBlock.data`, `Graphic.Image.ditherMask`, `ExtendedHeader.Unknown.data`,
  `ImageNode.pixels`) serialize as lowercase hex strings, no separators, no `0x` prefix.
- **Strings** are decoded through `HypCharset` at parse time — the model never carries raw
  charset-native bytes as a string field.

## `HypCharset` (sealed interface)

`AtariSt` (default), `Latin1`, `Utf8`. `AtariSt` is a fixed 128-entry table for bytes `0x80`-`0xFF`
(ASCII below that); see the table in `HypCharset.kt` for exact glyphs, sourced from the public Atari
ST character-set reference. `byName` resolves an `@charset` extended-header string
(`ATARI`/`ATARIST`/`TOS`, `ISO-8859-1` and aliases, `UTF-8`/`UTF8`), case-insensitively, trimmed;
an unrecognized name falls back to `AtariSt` and records `Diagnostic.UnsupportedCharset`.

## `HypColor` (16-entry enum)

Ordinal == wire palette index, selected by the `0xa5`/`0xa6` foreground/background escapes. Standard
Atari ST/GEM default RGB triples (full-intensity primaries + half-intensity "dark" variants); the RGB
values themselves are not corpus-verified, only the index-to-name mapping is. Order (ordinal 0-15):
`WHITE, BLACK, RED, GREEN, BLUE, CYAN, YELLOW, MAGENTA, LIGHT_GRAY, DARK_GRAY, DARK_RED, DARK_GREEN,
DARK_BLUE, DARK_CYAN, DARK_YELLOW, DARK_MAGENTA`.

## `TextStyle` — packed `Int`

One packed word per span, absolute (not delta) on every attribute escape:

| Bits | Meaning |
|---|---|
| 0-5 | attribute bit-vector: `escape code - 0x64`. Bit 0 bold, bit 1 light, bit 2 italic, bit 3 underlined, bit 4 outlined, bit 5 shadowed |
| 8-11 | foreground `HypColor` ordinal |
| 12-15 | background `HypColor` ordinal |

`TextStyle.Normal` = no attributes, foreground `BLACK` (ordinal 1 at bits 8-11 → raw value `0x100`
= 256), background `WHITE` (ordinal 0, bits 12-15 = 0). The canonical JSON emits the raw packed
`styleBits` int, not decoded booleans — decode per the table above.

## `Header`

`itableSize: u32`, `itableCount: u16`, `compilerVersion: u8`, `compilerOs: u8` — the 12-byte `HDOC`
file header verbatim (after the 4-byte magic).

## `ExtendedHeader` (sealed, tag = `kind`)

- `Charset { name: String }` — id **30**, NUL-terminated descriptor string.
- `Default { name: String }` — id **2**, NUL-terminated name of the node to open first.
- `Unknown { id: Int, data: bytes }` — every other id, raw payload preserved losslessly.

Terminator on the wire is a full `id=0, length=0` pair (4 bytes), not a bare `id=0` — confirmed
empirically, not documented in the prose spec.

## `IndexEntry`

Fields: `len, type, seek, compDiff, next, prev, toc, name, compressedLength` (`compressedLength` is
*derived*, not on the wire — see below). Type constants: `0` internal, `1` popup, `2` external ref,
`3` image, `4` system, `5` REXX script, `6` REXX command, `7` quit, `8` close, `255` EOF sentinel
(excluded from `HypDocument.entries` entirely — never appears in the model or the JSON).

- `hasData` — true only for types 0/1/3 (internal, popup, image); the rest are pure navigation
  entries with no data-region object.
- `isImage` — type == 3.
- `compressedLength`: no length field exists on the wire. It's `nextEntry.seek - thisEntry.seek`,
  or `fileLength - thisEntry.seek` for the last entry. Computed from the *raw* entry list (sentinel
  included, if present) before the sentinel is dropped.
- `uncompressedLength`: `compressedLength + compDiff` for every type **except** image, where `next`
  is overloaded to hold the high bits: `compressedLength + (next << 16) + compDiff`. This is the one
  place `next` is not a navigation link.
- **Raw storage**: when `uncompressedLength == compressedLength`, the object is stored uncompressed
  (the compiler skips lh5 when it wouldn't help) — read directly, no lh5 pass.

## `Link` / `LinkKind`

`LinkKind`: `LINK` (`@{...}`, escapes `0x24`/`0x25`) or `ALINK` (`alink`, `0x26`/`0x27`).
`Link { kind, target: NodeIndex, lineNumber: Int?, label: String }` — `lineNumber` is set only for
the `..._LINE` escape variants (`0x25`/`0x27`); null otherwise. **Invariant: `label` is always
exactly the carrying `Span.text`** — a link never straddles a style change, so there is no separate
"link label" concept distinct from span text. On the wire, a label-length byte of exactly 32
(`LABEL_LENGTH_BIAS`) means "use the target entry's own name" rather than an embedded literal;
`rawLength - 32` otherwise.

## `Span` / `Line`

`Span { text: String, style: TextStyle, link: Link? = null }`. `Line { spans: List<Span> }`, with a
derived `text` (spans' text concatenated, styling/links dropped) not carried in the JSON — recompute
if needed. A line is NUL-terminated on the wire; NUL is a plain terminator except when the current
span's foreground/background byte value happens to be a literal `0x00` byte inside a colour escape's
parameter (line splitting is escape-aware, not a blind split on NUL).

## `CrossReference`

`{ target: NodeIndex, popupText: String }` — prologue item b, escape `0x30`. Spec-documented soft
cap of 12 per node; exceeding it does not truncate, it raises
`Diagnostic.CrossReferenceLimitExceeded` alongside the full list.

## `DataBlock`

`{ type: Int, data: bytes }` — prologue item c, escapes `0x28`-`0x2e` (type = escape byte). `0x2f`
(dithermask) is excluded from this list under normal circumstances — see `Graphic.Image.ditherMask`
— but an orphaned `0x2f` (one not immediately followed by an image escape) surfaces here like any
other type once superseded by a later one, or at end of prologue.

## `ObjectTableEntry`

`{ lineNumber, tree, obj, pageIndex }`, all base-255-decoded `Int`s — prologue item e, escape
`0x31` (`@tree`/`@endtree`). Not corpus-evidenced; implemented from the prose spec only.

## `Graphic` (sealed, tag = `kind`)

Common fields `x, y, width, height` in **character cells** (confirmed empirically against the image
fixtures), plus a derived `centered = (x == 0)`.

- `Image { imageIndex: NodeIndex, ditherMask: bytes? }` — `width`/`height` are present on the wire
  but the format ignores them for images (real files carry 0 for both). `ditherMask` is the payload
  of an immediately-preceding `0x2f` data block, or null.
- `Line { arrowAtStart: Bool, arrowAtEnd: Bool, lineStyle: Int }` — from one flags byte: bit 0
  arrow-at-start, bit 1 arrow-at-end, remaining bits (`flags >> 2`) the line style.
- `Box { fillPattern: Int }`.
- `RoundedBox { fillPattern: Int }` — same shape as `Box`, distinct escape/tag.

## `ImageNode` (type-3 object payload)

Header: `width: u16, height: u16, planeCount: u8, planePresent: u8, planeFilled: u8, filler: u8`
(8 bytes), followed by one bitplane per bit set in `planePresent`, each `ceil(width/16)*2` bytes per
row (word-aligned, MSB-first pixel-per-bit), planes concatenated in ascending order (not
word-interleaved). A plane bit set in `planeFilled` but not `planePresent` means "fully set, no plane
data on the wire" (all-1s, no bytes consumed for it) — implemented from the prose spec, not
corpus-confirmed (every vendored image is single-plane with only `planePresent`, never `planeFilled`
alone).

`pixels: bytes` in the JSON is the fully decoded form: one palette-index byte per pixel, row-major,
plane 0 contributing the low bit of each pixel's index. This is what a Rust port's golden must match
— not the raw plane bytes.

## `Node` (`NodeKind.TEXT` | `NodeKind.POPUP`)

`{ index, name, kind, windowTitle: String?, graphics, crossReferences, dataBlocks, objectTable,
lines }`. Prologue records (items a-e) are **order-independent on the wire** — parsed as a set of
self-identifying, individually-optional records (first byte after `ESC` names the record type), not
a fixed a-e sequence; real files interleave them (e.g. window title before graphics). Prologue parsing
stops at the first byte pair that isn't a recognized prologue escape — that's where the text region
(item f) begins. Escape ranges: window title `0x23`; further-data-blocks `0x28`-`0x2e`; dithermask
`0x2f` (same range, special-cased); cross-reference `0x30`; object-table `0x31`; image `0x32`; line
`0x33`; box `0x34`; rounded-box `0x35`. Text-region escapes occupy a disjoint range (link `0x24`-
`0x27`, text attribute `0x64`-`0xa3`, no-op `0xa4`, fg/bg colour `0xa5`/`0xa6`) — the two ranges never
collide, which is what makes "stop at first unrecognized prologue escape" unambiguous.

A truncated prologue record (not enough bytes left to hold it) stops prologue parsing entirely and
records `Diagnostic.NodeDataOverrun` — the text region's start becomes unknowable, so nothing after
that point is reinterpreted as text.

## `Diagnostic` (sealed, tag = `kind`) — never fatal to opening the document

| kind | fields | raised when |
|---|---|---|
| `UnsupportedCharset` | `name` | `@charset` value not in `AtariSt`/`Latin1`/`Utf8` |
| `DecompressionFailed` | `index` | an internal/popup/image object's lh5 stream failed to decompress; the node/image is omitted from the document |
| `NodeDataOverrun` | `index` | a prologue record ran past the end of decompressed data |
| `CrossReferenceLimitExceeded` | `index`, `count` | a node carried more than 12 cross-reference blocks |
| `UnknownEscape` | `index`, `code` | a text-region `ESC` type byte the format doesn't define |
| `UnterminatedLine` | `index` | node data ended mid-line, without the terminating `NUL`; the partial line is still kept |
| `DanglingNodeReference` | `index`, `target` | a link/cross-reference/image placement named an index outside the entry table; the reference is dropped, any text it carried is kept |

## `HypDocument`

`{ header, extendedHeaders, entries, charset, nodes, images, diagnostics }`.

- `entry(NodeIndex)`, `node(NodeIndex)`, `image(NodeIndex)` — O(1) lookups; `node`/`image` are
  non-null only for their matching entry type (see `IndexEntry.hasData`).
- `defaultNode: NodeIndex?` — the entry named by the `Default` extended header, or null if absent or
  the name doesn't match any entry (dangling).
- `tableOfContents(): TocEntry` — rooted at `NodeIndex(0)` (the format's fixed default), nested by
  grouping every other entry under the entry its `IndexEntry.toc` names. **`toc` is not a tree-parent
  field in the prose spec sense** — `hcpcmds.ui`'s docs describe it as the "Contents" button's jump
  target (defaults to index 0); grouping by it still reconstructs the correct nesting tree in
  practice (verified against `st_guide_orig_en.hyp`'s real multi-level structure). `next`/`prev` are
  the unrelated "Page >"/"Page <" reading-order chain, not part of this tree. A `toc` cycle away from
  the root is broken silently (once an index is placed in the tree it cannot be placed again) rather
  than looping forever.
- `open(bytes): OpenOutcome` — `Failure(InvalidMagic)` if under 4 bytes or the magic isn't `HDOC`;
  otherwise always `Success`, even with any number of diagnostics recorded. **A total parse never
  fails for any reason other than a bad magic/too-short input.**

`TocEntry { index: NodeIndex, children: List<TocEntry> }`.

`OpenOutcome` = `Success(document)` | `Failure(reason)`; `OpenFailure` = `InvalidMagic` (the only
variant in v1).

## Cross-cutting invariants

1. A total parse never fails except on bad magic / under-4-byte input — everything else becomes a
   `Diagnostic`, never an exception or a `Failure`.
2. `Link.label` is always identical to its carrying `Span.text` — never modeled as a separate field
   a consumer could desync from the span.
3. Attribute escapes are absolute, not incremental — a new text-attribute escape replaces the whole
   6-bit vector, it does not toggle individual bits on top of the previous style.
4. `Graphic.centered` is derived (`x == 0`), never a separate wire flag.
5. A dangling reference (link, cross-reference, image placement) is dropped but never silently loses
   the surrounding text/structure — `DanglingNodeReference` is recorded and whatever text existed
   around it is preserved.
6. Unknown extended headers and unknown further-data-block types are preserved losslessly as raw
   bytes (`ExtendedHeader.Unknown`, `DataBlock`), never discarded — a lossless round-trip needs
   nothing beyond what's already captured.
7. The EOF sentinel (index-table type 255) is never exposed as an `IndexEntry` or counted in any
   index — `HypDocument.entries` and every derived index are already sentinel-free.

## Canonical JSON schema

Produced by `CanonicalJson.kt` (`src/commonTest`), exercised by `ParityGoldenTest` (`src/jvmTest`)
against `doc/goldens/*.json`. Pretty-printed, 2-space indent, fields in the order listed here
(never alphabetized, never hash-map order) — a Rust port's writer must match this exactly, key for
key, for the goldens to compare equal.

```
Document   { header, extendedHeaders[], charset, entries[], nodes[], images[], diagnostics[] }
Header     { itableSize, itableCount, compilerVersion, compilerOs }
ExtHeader  { kind: "Charset", name } | { kind: "Default", name } | { kind: "Unknown", id, data(hex) }
Charset    "AtariSt" | "Latin1" | "Utf8"
IndexEntry { index, len, type, seek, compDiff, next, prev, toc, name, compressedLength, uncompressedLength }
Node       { index, name, kind: "TEXT"|"POPUP", windowTitle(nullable),
             graphics[], crossReferences[], dataBlocks[], objectTable[], lines[] }
Graphic    { kind: "Image", imageIndex, x, y, width, height, ditherMask(hex, nullable) }
         | { kind: "Line", x, y, width, height, arrowAtStart, arrowAtEnd, lineStyle }
         | { kind: "Box"|"RoundedBox", x, y, width, height, fillPattern }
CrossRef   { target, popupText }
DataBlock  { type, data(hex) }
ObjTable   { lineNumber, tree, obj, pageIndex }
Line       { spans[] }
Span       { text, styleBits, link(nullable) }
Link       { kind: "LINK"|"ALINK", target, lineNumber(nullable), label }
ImageNode  { index, name, width, height, planeCount, planePresent, planeFilled, pixels(hex) }
Diagnostic { kind, index?, target?, count?, code?, name? } — fields present per variant, see table above
```

`index`/`target`/`imageIndex` fields all serialize as the plain decoded `Int` (`NodeIndex.value`),
never as a wrapped object. Byte arrays serialize as lowercase hex, no prefix/separator. Nullable
fields serialize as JSON `null`, never an omitted key.
