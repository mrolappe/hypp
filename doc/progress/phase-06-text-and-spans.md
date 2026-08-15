# Phase 6 — Text and spans

**State: green.**

## Completed

- `HypColor` — the format's fixed 16-entry palette as an enum whose ordinal
  *is* the wire palette index (`WHITE, BLACK, RED, GREEN, BLUE, CYAN, YELLOW,
  MAGENTA, LIGHT_GRAY, DARK_GRAY, DARK_RED, DARK_GREEN, DARK_BLUE, DARK_CYAN,
  DARK_YELLOW, DARK_MAGENTA`), plus `byIndex`. Colour *identity* only — RGB
  values belong to phase 7's `Palette`.
- `TextStyle` — a `@JvmInline value class` over an `Int`: bits 0-5 the format's
  own absolute attribute bit-vector, bits 8-11 foreground, bits 12-15
  background. `isBold`/`isLight`/`isItalic`/`isUnderlined`/`isOutlined`/
  `isShadowed`/`foreground`/`background`, and `TextStyle.Normal` (no
  attributes, black on white). `withAttributes`/`withForeground`/
  `withBackground` are `internal` — nothing outside the parser builds a style.
- `Link` + `LinkKind` (`LINK`/`ALINK`) — `target: NodeIndex`, optional
  `lineNumber`, and `label`.
- `Line(spans)` + `Span(text, style, link?)`, with `Line.text` for consumers
  that only want the plain text.
- `parseLines` (private, `Node.kt`) — node data item f. Escape-aware line
  splitting, `ESC ESC` as a literal ESC mid-run, the absolute attribute vector
  (`0x64`-`0xa3`), `0xa4` as a documented no-op, fg/bg colour (`0xa5`/`0xa6`),
  and link/alink with and without line numbers (`0x24`-`0x27`) including the
  `32 + n` label rule and its "exactly 32 → use the target's own name" case.
- `Node.lines: List<Line>` replaces phase 5's raw `Node.textBytes`, which is
  gone. `parseNode` gained two trailing parameters with defaults —
  `charset: HypCharset` and `entryNames: List<String>` — so all text (and any
  name-resolved link label) is decoded through the document's charset, per the
  plan's "text decoded to String at parse time" locked decision.
- Three new `Diagnostic` variants: `UnknownEscape(index, code)`,
  `UnterminatedLine(index)`, `DanglingNodeReference(index, target)`.
- **Uncompressed objects.** `HypDocument.open` now feeds an entry's bytes
  straight to `parseNode` when `uncompressedLength == compressedLength`
  (`compDiff == 0`), instead of through `Lh5.decompress`. See
  `doc/format-notes.md`.
- `hyp2text` (`commonTest`) — the phase's required integration consumer, ~55
  lines, reaching the document only through the public API.

## Decisions

- **Line splitting is escape-aware, not a split on NUL.** A colour escape's
  parameter is a raw palette index and index 0 (white) is a literal `0x00`
  byte, so the terminator test has to come *after* each escape and its
  parameter are consumed. This is the phase's headline format resolution;
  `colors.hyp` is the evidence. See `doc/format-notes.md`.
- **`0xa5`/`0xa6` carry one raw byte, not a base-255 pair** — settled
  empirically against `colors.hyp`, where the fixture's own words name the
  colour each escape selects. The `0xa4` typewriter-range overlap is *not*
  resolved here; it stays deferred to phase 11 as planned, and this phase's
  reading is recorded as consistent with the fixtures rather than as the
  answer.
- **A link resolves its label against the index table, so `parseNode` needs
  the entry names.** Passed as a `List<String>` rather than a resolver
  function or the `IndexEntry` list — the label rule is the only thing the
  text parser needs from the container.
- **A link whose target isn't in the index table is dropped, not fabricated.**
  The span keeps whatever label text was on the wire, `link` is null, and a
  `DanglingNodeReference` diagnostic is recorded. A negative base-255 decode
  (only reachable on malformed data) is handled the same way, which also
  removes a latent `NodeIndex` `require` throw from phase 5's cross-reference
  and image paths — a hostile file can no longer crash the parser there.
- **A truncated prologue record now stops the whole node, not just the
  prologue.** Before this phase the parser diagnosed `NodeDataOverrun` and
  handed the remaining bytes to the text region; since the record's length is
  the thing that's broken, the text region's start is unknowable, so the
  parser stops. Otherwise one malformed prologue byte produces a cascade of
  bogus `UnknownEscape`s.
- **A label-length byte below 32 is read as "use the target's name"**, the
  same as exactly 32. The spec only defines 32; anything smaller would mean a
  negative label length, and this is the lenient reading that can't overrun.
- **Window title and cross-reference popup text still use `decodeName()`**,
  not the document charset — unchanged from phase 5, still worth revisiting if
  a non-ASCII window title ever turns up.

## Tests added

All green on `jvm`/`wasmJs`/`wasmWasi` — 51 tests in the suite now (was 33).

`TextTest.kt` — span-by-span, expected values hex-derived from the
decompressed node bytes:

- `textattr.hyp`: all 9 lines, all 27 spans, exact text and exact
  `TextStyle` for each of bold / light / italic / underlined / outlined /
  shadowed, including an attribute run at the start of a line and one whose
  surrounding whitespace has to survive intact.
- `colors.hyp`: all 17 lines — the escape-aware count, not the 19 a NUL-only
  split gives — with each of the 16 palette entries asserted by name against
  the fixture's own wording, plus the white-on-black first line and the
  trailing empty line.
- `linkattr.hyp`: all 8 links, exact target index and label, covering link
  targets of every entry type the format has (internal, popup, external
  reference, system, REXX command/script, close, quit); a link at the start of
  a line with text after it; and the three uncompressed nodes' text.
- `hcp_orig_en.hyp`: the 9 links of node "Main", four of which use the
  "length byte == 32 → use the target's own name" rule, asserted against the
  index table's actual names.
- `hcp_orig_en.hyp` + `st-guide_orig_en.hyp`: no `UnknownEscape`, no
  `UnterminatedLine`, no `DanglingNodeReference` across either whole document.
- Hand-constructed bytes for what no fixture exercises: `ESC ESC`, escape
  `0xa4`, alink-with-line-number (`0x27`, base-255 line number 258), a link
  to an index outside the table, an unknown escape, a final line with no NUL,
  and the same bytes decoded through two different charsets.

`Hyp2TextTest.kt` — the integration suite:

- The whole "Main" node of `hcp_orig_en.hyp` rendered and asserted line by
  line, including its window title, its 10 graphic placements and its 3
  cross-references.
- Both real documents rendered end to end: exact node counts (104 and 63) and
  an assertion that every node's every line, graphic and cross-reference
  reaches the output.
- Styled and coloured runs carry their markers.

## Remaining

- `0xa4` typewriter vs `0xa5`/`0xa6` colour overlap — still deferred to the
  phase-11 wild sweep.
- No vendored fixture uses `alink` (`0x26`/`0x27`) or a link line number, so
  those paths are covered only by hand-constructed bytes.
- `Diagnostic` still has no `Location` type; the new variants carry a
  `NodeIndex` and no byte offset, same as phase 5's.
