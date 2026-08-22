# hypp — concepts

The domain model hypp exposes, independent of exact Kotlin signatures (see
`api.md` for those). Read this to build a correct mental model before
writing code against the library.

## The shape of a document

```
HypDocument
├─ header              (file-level metadata: index table size, compiler version/OS)
├─ extendedHeaders      (@charset, @default, and unrecognized ids, losslessly)
├─ entries              (the index table: one IndexEntry per addressable "page")
├─ nodes                (parsed content for entries of type INTERNAL/POPUP)
├─ images               (parsed content for entries of type IMAGE)
├─ charset               (resolved once, applied to every node's text)
└─ diagnostics           (everything non-fatal the parser noticed)
```

`entries`, `nodes` and `images` are three different views of overlapping
data, not three independent lists to reconcile yourself:

- `entries` is the complete index table — every page the file knows about,
  regardless of type (internal text, popup, image, external reference,
  system action, REXX script/command, quit). Its position (`0`, `1`, `2`,
  …) is the `NodeIndex` every other part of the model uses to refer to it.
- `nodes` holds the parsed content for entries of type `INTERNAL` or
  `POPUP` only — the two types that carry text.
- `images` holds the parsed content for entries of type `IMAGE` only.
- An entry of any other type (external ref, system, REXX, quit) has no
  corresponding `Node`/`ImageNode` — it's still listed in `entries` because
  a link can still target it, but there's no content to parse.

`HypDocument.entry(index)` / `.node(index)` / `.image(index)` are how you
go from a `NodeIndex` to whichever of these views applies — see `api.md`.

## Everything is indexed, nothing is a direct reference

Links, cross-references, image placements and the table-of-contents parent
relation all point at other pages via `NodeIndex` — a plain integer wrapper
— never via a direct object reference. Two consequences:

1. **The model can't have reference cycles** even though the *document*
   graph can (a table of contents that (incorrectly) points back at
   itself, for instance) — resolving a `NodeIndex` is always an explicit,
   nullable lookup (`document.node(index)`), never a stored pointer that
   could create a Kotlin object cycle or force eager resolution.
2. **An index can dangle.** Nothing stops a source `.hyp` file from
   referencing an index that doesn't exist in its own table, or that names
   an entry type with no content of the kind you're expecting. hypp doesn't
   guess or fail in that case — it drops the reference and records a
   `Diagnostic.DanglingNodeReference`, keeping whatever text/label
   accompanied it. Never assume a link's `target` resolves; always go
   through the document's lookup and handle `null`.

## The parse never fails past the container check

Opening a document is exactly one binary decision:
`OpenOutcome.Success` or `OpenOutcome.Failure(InvalidMagic)` — the *only*
way `open()` fails is "this isn't a `.HYP` file" (bad/missing `HDOC`
magic). Every other thing that can be wrong with a real file — a corrupt
compressed object, a node whose prologue runs past its own data, an escape
code the format doesn't define, a dangling reference, too many
cross-references, a line that never got its terminating NUL — is instead
recorded as one entry in `document.diagnostics` and parsing continues
around it. This was a deliberate design goal, verified by opening all 702
files in the public wild corpus with zero crashes and zero unhandled
failures (`doc/progress/phase-11-wild-sweep.md`).

Practically: **check `diagnostics` if you care about data quality, but
never wrap `HypDocument.open()` in a try/catch expecting parse errors** —
there aren't any short of the one `OpenFailure` case.

## Nodes: prologue + text region

A `Node` (an internal page or a popup) has two parts, both already parsed
by the time you see it:

- **Prologue** — everything that isn't running text: an optional window
  title, placed graphics (images, lines, boxes, rounded boxes),
  cross-reference blocks, opaque "further data blocks," and object-table
  entries. These arrive in the model as separate lists
  (`node.graphics`, `node.crossReferences`, `node.dataBlocks`,
  `node.objectTable`) — the wire format's escape-tagged, no-fixed-order
  encoding is fully resolved by parse time; you never see raw escape bytes.
- **Text region** — `node.lines`, a `List<Line>`, each holding a flat
  `List<Span>`. A run of text keeps one `TextStyle` (bold/light/italic/
  underlined/outlined/shadowed, plus foreground/background colour) for its
  whole span; styling never straddles a `Line` boundary, and a link never
  straddles a style change (a linked span's text *is* the link's label,
  never a mix of linked and unlinked text in one span).

## A node is a fixed-grid canvas, not free text

`node.lines`/`node.graphics` look like two independent lists, but they
share one coordinate system: every `Graphic`'s `x`/`y` are character-cell
row/column positions into the *same* grid `node.lines` renders as text.
Treat a node as a fixed-width character-cell canvas that text and
graphics both draw onto, not as a text stream with decorations bolted on
the side — three consequences follow, each a real bug hit and fixed while
building `hypp-cli`'s renderers (`doc/PROGRESS.md`'s "Root-cause and
fidelity fixes" entry):

- **Whitespace is data.** Indentation and inter-column gaps are literal
  runs of space characters positioned by the grid, not incidental
  formatting a renderer can collapse. A consumer that renders `node.lines`
  through anything that collapses whitespace or uses a proportional font
  (HTML's default text flow, a GUI label) will visibly misalign multi-column
  layouts. `hypp-cli`'s `HtmlSpans.HTML_BODY_STYLE` fixes this with
  `white-space:pre-wrap;font-family:monospace`.
- **A graphic is positioned by row, not layered above/below the text.** A
  `Graphic`'s `y` says which text row it decorates; rendering all graphics
  as one block before or after the text (instead of interleaved at each
  graphic's own row) puts every decoration next to the wrong line.
  `hypp-cli`'s `HtmlSpans.graphicsByRow` buckets graphics by `y` and
  interleaves each row's markup immediately before that row's text.
- **A transform that changes row count must carry graphics' positions
  forward.** Anything that merges, drops, or reorders lines (word-wrap
  reflow, line-range extraction, a future line-numbering filter) shifts
  every row after the change point — a `Graphic` left pointing at its
  pre-transform row now decorates the wrong text. `hypp-cli`'s
  `Reflow.kt` handles this for its own `--reflow` paragraph-joining
  transform: `reflowWithRowMap` returns the original-row → new-row mapping
  alongside the reflowed lines, and `Graphic.remappedTo` carries every
  graphic's `y` (and, for a graphic that spans multiple rows — every
  `Graphic` type's `height`, a 1-based row *count* including
  `Graphic.Line`'s — its row *span*, not just its start row) forward
  through that mapping rather than leaving it stale. Any future
  row-count-changing transform needs the same kind of mapping, not just a
  straight index copy.

## Styling is absolute, not incremental

A style-change escape in the wire format replaces the *entire* attribute
set, it doesn't toggle one bit on top of whatever was active. `TextStyle`
mirrors that: each `Span`'s style is a complete, self-contained snapshot
(bold/light/italic/underlined/outlined/shadowed booleans plus resolved
foreground/background `HypColor`), never a delta you'd need to apply on top
of a previous span's style to get the right rendering.

## Charset resolution happens once, up front

A document's `@charset` extended header (if present) is resolved into a
`HypCharset` exactly once, before any node is parsed, and that one
resolved charset decodes every node's text uniformly. An unrecognized
charset name doesn't fail the parse — it falls back to the format's own
default (Atari ST) and records `Diagnostic.UnsupportedCharset`, so you
always get *a* consistent decoding, possibly the wrong one for that
specific file, flagged so you can tell.

## Images: planes in, RGBA out, decoding is lazy

An `ImageNode` stores the format's native representation — one bitplane
per bit set in `planePresent`, word-aligned rows — and exposes
`pixels: ByteArray` (one palette index per pixel, decoded and memoized
lazily on first access) plus `toRgba(palette)` for a ready-to-display
byte buffer. You don't need to understand bitplanes to consume an image;
`toRgba()` with the default `Palette.AtariSt` is the normal path. The
default palette's RGB values follow the standard Atari ST/GEM convention
but aren't independently corpus-verified (no vendored file carries a
rendering oracle) — see `doc/format-notes.md` if exact colour fidelity
matters to your use case.

## The table of contents is a derived tree, not a stored one

There's no single "toc" field in the wire format — `HypDocument
.tableOfContents()` builds the tree on demand from every entry's `toc`
field (which names that entry's structural parent), rooted at index 0 (the
format's fixed "physically first page" convention). It's a plain
recursive structure (`TocEntry(index, children)`) safe to walk even
against a malformed source file — a cycle away from the root is broken
silently by construction, never an infinite loop.

## Diagnostics are data, addressed by node index

Every `Diagnostic` variant that concerns a specific node carries that
node's `NodeIndex`, so you can correlate "what went wrong" back to "in
which page" without re-deriving anything. The one exception is
`UnsupportedCharset`, which is document-wide (charset resolution happens
before any node exists to attach the diagnostic to). See `api.md` §
Diagnostic for the full list and what triggers each one.

## The JS façade

`wasmJs`'s `@JsExport` surface (`HyppJs.kt`) is a deliberately different
shape from the Kotlin API described above — not a 1:1 mirror. Kotlin/Wasm's
`@JsExport` (as of this project's pinned Kotlin version, 2.4.10) doesn't
support exporting arrays or classes across the JS boundary, so the façade
flattens everything into a handle-based, C-style API: `hyppOpen()` returns
an integer handle instead of a `HypDocument` object; every other function
takes that handle plus plain integer indices (node index, line number,
span number, …) and returns a primitive or a `String`. `*Count` functions
(`hyppNodeLineCount`, `hyppLineSpanCount`, …) bound the loops a consumer
would otherwise get for free by iterating a `List`. Out-of-range indices
return a sentinel (`-1` for absent Int/Boolean, `""` for absent String)
rather than throwing — JS callers check the sentinel instead of
catching an exception. See `api.md` § "JS façade" for the full function
list, and `overview.md`'s JavaScript quick start for a worked example.
