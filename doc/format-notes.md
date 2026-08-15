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

**Evidence:** `hcp_orig_en.hyp`'s last two index entries (types 2, external
reference) both have `seek == 57785` (the exact file size, `next`/`prev`/
`toc` all zero) with no trailing type-255 entry after them; `itableCount`
(124) already accounts for all of them, no more, no less.
