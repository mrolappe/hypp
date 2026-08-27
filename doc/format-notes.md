# hypp — format spec gaps and resolutions

Recorded when the prose spec (`hypfmt.ui`) and `hyp.h` are silent, ambiguous,
or contradicted by real files. Each entry states the resolution taken and the
evidence for it.

## Extended-header terminator is a full 4-byte pair, not a bare `id=0`

**Gap:** `hypfmt.ui` says the extended-header list is "id:u16, length:u16,
data[] — terminated by id 0" but doesn't say whether the terminator itself
still carries a (zero) length field.

**Resolution:** the terminator is `id=0, length=0` — a full 4-byte pair, with
no data following. Parsing must always read the `length` u16 immediately
after `id`, even when `id == 0`, then stop without reading `data`.

**Evidence:** in both `empty.hyp` and `textattr.hyp`, treating the terminator
as a bare 2-byte `id=0` leaves a stray 2-byte gap (`00 00`) between the end
of the extended headers and the first index entry's recorded `seek` offset.
Reading the terminator as the full 4-byte `id=0, length=0` pair consumes
exactly those bytes and the reader position lands precisely on `seek` in
both files (110 in `textattr.hyp`, and end-of-file with no data region at
all in `empty.hyp`, which has zero real nodes).

## `itableCount`'s trailing type-255 EOF sentinel is not always present

**Gap:** the two tiny hand-verified corpus files (`empty.hyp`, `textattr.hyp`)
both end their index table with an explicit type-255 sentinel entry whose
`seek` equals the file length, which is what makes "derived object length =
seek[i+1] − seek[i]" work at the boundary.

**Resolution:** don't assume a sentinel exists. Real files (e.g.
`hcp_orig_en.hyp`, 124 index entries) end their index table with an ordinary
entry — no type-255 record at all. The general rule: an entry's derived
length is `(next entry's seek, or the file's total byte length if this is
the last entry) − this entry's seek`. When a sentinel is present, its `seek`
already equals the file length, so the two cases produce the same result;
the sentinel is just a redundant, optional way of stating it.

## `-lh5-`'s window is 8 KiB, not 16 KiB — so there are 14 offset codes

**Gap:** public secondary descriptions of LHA disagree about `-lh5-`'s sliding
window size. HandWiki's LHA page says 16 KiB; the `lha(1)` manual page and the
Entropymine format notes say 8 KiB (with 16/32/64 KiB belonging to `-lh6-` and
`-lh7-`). The number is load-bearing: the offset Huffman tree has
`windowBits + 1` symbols, whose count is transmitted in `PBIT` bits, so getting
it wrong misparses every block header, not just long matches.

**Resolution:** 8 KiB, i.e. 13 window bits, 14 offset codes, and a 4-bit count
field for the offset code-length list.

**Evidence:** with those values all 184 data-bearing nodes across
`hcp_orig_en.hyp` (106) and `st-guide_orig_en.hyp` (78) decode to exactly the
uncompressed length derived independently from the index table
(`compressedLength + compDiff`, plus the `next` overload on the 17 image
entries), consuming their compressed region without overrunning it. A wrong
window size cannot survive even one block: the count field width shifts and the
first tree fails to reconstruct.

## A data-region object is a bare lh5 stream, with no per-object header

**Gap:** the prose spec describes the data region as "lh5-compressed objects at
each entry's seek offset" without saying whether an object begins with any
length, checksum, or method tag of its own (an LHA *archive* would have a full
file header there).

**Resolution:** there is none. The bytes at `seek` are the raw `-lh5-` bit
stream, and the object's uncompressed size comes only from the index entry
(`compressedLength + compDiff`, plus `next << 16` for images).

**Evidence:** decoding from byte 0 of the region yields a valid first block in
every one of the 184 data-bearing nodes of the two real documents; for
`textattr.hyp`'s "Main" node it yields exactly the 293 bytes the index entry
predicts, and those bytes read as the document's German prose with the spec's
`0x64`-based absolute attribute escapes in the expected places.

## `blockSize == 0` is not reachable in practice; treated as malformed

**Gap:** each lh5 block starts with a 16-bit symbol count, and the format gives
no interpretation for zero. Implementations in the wild differ (no symbols /
65536 symbols / end of data), which is documented publicly as a real
incompatibility between LHA implementations.

**Resolution:** treat it as a malformed stream and fail the decode. Reading it
as "no symbols" makes the decoder loop forever on a truncated or corrupt input,
which is the failure mode that actually matters for a hostile file.

**Evidence:** no block in the vendored corpus has a zero size — every node in
both real documents decodes fully — so nothing is lost by rejecting it. Revisit
if the phase-11 wild sweep turns one up.

**Evidence:** `hcp_orig_en.hyp`'s last two index entries (types 2, external
reference) both have `seek == 57785` (the exact file size, `next`/`prev`/
`toc` all zero) with no trailing type-255 entry after them; `itableCount`
(124) already accounts for all of them, no more, no less.

## Extended header id 30 (`@charset`) is a name string, not `hyp.h`'s numeric enum

**Gap:** `hyp.h` (in-bounds as constants only) defines `HYP_EXTH_CHARSET = 30`
and a `HYP_CHARSET` enum (`HYP_CHARSET_ATARI = 2`, `HYP_CHARSET_LATIN1 = 14`,
etc.). Nothing in the prose spec (`hypfmt.ui`, which only documents ids 0–11)
says what shape id 30's payload takes, and the enum invites reading it as a
one-byte numeric id.

**Resolution:** the payload is a NUL-terminated C-string charset descriptor —
the same string UDO's `@charset` source command takes literally (e.g.
`@charset atarist`) — not a byte from the `HYP_CHARSET` enum. `hyp.h`'s enum
is hypview's internal representation *after* it parses this string; it says
nothing about the file's on-disk encoding.

**Evidence:** `textattr.hyp` and `empty.hyp` both carry an id-30 extended
header with `length = 8` and payload bytes `61 74 61 72 69 73 74 00` —
`"atarist\0"` — matching `@charset atarist` in hypview's own UDO source
(`doc/en/header.ui`, `doc/en/orig/1st_conv.stg`). No corpus file (including
`hcp_orig_en.hyp` / `st-guide_orig_en.hyp`, which have no id-30 header at
all) contains a single numeric byte at this id. Alias spellings for the
other two v1 charsets (Latin-1, UTF-8) aren't evidenced in any vendored
corpus file, so those come from the current UDO manual's charset descriptor
table (`man.udo-open-source.org/en/spec_converting_8bit_characters.htm`) —
public documentation of the compiler that writes this field, independent of
hypview and predating it.

## Node prologue records (a-e) are not emitted in a fixed order

**Gap:** `hypfmt.ui`'s "Format of a text object" section enumerates the
prologue as `a) graphics`, `b) cross-references`, `c) further data blocks`,
`d) window title`, `e) object table`, then `f) text` — reading as a fixed
sequence.

**Resolution:** each record is self-identifying (`ESC` + a type byte in
`0x23, 0x28-0x35`), and a real file interleaves them freely. Parse the
prologue as a loop that dispatches on the type byte and stops at the first
`ESC` whose type falls outside that set (or at a non-`ESC` byte) — that byte
starts the text region. This is safe because the text region's own escapes
(`0x24-0x27` link/alink, `0x64-0xa6` attributes/colour) use a disjoint range
from the prologue's, so there's no ambiguity about where one ends and the
other begins.

**Evidence:** `hcp_orig_en.hyp` node 0 ("Main") emits its window title
(`0x23`) first, then one image (`0x32`) and nine box-drawing lines (`0x33`),
then three cross-references (`0x30`) — title before graphics before
cross-references, none of which matches the prose spec's a-b-c-d-e reading
order.

## Window title has no alignment padding in practice

**Gap:** `hypfmt.ui` says a window title (`0x23`) is "NUL-terminated" plus a
"possible fill-byte so that the text starts at an even address" — worded
conditionally, without saying when the fill byte applies.

**Resolution:** no padding byte is ever present. Read the title as a
NUL-terminated string and continue immediately after the NUL, regardless of
whether that position is odd or even.

**Evidence:** across 29 window titles in `hcp_orig_en.hyp` and
`st-guide_orig_en.hyp`, the byte immediately after title `n`'s NUL is the
next prologue record's `ESC` (`0x1b`) in every case — including titles
whose length makes that position odd — with no extra byte ever inserted.
(A handful of popup nodes have `0x00` immediately after the title, but that
is the node's first text line being empty, not padding: those nodes have no
further prologue records, and the `0x00` is the first byte of item f.)

## Base-255 field encoding, and its byte order

**Gap:** `hypfmt.ui` repeatedly says a 2-byte field is "present to a base of
255 and a value of 1 is added to both bytes" (to avoid NUL bytes in node
data) without spelling out the arithmetic or which byte is the more
significant one.

**Resolution:** for stream bytes `lo, hi` (in that order — the less
significant digit comes first), the decoded value is
`(hi - 1) * 255 + (lo - 1)`. Applies uniformly to every base-255 field this
phase touches: image index, graphic Y-offset, cross-reference target index,
and all four object-table fields.

**Evidence:** `image.hyp`'s single image entry sits at index 1 in the file's
index table; its three placements all decode `imageIndex` bytes `02 01` to
`1` under this formula. `limage2.hyp` has two image entries (indices 1 and
2); its four placements decode to `1, 2, 1, 2` in file order, matching
`select_box.img`, `title.img`, `select_box.img`, `title.img` by name.
Y-offsets across all four graphics fixtures decode to values consistent
with the fixtures' own visual intent (e.g. `limage.hyp`'s three
line-height images land at `y = 1, 2, 3`, one per line).

## Dithermask (`0x2f`) and object table (`0x31`) are unevidenced in the vendored corpus

**Gap:** the prose spec documents both, and `hyp.h` separately calls the
dithermask escape "undocumented". Neither appears anywhere in the four
graphics micro-corpus files or in either full real document
(`hcp_orig_en.hyp`, `st-guide_orig_en.hyp`).

**Resolution:** implemented from the prose spec only — a dithermask
immediately preceding an image escape attaches to that `Graphic.Image`; one
that isn't followed by an image (or is itself superseded by a second
dithermask before an image claims it) surfaces as an ordinary `DataBlock`.
Object-table entries are fixed 10-byte records (no length field, unlike the
cross-reference and generic data-block records) per the prose spec's field
list. Both are covered only by hand-constructed unit tests, not corpus
fixtures — revisit if the phase-11 wild sweep turns up a real example.

## Colour escapes `0xa5`/`0xa6` take one *raw* palette-index byte — so a line split on NUL alone is wrong

**Gap:** neither source describes the on-wire parameter of the foreground /
background colour escapes. `hypfmt.ui`'s item-f enumeration stops at type 164
(`0xa4`) and never mentions colour at all; `hyp.h` stops enumerating escapes at
`HYP_ESC_BG_COLOR 0xa6` with no parameter description. So the parameter's width
(one byte? two?) and encoding (raw 0-15 index, or base-255-biased like every
other multi-byte inline value) were both unstated. The question matters because
`HYP_COLOR_WHITE` is 0: a raw index 0 puts a literal NUL byte inside a line,
which the format elsewhere goes out of its way to avoid.

**Resolution:** exactly **one byte**, holding the **raw** palette index 0-15 —
*not* base-255 biased, and *not* NUL-avoiding. Colour index 0 (white) therefore
does appear as a `0x00` byte in the middle of a line. The consequence for
parsing: **line splitting must be escape-aware.** A parser that splits the text
region on NUL first and interprets escapes second breaks every line that selects
white, and every escape parameter that follows. Consume the escape and its
parameter, *then* test the next byte for the line terminator.

**Evidence:** `colors.hyp` exists to exercise exactly this. Splitting its 422
decompressed text bytes on NUL yields 19 "lines", of which the first three are
`hello ␛\xa5`, `␛\xa6\x01white world␛\xa6` and `␛\xa5\x01` — three fragments
whose escapes are each cut off mid-parameter. Reading each `0xa5`/`0xa6` as
"escape + one raw index byte" splices them into one coherent line,
`hello ` + fg=white(0) bg=black(1) + `white world` + bg=white(0) fg=black(1),
and turns the whole node into 17 lines whose text is `hello <name> world` for
each of the 16 palette entries in index order (`white, black, red, green, blue,
cyan, yellow, magenta, light gray, dark gray, dark red, dark green, dark blue,
dark cyan, dark yellow, dark magenta`) plus a trailing empty line — the fixture's
own words naming the colour its escape selects, entry for entry, with
`hyp.h`'s `HYP_COLOR_*` numbering. Under any other reading (two-byte parameter,
or `(hi-1)*255 + (lo-1)`) the names and the indices stop lining up at the first
line. The white-on-black first line and the `hello black world` second line also
confirm `HYP_DEFAULT_FG = BLACK` / `HYP_DEFAULT_BG = WHITE`: the fixture writes
no escape at all for black-on-white.

**Not a resolution of the `0xa4` typewriter overlap.** `hyp.h` comments that
typewriter (`0xa4`) "actually uses range 0xa4-0xe3", which would swallow
`0xa5`/`0xa6`. This phase implements `0xa4` as the prose spec's documented
one-byte no-visual-effect escape and `0xa5`/`0xa6` as fg/bg colour per `hyp.h`,
which is consistent with every vendored fixture (`colors.hyp` uses `0xa5`/`0xa6`
as colour; no vendored file uses `0xa4` at all). The full overlap stays deferred
to the phase-11 wild sweep, as planned.

## An object whose `compDiff` is 0 is stored uncompressed, not lh5-compressed

**Gap:** the prose spec describes the data region as "lh5-compressed objects at
each entry's seek offset" with no mention of a stored/uncompressed alternative,
and there is no per-object header to signal one (see the entry above).

**Resolution:** when an entry's derived uncompressed length equals its
compressed length — equivalently, when `compDiff == 0` — the bytes at `seek`
are the object's contents verbatim. The compiler skips lh5 when compressing
would not pay for itself. Feed them straight to the node parser; running the
lh5 decoder over them fails.

**Evidence:** `linkattr.hyp`'s entries 1, 2 and 3 ("Page 1", "Page 2", "popup")
each have `compDiff = 0` and a derived `compressedLength` of 17, and the 17
bytes at each `seek` read literally as `Dies ist Seite 1\0`,
`Dies ist Seite 2\0` and `This is a popup.\0` — the nodes' own plain text, with
no bit-stream framing of any kind. Before this rule, all three produced
`DecompressionFailed` diagnostics and no `Node`; with it, `linkattr.hyp` opens
with zero diagnostics. No entry in `hcp_orig_en.hyp` or `st-guide_orig_en.hyp`
has `compDiff == 0`, which is why phase 3's sweep over those two documents never
surfaced the case.

## A line's width byte is a *signed* x-length stored excess-128; a box's is plain unsigned

**Gap:** `hypfmt.ui`'s graphic-object layout gives one shared field list for
image/line/box/rbox — "1 byte width of the object in characters" — with no
mention of signedness or of a bias, and it names the base-255 encoding only
for the index and Y-offset fields. But the HCP `@line` command's own
documentation (`hcpcmds.ui`, "Command @line") states the parameter ranges as
`X-offset: 1..255`, `X-length: -127..126`, `Y-length: 0..254`, with a negative
x-length meaning "a line from upper right to lower left". Nothing in either
document says how a signed −127..126 fits into that one unsigned byte, and
neither is the width listed as base-255. Reading it as a plain unsigned byte
produced nonsense: every line in `st-guide_orig_en.hyp` came out 112..199
columns wide on pages no wider than ~72 columns.

**Resolution:** for `ESC 0x33` (line) only, the width byte is the x-length
stored **excess-128**: `xLength = byte - 128`. That maps the documented
−127..126 onto bytes 1..254, keeping NUL out of node data — the same
NUL-avoiding motive as the base-255 fields, applied to a signed one-byte
field. `ESC 0x34`/`ESC 0x35` (box/rbox) widths at the *same* body offset are
plain unsigned counts and must not be biased. Height is untouched in both
cases.

**Evidence:** a report-only raw-byte scan of every graphic record in
`st-guide_orig_en.hyp` (`./gradlew lineGraphicScan`,
`src/jvmTest/kotlin/de/rholambdapi/hypp/LineGraphicScan.kt`):

- Node "Lines, arrows and boxes" contains a fan of ten arrows sharing an
  origin, all `x=17, y=10`, with width bytes
  `112 113 115 119 124 132 137 141 143 144` → x-lengths
  `-16 -15 -13 -9 -4 +4 +9 +13 +15 +16`: exactly symmetric about zero, which
  no unsigned or two's-complement reading produces (two's complement gives
  `112 113 115 119 124 −124 −119 −115 −113 −112`).
- Node "Symbol bar"'s 14 short connectors between the icon row and its
  captions all carry width byte `128` → x-length `0`, i.e. purely vertical —
  their differing lengths live in `height` (3 or 5), so the uniformity was
  real data, not a misread offset. Their raw bodies, e.g.
  `[08 10 01 80 03 03]` and `[12 10 01 80 05 03]`.
- The standard page rule at the top of 51 nodes is `[01 02 01 c7 01 31]` →
  `x=1, x-length=71, height=1`: one row spanning columns 1..72, matching the
  fixture's page width. Read unsigned it was a 199-column rule.
- Box/rbox records interleaved in those same nodes at the same body offset
  carry small plain values — e.g. "Lines, arrows and boxes" has
  `box x=1 y=2 w=32 h=16`, `box w=16 h=8`, `w=8 h=4`, `w=4 h=2`, `w=2 h=1`,
  and the line drawn across the largest of them is `x=1 y=2` with width byte
  `160` → x-length `32`, exactly that box's width. Biasing box widths too
  would turn them all negative.
- Across the whole fixture no line width byte falls outside 112..199, i.e.
  x-lengths −16..+71 — a tight cluster around 128 that only the excess-128
  reading explains.

## Line/box/rbox `Data` byte: bit0/bit1/rest decomposition doesn't match `lines.hyp`'s filename labels

**Gap:** the prose spec says a line's data byte is bit0 = arrow at start,
bit1 = arrow at end, remaining bits = line style. `lines.hyp`'s ten line
placements are named for what they're meant to show (`"...arrow end"`,
`"...arrow start"`, `"...both arrows"`, plain, diagonal), which looked like
a chance to cross-check the bit assignment against real data.

**Resolution:** decomposed the data byte exactly as the prose spec states,
without trying to force the result to match the filename labels — the two
don't line up cleanly (e.g. both "arrow end" placements and both diagonal
placements decode to the same flags, `arrowAtStart = arrowAtEnd = true`,
while "both arrows" decodes to neither flag set). Rather than guess at a
reinterpretation with no oracle to check it against, `NodeTest` asserts the
literal decoded values only, with a comment noting the label mismatch is
unresolved. No visual rendering oracle exists to settle this; revisit if
the phase-11 wild sweep or a rendering cross-check ever provides one.

## Image object header: `width`/`height` are annotated "(will be ignored)" but are exactly right

**Gap:** `hypfmt.ui`'s "Format of an image object" section gives the header as
`width:u16, height:u16, planeCount:u8, planePresent:u8, planeOnOff:u8,
filler:u8`, but annotates *both* `width` and `height` "(will be ignored)" —
read literally, that says not to trust them for decoding.

**Resolution:** they are exactly right. `rowBytes = ceil(width/16)*2` times
`height` equals the decompressed plane-data length to the byte in all four
vendored images (`image.hyp`/`limage.hyp`: 216×177, 1 plane, 4964 − 8 = 4956 =
28×177; `limage2.hyp`'s two images: 66×32 → 320 = 320, 82×18 → 216 = 216).
Decoding a full image (`image.hyp`'s `rtr_logo.img`) through this header and a
hand-rolled BMP encoder into `hyp2html`'s embedded `data:` URI produced a
legible "Ardi Soft" logo — the compiler's own name — which is
strong independent confirmation the header, the row-byte formula and the
pixel bit order (MSB-first per byte, matching the format's big-endian
convention elsewhere) are all correct. The "(will be ignored)" note most
likely means "ignored/regenerated by the compiler on write", not "unreliable
to trust on read" — ambiguous prose, resolved empirically same as several
earlier phases.

**Multi-plane concatenation order: now corpus-confirmed.** The vendored
`st-guide_orig_en.hyp` has one multi-plane image — index 77, 528×153,
`planeCount = 4`, `planePresent = 255`, `planeFilled = 0` — and decoding it
with planes stored whole and sequentially (the prose's "1st plane / optional
2nd plane / ..." enumeration, plane 0 contributing the pixel value's low bit)
yields exactly four distinct values (0, 4, 10, 15) landing on four coherent
regions of a legible banner: a white background, a blue "ST-Guide" wordmark, a
round stamp on the right, and a subtitle band. Any other bit order (plane 0 as
the high bit, per-row or per-word interleaving) turns the same bytes into
noise. `./gradlew imagePlaneScan` reprints the histogram, a downsampled
by-value art dump and a rendered PNG as ongoing evidence.

`planeFilled` is still zero in every vendored image, so the "present vs.
filled vs. neither" combination remains prose-only, exercised by synthetic
tests alone.

## `x == 0` is the only centring signal, and it is an *image-only* signal; the graphic `width` byte separates `@image` from `@limage`

**Gap:** `Graphic.centered` was implemented as `x == 0` on the whole `Graphic`
hierarchy, with a doc comment claiming that reading was "confirmed empirically"
against `image.hyp`/`limage.hyp`/`limage2.hyp`/`lines.hyp`. Those four fixtures
happen to contain no case that contradicts it, so the claim was really "not yet
falsified", not "confirmed" — and applying it to `@line`/`@box`/`@rbox` was
never checked against the spec at all.

**Resolution, part 1 — no better signal exists, and it is images-only.** Both
sources state `x == 0` and neither offers a second, more explicit flag:
`hypfmt.ui`'s graphic-object layout says "1 byte X-offset in characters (X == 0
for centered images)", and `hyp.h`'s `x_offset` comment gives the valid ranges
per command — `@line`/`@box`/`@rbox` **1-255**, `@image`/`@limage` **0-255**
with "(0 == centered)". So zero is not a placement mode a vector graphic can
even carry; for those it is a column, or malformed data. `centered` therefore
lives on `Graphic.Image` only, not on the `Graphic` interface.

**Resolution, part 2 — `width == 1` marks `@limage`.** `hypfmt.ui` annotates
the graphic `width` byte "(width == 1 for @limage)" and `hyp.h` spells the same
out ("value used internally: 0, or 1 for limage"; `height` "value written to
file: 0"). So for an image the `width` byte is not a width at all — it is the
flag that separates the format's two image commands, and the earlier claim that
`width`/`height` are "present on the wire but ignored by the format for images
(real files carry 0 for both)" was wrong.

The distinction is load-bearing for any renderer, because the two commands lay
out differently. Per the HCP command reference for `@limage`: "images
incorporated in this way will be treated by ST-Guide as lines (limage == line
image), meaning that text cannot be placed to either the left or the right of
them and it isn't necessary to insert blank lines below the image, as ST-Guide
will automatically move the following text down by a distance depending on the
height of the image and the current font." A plain `@image` is the opposite —
an overlay drawn on top of the character grid, on rows the author left blank
for it.

**Evidence:** `st-guide_orig_en.hyp` uses both, and only the two together
explain what its pages look like. Its 29 image placements are 1 with `width ==
1` (the 528×153 "ST-Guide" banner on "Main", at `x = 0`, i.e. centred) and 28
with `width == 0` (the 32×24 toolbar icons on "Symbol bar" and the icon pages,
at `x = 3, 13, 23, …`). "Symbol bar" is the control case for overlays: its
icons sit at rows 11/13/15 and the node's own text has blank lines at exactly
those rows. "Main" is the control case for line images: its 14 text lines are a
dense two-column table of contents with *no* blank rows, so an overlay reading
of its banner puts a 66-cell-wide, ~9-row-tall image straight on top of the
whole table of contents — which is exactly the bug this note came out of.
`limage.hyp`/`limage2.hyp` (all placements `width == 1`) and `image.hyp` (all
`width == 0`) agree, and the previously-passing test
`NodeTest.limageHypPlacesLineHeightImages()` had already recorded the `width ==
1` observation without drawing the conclusion from it.

## Image pixel values are Atari ST hardware pens, not VDI colour indices

**Gap:** `hypfmt.ui` describes an image's planes but never says what a decoded
pixel value *means*, and the format's own `0xa5`/`0xa6` text-colour escapes
carry a different kind of number — a GEM VDI colour index, which is exactly
`HypColor`'s ordinal (phase 6, via `colors.hyp`). Treating a bitplane pixel
value as a `HypColor` ordinal is the obvious reading and is what hypp did.

**Resolution:** it is wrong for 16-colour images. A bitplane pixel value is an
Atari ST *hardware palette register* ("pen"); GEM permutes pens and VDI
indices, differently per plane count. `Palette.forPlaneCount` applies the
standard GEM pen→VDI table (identity at 1 plane, which is why every
single-plane fixture always looked right).

**Evidence:** image 77's four pens, read as VDI indices, render the round
stamp `DARK_RED` and the subtitle `DARK_MAGENTA` — the "badge is red, should
be green" and "subtitle is lilac, should be black" symptoms reported against
`hypview`'s rendering of the same file. Mapped pen→VDI, the same four values
become `WHITE` (69.55%, background), `BLUE` (20.56%), `DARK_GREEN` (7.65%,
`x = 364..510` — the right-hand stamp) and `BLACK` (2.24%, `y = 115..137` — a
thin subtitle band), and the rendered PNG reads "ST-Guide" in blue over
"fairware from holger weets" in black beside a green "ST-Guide documentation"
stamp. The table has no free parameters and fixes both reported colours while
leaving the two already-correct ones (`WHITE`, `BLUE` — both pen→VDI fixed
points) untouched, which is what rules out the reordering hypotheses: any
bit-order change that produced green and black also moved the wordmark off
blue. Locked in by `ImageNodeTest.fourPlanePensAreBlockConcatenated...` and
`StGuideBannerColorTest`, which asserts the decoded-PNG palette through
`ImageIO`.

The 2-plane row of the table is the same documented GEM convention but has no
corpus case; 3 and 5..8 planes fall back to the identity, unconfirmed.

## ST palette RGB values are a documented convention, not corpus- or spec-derived

**Gap:** neither `hypfmt.ui` nor `hyp.h` gives RGB values for the 16-entry
palette `HypColor` names (phase 6 confirmed the index-to-name mapping via
`colors.hyp`'s self-describing text, but that fixture has no rendering oracle
for the actual colours).

**Resolution:** `HypColor.red/green/blue` use the standard Atari
ST/GEM default 16-colour palette convention — full-intensity primaries
(255) for indices 2–7, half-intensity (128) for the "dark" variants at
indices 10–15, which matches those entries' own corpus-confirmed names.
This is a public, independent, standard-palette convention (same category as
phase 4's UDO charset table), not verified against any `.hyp` file's actual
rendering.

## Index-entry `toc` is `@toc`'s "Contents" jump target, not a raw tree-parent field

**Gap:** the index-table field's prose gloss is just "Index of the table of
contents for this object" — read in isolation, ambiguous between "the parent
in a navigation tree" and something else entirely.

**Resolution:** `hcpcmds.ui`'s `@toc <name>` documentation is unambiguous:
it sets what the ST-Guide window's "Contents" button jumps to *from this
page*, defaulting (when unset) to "the physically first page of the text"
(index 0). Placed before a `@node`, it applies to all following nodes until
overridden — "an easy way to create a group of nodes with a single entry in
the Contents popup". Read that way, grouping `entries` by this field
(children of `k` = every entry whose own `toc` equals `k`) still produces
exactly the nesting a table-of-contents needs, because a group's "Contents"
target *is* its structural parent — confirmed against `st-guide_orig_en.hyp`'s
real, multi-level `@toc` groups (`doc/progress/phase-08-document-api.md`).
`next`/`prev` are a separate mechanism entirely — `hcpcmds.ui`'s `@next`/
`@prev` describe them as the "Page >"/"Page <" buttons' reading-order chain,
unrelated to tree nesting; a self-referencing `next`/`prev` value means that
button is greyed out, not "no data".

## `0xa4` "typewriter" vs. `0xa5`/`0xa6` colour overlap — resolved: `0xa4` is a genuine zero-parameter marker

**Gap:** carried over from phase 6 (see above). `hyp.h` comments that typewriter
(`0xa4`) "actually uses range `0xa4`-`0xe3`", which would make it a
multi-value, parameterised escape overlapping the documented one-byte
`0xa4` no-effect code and the one-parameter-byte `0xa5`/`0xa6` colour
escapes. Deferred to the phase-11 wild sweep for lack of any occurrence in
the vendored micro-corpus.

**Resolution:** `0xa4` takes **zero** parameter bytes, exactly as
implemented since phase 6 — `hyp.h`'s broader range claim does not manifest
in real files. Unchanged: no code or model change from phase 6's
implementation.

**Evidence:** the phase-11 wild sweep (`./gradlew corpusSweep`) downloaded
and opened all 702 files from `tho-otto.m68k.eu/hypview/`'s public corpus
listing (`doc/progress/phase-11-wild-sweep.md` has the full report). `ESC
0xa4` occurs only **45 times total**, confined to a single file
(`hyp2gdos.hyp`, across its "Bedienung", "Optionen" and "Beispiel einer
Konfigurationsdatei" nodes; the last is a monospaced sample config-file
listing, `H2G_DEVICE=...`, `H2G_BORDER_LEFT=25`, etc.) — restricted to
text/popup entries only, since a first, unrestricted pass also matched
`0x1b 0xa4` inside raw image bitplane data 122 times, which is coincidental
binary noise, not an escape occurrence.

The decisive test: if `0xa4` consumed a parameter byte, the byte immediately
after it (what the current zero-parameter implementation treats as the next
real content byte) would instead be *inside* that parameter — and would only
coincidentally look like sensible content. Tallied across all 45
occurrences, that byte is one of exactly four values: `ESC` (`0x1b`, 32
times), `'#'` (`0x23`, 5 times, always the start of a config-file comment
line), `' '` (`0x20`, 4 times) or `NUL` (`0x00`, 4 times, the line
terminator) — never a small integer in a broad spread the way a genuine
parameter byte would read (contrast the `0xa5`/`0xa6` colour parameter's
full 0-15 spread in `colors.hyp`, phase 6). Every occurrence sits at a
natural structural boundary: end-of-line, immediately before a link escape,
or immediately before a comment marker — and the ±20-byte decoded context
around each one reads as clean, unbroken German prose and config-file text
with no stray characters, exactly what zero-parameter decoding predicts.
Also notable: `0x64`-`0xa3` (the documented absolute-attribute bit vector)
is exactly 64 codes — `2^6`, matching its own 6 documented style bits
exhaustively — so `0xa4` is structurally "the code right after a full
bit-vector range", consistent with being a distinct one-off marker rather
than a continuation of it.

**Not fully explained:** *why* the marker exists (its "no visual effect" is
taken from the prose spec, not derived here) or what `hyp.h`'s range comment
was describing — possibly an implementation-detail range check in the
reference decoder rather than a claim about the wire format. Immaterial to
correctness: parsing 0xa4 as a bare zero-parameter code is confirmed against
every real occurrence found.

## Phase 12: `.REF` binary format findings

### `.REF` entry type and field layout confirmed (2026-08-18)

**Scope:** The `.REF` container format (magic "HREF", modules, entry sequences) was parsed successfully into a working model (`RefFile.kt`, committed `ba35526`); the property-based round-trip test confirms encode/decode symmetry across 200 random seeds.

**Layout confirmed:** per `doc/PLAN-12-19.md` § ".REF binary format":
- **Module-header:** 4-byte big-endian length (module data only, not header), 4-byte big-endian entry count.
- **Entry** (one per-entry sequence):
  - 1 byte: entry id (0=File, 1=Node, 2=Alias, 3=Label, 4=Database).
  - 1 byte: length of string field (NUL-terminated, not including the NUL).
  - N bytes: NUL-terminated string.
  - **3 only (Label):** 2 additional bytes (big-endian line number) after the NUL — the "EXTRA" bytes flagged by the spec's capitalization.
- **Terminator:** 8 zero bytes in place of a module-header marks end of file. An "empty" (zero-entry, zero-length) module is byte-identical to the terminator and ends the file.

**Evidence:** The exact byte-encoding pattern (via `RefFileTest.kt`'s `entry()`/`module()`/`refBytes()` helpers, reused for the property generator in `RefFilePropertyTest.kt`) encodes and decodes symmetrically for random entry sequences. No special cases found; the spec text reads literally.

### `TYPE_EXTERNAL_REF.name` format and real anomalies — count correction (2026-08-18)

**Original estimate:** `doc/PLAN-12-19.md` § "Resolved: what TYPE_EXTERNAL_REF.name actually contains" listed 18 type-2 entries in `hcp_orig_en.hyp`.

**Actual verified count:** 16 entries — 14 with a `/`-split fileName plus 2 anomalies with no `/`. Source: `src/commonTest/kotlin/de/rholambdapi/hypp/IndexEntryTest.kt`, lines 44–52, verified against the vendored fixture. The plan's estimate was off by 2.

**Anomalies:** Two entries with no `/` (whole string = node name, `fileName = null`):
- `"Options"`
- `"command extern"`

**Treatment:** `IndexEntry.externalRef()` splits on the first `/` if present, else yields `ExternalRef(null, name)`. Matches the empirical finding.

### Parser judgment calls (design decisions during implementation)

Three deliberate reading choices made during parser implementation, recorded here as they are not directly contradicted by corpus evidence but rest on interpretation of spec text:

**(a) Empty modules are indistinguishable from terminator.** A module with length=0 and count=0 is byte-identical to the 8-zero-byte terminator. Parser treats such a module as the terminator and ends the file, rather than yielding an empty module followed by more content. Test: `RefFileTest.kt` § `zeroEntryModuleIsIndistinguishableFromTheTerminator()` — confirms this behaviour preserves the spec's terminator semantics. No real `.REF` files exist to confirm this against; synthetic test pins the choice.

**(b) File ending without an explicit terminator is accepted.** The container may end at a module boundary (after the last module's data) without an 8-zero-byte terminator. This mirrors the `.HYP` container's own optional EOF sentinel convention (`doc/LEARNINGS.md` § "EOF sentinel is optional"). Parser stops at `bytes.size` if the terminator is never reached, rather than treating it as truncated. Test: `RefFileTest.kt` § `fileEndingWithoutATerminatorStillParses()` confirms this accepts valid-but-unterminated input.

**(c) Label entries' line-number bytes are read as per spec text, literally.** The spec marks them "EXTRA" bytes following the NUL, and the parser reads exactly 2 bytes as big-endian line number. No corpus files exist to confirm this against (no real `.REF` samples in the public corpus — see `doc/PLAN-12-19.md` § Semantically). The reading is a bytes-follow-the-spec-text-literally interpretation. Test: `RefFileTest.kt` § `labelEntryCarriesItsLineNumber()` covers this.

**Evidence:** All three choices are consistent with the parser's successful round-trip test across 200 random seeds, and with the hand-constructed test cases in `RefFileTest.kt`. No contradictions found. Behavior is deferred-decision-safe: a future real `.REF` file would immediately surface any misreading.
