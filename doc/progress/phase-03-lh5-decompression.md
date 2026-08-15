# Phase 3 — lh5 decompression

**State: green.**

## Completed

- `internal/BitReader.kt` — MSB-first bit reader over `bytes[fromIndex until
  toIndex]`. Reads past the region yield zero bits (the encoder pads the last
  byte, and the decoder legitimately peeks past the final symbol) but set an
  `overrun` flag, so a padded tail is distinguishable from a stream being
  decoded wrongly. Specified and tested on its own before anything used it.
- `internal/Lh5.kt` — the `-lh5-` decoder, written clean-room from public
  descriptions of the LHA method (no hypview `.c`, no `~/studio` code, no
  third-party LZH implementation was read). LZSS over an 8 KiB / 13-bit window,
  matches 3..256 bytes, two Huffman trees re-transmitted per block:

  ```
  block := blockSize:u16
           codeLengthTree     19 symbols, 5-bit count, 3-bit lengths (escape 7 + unary),
                              2-bit skip count right after index 3
           literalLengthTree  510 symbols, 9-bit count, lengths coded with the tree above;
                              symbols 0/1/2 are run-length escapes for unused entries
                              (1, 4-bit+3, 9-bit+20)
           offsetTree         14 symbols, 4-bit count, same 3-bit length coding, no skip
           blockSize × symbol
  ```

  Literal/length symbol `< 256` is a literal; otherwise match length is
  `symbol − 256 + 3` and an offset symbol `k` follows, meaning distance
  `k == 0 ? 0 : (1 shl (k−1)) + <k−1 more bits>`, counted back from the byte
  about to be written. The window is *not* reset between blocks; only the trees
  are.
- `Huffman` (same file) — canonical Huffman decoder built from a code-length
  list (shortest codes first, ties by symbol index — the DEFLATE convention,
  which is also what LHA's table builder produces), plus the format's
  "count == 0 ⇒ the tree is one constant symbol, consuming no bits" case.
- `IndexEntry.hasData` — true for internal/popup/image entries only. Types 2 and
  4–8 have no object in the data region, so their derived `compressedLength` is
  meaningless and they must not be fed to the decompressor.
- `st-guide_orig_en.hyp` (51349 B) vendored under
  `src/commonTest/resources/corpus/` and embedded in `TestCorpus.kt` as four
  chunked base64 literals, exactly as phase 2 did for `hcp_orig_en.hyp`.

## Tests added

All green on `jvm`, `wasmJs`, `wasmWasi` (14 tests total in the suite now):

- `BitReaderTest` (6) — MSB-first ordering, reads spanning a byte boundary,
  `peek` not advancing, zero-fill + `overrun` past the end, sub-range honouring,
  zero-width read. Expected values hand-derived from the literal bit patterns
  `0xB4 = 1011 0100`, `0x2D = 0010 1101`.
- `Lh5Test.textattrMainNodeDecompressesToItsDerivedLength` — the plan's red
  checkpoint: the "Main" node of `textattr.hyp` (`seek=110`,
  `compressedLength=119`, `compDiff=174`) decodes to exactly 293 bytes, ending
  in the NUL that terminates the last text line.
- `Lh5Test.truncatedStreamIsRejectedRatherThanReturningPartialData` — feeding
  half the compressed bytes returns `null`, not a half-filled buffer. This is
  what keeps the length assertions from being tautologies (see Decisions).
- `Lh5Test.everyDataBearingNodeInHcpOrigEnDecompresses` — the plan's phase-3
  integration row: all 106 data-bearing entries of `hcp_orig_en.hyp` (96
  internal + 8 popup + 2 image, out of 124) decode to their exact derived
  uncompressed length.
- `Lh5Test.everyDataBearingNodeInStGuideOrigEnDecompresses` — same for all 78
  data-bearing entries of `st-guide_orig_en.hyp` (59 internal + 4 popup + 15
  image, out of 101).

Both integration tests also assert the expected image-entry count, because
image entries are the only ones whose uncompressed length comes through the
`next` overload — so this is the first end-to-end proof of that phase-2 rule.

## Decisions

- **`decompress` returns `ByteArray?`, and returns null unless it produced
  exactly the requested size without overrunning.** The uncompressed size is an
  *input* (the format has no end-of-stream marker; the decoder stops when it has
  produced `compressedLength + compDiff` bytes), so an implementation that
  pre-sizes its output array would satisfy "decompresses to exactly N bytes"
  trivially. Making failure representable is what gives the plan's required
  assertion any content. Failure cases: a code not in the tree, a match reaching
  back before the start of the output, a zero block size, a read past the end of
  the compressed region, or an out-of-bounds source range.
- **No `Diagnostic` yet.** `DecompressionFailed` is in the plan's sealed
  hierarchy, but that hierarchy still does not exist (phase 2 deferred it) and
  nothing in `HypDocument` calls the decompressor yet — node objects get read in
  phase 5/6. `null` is the right shape until there is a collector to report to.
- **Canonical Huffman decoded bit-by-bit**, not via the 8/12-bit lookup tables
  the classic implementations use. The corpus is tens of KB per document and
  decoding is not on any hot path yet; the bit-by-bit form is short and
  obviously correct. Revisit only if the phase-11 wild sweep over 704 files is
  measurably slow.
- **Window size 8 KiB, therefore 14 offset codes with a 4-bit count.** Public
  secondary sources disagree on this — see `doc/format-notes.md`.
- **A `blockSize` of 0 is treated as a malformed stream.** It is ambiguous in
  the wild (implementations variously read it as "no symbols", "65536 symbols",
  or end-of-data); honouring it literally would spin forever. No corpus file
  contains one.
- The decompressed bytes were cross-checked by eye once, outside the test suite:
  `textattr.hyp`'s "Main" node reads as German prose with `ESC 0x64+bits`
  absolute attribute vectors exactly where the spec says they belong
  (`ESC 'f'` = light, `ESC 'e'` = bold, `ESC 'l'` = underlined, `ESC 0x84` =
  shadowed, `ESC 'd'` = back to normal). Not asserted — that is phase 6's job —
  but it confirms the output is real node data and not merely the right length.

## Remaining

- Nothing in `HypDocument` reads node objects yet; wiring the decompressor into
  the document (and turning failures into a `DecompressionFailed` diagnostic)
  belongs to phases 5–6.
- No lh5 *compressor*. Out of scope (read-only v1), and the plan's write/
  round-trip note already accounts for it.
- The decoder rejects rather than repairs damaged streams; if the wild sweep
  (phase 11) finds files that partially decode, revisit whether a partial result
  plus a diagnostic is more useful than `null`.
