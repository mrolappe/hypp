# Phase 7 — Images

**State: green.**

## Completed

- `HypColor` gained `red`/`green`/`blue` (0..255) per entry, the standard
  Atari ST/GEM default palette convention (full-intensity primaries,
  half-intensity "dark" variants). See `doc/format-notes.md`.
- `Palette` (`commonMain`) — wraps a `List<HypColor>`; `colorAt(index)` falls
  back to black past the end. `Palette.AtariSt` is the format's 16-entry
  default.
- `ImageNode` (`commonMain`) — `width`, `height`, `planeCount`,
  `planePresent`, `planeFilled`, lazily-memoised `pixels: ByteArray` (one
  palette index per pixel, row-major), and `toRgba(palette = Palette.AtariSt)`.
  Plane decoding honours `planePresent` (data follows) and `planeFilled`
  (expands to all-1 without consuming any data); a plane that is neither
  contributes 0.
- `parseImage` (internal, `ImageNode.kt`) — reads the 8-byte header
  (`width:u16, height:u16, planeCount:u8, planePresent:u8, planeFilled:u8,
  filler:u8`) and hands the rest to `ImageNode`.
- `HypDocument.images: List<ImageNode>` — type-3 entries decompressed and
  parsed the same way as `nodes` (including the phase-6 uncompressed/`lh5`
  branch, now shared via a local `decompress` helper instead of duplicated).
  Index is the entry's position in `entries`, same convention as `nodes`.
- `hyp2html` (`commonTest`) — the phase's required integration consumer:
  extends `hyp2text`'s walk with inline styled/coloured spans (`<b>`/`<i>`/
  `<u>`/inline `color:`/`background-color:`) and embeds each placed image as
  a `data:image/bmp;base64,...` URI via a ~25-line hand-rolled uncompressed
  24-bit BMP encoder — no compression library needed for a by-eye check.

## Decisions

- **The image header's `width`/`height` are authoritative despite being
  annotated "(will be ignored)" in the prose spec.** Confirmed by an exact
  byte-count match across all four vendored images, then by decoding
  `image.hyp` end-to-end into a legible "Ardi Soft" logo. See
  `doc/format-notes.md`.
- **Planes are stored sequentially, not word-interleaved** — the prose
  spec's "1st plane / optional 2nd plane / ..." enumeration read literally.
  Plane 0 contributes the pixel index's low bit. **Not corpus-confirmed**:
  every vendored image is single-plane. Covered by hand-written synthetic
  tests only; deferred to the phase-11 wild sweep alongside the dithermask
  and `0xa4` overlap.
- **Pixel bits are MSB-first per byte**, matching the format's big-endian
  convention elsewhere; confirmed by the same end-to-end logo render.
- **ST palette RGB values are a documented convention, not spec- or
  corpus-derived** — `colors.hyp` confirmed *names* (phase 6), not colours.
  See `doc/format-notes.md`.
- **`decompress` extracted as a local function in `HypDocument.open`**,
  shared between `nodes` and the new `images` — same compDiff-driven
  raw-vs-lh5 branch, now written once.
- **BMP over PNG for `hyp2html`'s embedded images.** No compression, no zlib
  dependency, ~25 lines for a header + raw bottom-up 24-bit rows — the
  by-eye cross-check doesn't need a smaller file, just a correct one.

## Tests added

All green on `jvm`/`wasmJs`/`wasmWasi` — 58 tests in the suite now (was 51):
+4 `ImageNodeTest`, +3 `Hyp2HtmlTest`.

`ImageNodeTest.kt`:

- `image.hyp`/`limage.hyp`: both decode `rtr_logo.img` to 216×177,
  1 plane, `planePresent = 1`, `pixels.size == width*height`.
- `limage2.hyp`: both images (`select_box.img` 66×32, `title.img` 82×18) in
  index-table order.
- Hand-constructed bytes: a present plane's bits decode MSB-first with row
  padding beyond `width` ignored; a filled-but-absent plane expands to all
  1s and combines correctly with a present plane's bits; `toRgba` resolves
  pixel indices through the default palette.

`Hyp2HtmlTest.kt` — the integration suite:

- `image.hyp`'s placed image appears as a correctly-dimensioned `<img>` with
  a `data:image/bmp;base64,` source.
- `colors.hyp` renders a coloured span as an inline `color:rgb(...)` style.
- Both real documents (`hcp_orig_en.hyp`, `st-guide_orig_en.hyp`) render
  end to end without crashing.

Ad hoc verification (not a checked-in test): decoded `image.hyp` through
`hyp2html`, extracted the embedded BMP, and viewed it — a clean, legible
"Ardi Soft" logo. Recorded as evidence in `doc/format-notes.md` rather than
as an automated assertion (no pixel-exact oracle to assert against).

## Remaining

- Multi-plane images (`planeCount > 1`) and any real file with
  `planeFilled != 0` are untested against real data — phase 11.
- `Palette` has no public way to construct a non-default instance from a
  document's own colours (nothing in the format currently carries a custom
  palette; `Palette.AtariSt` is the only vendored need).
- `hyp2html`'s image lookup (`document.images.firstOrNull { it.index ==
  graphic.imageIndex }`) is a linear scan — fine for a test consumer;
  phase 8's `HypDocument.image(index)` accessor should replace it with
  something better than `O(n)`.
