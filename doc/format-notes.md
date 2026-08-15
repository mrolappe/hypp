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
