# hypp — progress index

See `doc/PLAN.md` for the full plan. One entry per phase, updated at the end
of every round.

| Phase | Name | State |
|---|---|---|
| 1 | Skeleton | green |
| 2 | Container | green |
| 3 | lh5 decompression | green |
| 4 | Charsets | green |
| 5 | Node prologue | green |
| 6 | Text and spans | green |
| 7 | Images | green |
| 8 | Document API | green |
| 9 | JS façade | green |
| 10 | Parity artefacts | green |
| 11 | Wild sweep | green |
| 12 | `.REF` parsing | green |
| 13 | Traversal API: `resolve()` | green |
| 14 | `hypp-cli` scaffold + `Renderer` abstraction | green |
| 15 | Six renderers | green |
| 16 | CLI commands + Round A (JVM fat jar) | green |
| 17 | CLI Round B (GraalVM native-image) | green |
| 18 | CLI Round C (`wasmWasi`) | green |
| 19 | CLI Round D (`macosArm64`) | green |

Per-phase detail: `doc/progress/phase-NN-<name>.md`. Phases 12–19 are planned
in `doc/PLAN-12-19.md` (approved 2026-08-18, not `doc/PLAN.md`'s original
11-phase roadmap — a follow-on plan, same convention). Execution is by
delegating each step to sub-agents with model overrides per that plan's
per-step assignments, not self-implemented directly — see that file's
"Execution mode" note.

**2026-08-21: Xcode.app installed and its license accepted on this machine — Phase 19 finished for
real.** The `onlyIf` opt-in guard added 2026-08-19 was deleted (`macosArm64` is a first-class
target again, same footing as `jvm()`/`wasmWasi()`, matching plan decision 4/13), and
`linkReleaseExecutableMacosArm64` now succeeds. The real linked binary
(`hypp-cli/build/bin/macosArm64/releaseExecutable/hypp-cli.kexe`) was run directly (no VM/
interpreter) against `st-guide_orig_en.hyp`: `dump --format html` produced well-formed HTML with
embedded base64 PNGs, and `extract-images` wrote all 15 valid PNGs (`file` confirmed), matching the
JVM/wasmWasi targets' verified counts on the same fixture. A new `macosArm64SmokeTest` Gradle
`Exec` task (mirroring `wasmWasiSmokeTest`'s idiom, one dev-machine shell script rather than a
WASI host) now runs both checks automatically and is wired into `check`. `./gradlew clean build`
is green end to end, including this new task. `doc/PLAN-12-19.md`'s follow-on plan (phases 12–19)
is now **complete**. See `doc/progress/phase-19-macos-arm64.md`'s "Finished" section for the full
writeup.

## Post-plan-12-19 follow-up (2026-08-21): local-link and encoding bugs across all renderers

Manual eyeballing of the real `macosArm64` `dump --format html` output (produced while closing
Phase 19) surfaced three issues, investigated and two fixed same round:

1. **Not a bug**: the character before bullet points is `·` (U+00B7 MIDDLE DOT), correctly decoded
   from Atari ST charset byte `0xFA` (`HypCharset.kt`) — the original document's own bullet glyph,
   confirmed against the standard Atari ST high-ASCII table and this fixture's zero unknown-escape
   count from the Phase 11 corpus sweep.
2. **Fixed — mojibake root cause**: `HtmlRenderer` emitted `<!doctype html><html><body>` with no
   `<head>`/charset declaration, so a browser guessing the wrong encoding for the UTF-8 bytes
   (`·` = `C2 B7`) renders the classic `Â·` mojibake — matching the "strange character before the
   bullet" report exactly. Fixed by adding `<meta charset="utf-8">`.
3. **Fixed — broken internal navigation, systemic across all 5 renderers**: `href="#<target>"`
   (HTML/Markdown/AsciiDoc/Org) and EPUB's node links referenced `target.value` with no matching
   anchor ever emitted anywhere. Fixed per-dialect: HTML gets `id="<index>"` on each `<h2>`;
   Markdown gets an `<a id="<index>"></a>` before each heading (GFM/CommonMark auto-slug from text,
   not an arbitrary id); AsciiDoc gets an explicit `[#<index>]` block attribute; Org gets a
   `:PROPERTIES:/:CUSTOM_ID: <index>/:END:` drawer (Org resolves `[[#id]]` against `:CUSTOM_ID:`,
   not the heading text); EPUB's internal links were retargeted from same-page fragments to
   cross-file `node-<target>.xhtml` hrefs, since each node is its own XHTML document there.
   `HtmlSpans.renderSpan` gained an optional `linkHref` hook (default: same-page fragment) so HTML
   and EPUB share the span-rendering logic while differing only in href shape.
   `MarkupSyntax.heading` gained an `index: Int` parameter so each dialect can emit its own anchor
   convention. All changes covered by new tests (`HtmlSpansTest`, `HtmlRendererTest`,
   `MarkupSyntaxTest`, `MarkdownRendererTest`, `AsciiDocRendererTest`, `OrgRendererTest`,
   `EpubRendererTest`); `./gradlew clean build` green. Security review: only new interpolation is
   `NodeIndex.value` (a validated non-negative `Int` from the document's own index table), no new
   attacker-controlled string reaches any sink — no findings.

Deferred (explicit user decision): a paragraph-reflow option (joining retro-hardwrapped lines back
into flowing paragraphs) — a separate feature, not done this round.

## Post-plan-12-19 follow-up (2026-08-22): EPUB image embedding, `--reflow`, title/author metadata

Three changes, all covered by new tests and `./gradlew clean build` green on both `hypp` and
`hypp-cli` (JVM/macosArm64/wasmWasi):

1. **EPUB image embedding** (previously deferred, `EpubRenderer`'s `imageEncoder` was an unused
   placeholder). Images referenced by any node's `Graphic.Image` are now encoded once each (deduped
   by image index) into separate `OEBPS/images/img-<index>.png` files with their own manifest
   `<item>` — the spec-idiomatic, best-e-reader-compatibility shape, over inlining as XHTML data
   URIs (deliberate choice over `HtmlRenderer`'s approach, per user decision this round).
2. **Paragraph-reflow option, done** (the item deferred above). `hypp-cli/.../cli/Reflow.kt`
   joins hard-wrapped lines back into paragraphs: a blank line or a bullet-marked line (`"· ..."`)
   never merges with a neighbor, everything else in a run gets joined with a plain space `Span`.
   Implemented once as a shared `HypDocument -> HypDocument` preprocessing step (per user decision),
   applied in `Commands.dump` ahead of every format via a new `--reflow` CLI flag, not duplicated
   per-renderer.
3. **EPUB title/author, previously hardcoded to `"hypp export"` with no author** (user report: doc
   title/author wrong/unset). Extended headers id 1 (`@database`) and id 5 (`@author`) — documented
   in `hyp.h` as `HYP_EXTH_DATABASE`/`HYP_EXTH_AUTHOR` but previously only captured as
   `ExtendedHeader.Unknown` — are now modeled as `ExtendedHeader.Database`/`Author` (same pattern as
   `Charset`/`Default`) and exposed as `HypDocument.title`/`author`. `EpubRenderer`'s `content.opf`
   derives `<dc:title>` from `document.title`, falling back to the first node's name and finally to
   `"hypp export"` when absent; `<dc:creator>` is emitted only when `document.author` is present.
   Both go through the existing `HtmlSpans.escapeHtml` (XML text-content escaping, same sink as
   every other document-derived string in this renderer). Golden JSON fixtures for
   `hcp_orig_en`/`st_guide_orig_en` regenerated to reflect the new `ExtendedHeader` variants.

Security review (both changes): no findings — image filenames key off the numeric `ImageNode.index`
(not the attacker-controlled `image.name`, unlike `extractImages`' `sanitizeImageFileName` sink), and
title/author reuse the same escaping already applied to every other document-derived string reaching
this renderer's XML output.

## Post-plan-12-19 follow-up (2026-08-22, round 2): EPUB rendering-fidelity bugs vs. hypviewer

User compared the online hypviewer rendering of `st-guide_orig_en.hyp` against `hypp-cli`'s EPUB
output and reported five issues. Root-caused all five by instrumenting the real parser against the
corpus fixture (a throwaway `jvmTest`, deleted before commit) rather than guessing, then fixed the
two that were genuine renderer bugs. `./gradlew allTests` green on `hypp-cli` throughout (JVM,
`wasmWasi`, `macosArm64`); every generated `.xhtml` in a regenerated `st-guide_orig_en.epub`
independently re-verified well-formed with Python's `xml.dom.minidom` outside the JVM toolchain too.

1. **Fixed — "PCDATA invalid Char value 3" / apparent truncation.** Root cause: a decoded `.hyp`
   line can contain a literal C0 control byte (confirmed: `st-guide_orig_en.hyp` node 1
   "Introduction" has a real byte `0x03` mid-sentence — an Atari-font icon glyph, e.g. a keyboard
   arrow-key icon, that `HypCharset.AtariSt`'s clean-room table (Wikipedia-sourced, high range
   0x80-0xFF only — see `doc/PLAN.md`'s locked "spec sources" decision) has no mapping for and so
   passes through as a raw control character). XML 1.0 forbids raw C0 controls in element content
   outside tab/LF/CR; `HtmlSpans.escapeHtml` previously only escaped `&<>`, so the byte reached
   `EpubRenderer`'s strict XHTML unescaped and broke well-formedness — a strict reader (Apple
   Books/Preview) renders only up to that point, which looks like the document got truncated.
   `escapeHtml` now maps every such byte to Unicode's own public "Control Pictures" block (U+2400 +
   code) — always-safe XML content, and a real, independently-citable (unicode.org) standard, not a
   copy of hypview's own `cp_atari.h` icon table (which the project's clean-room policy places
   out of bounds, and which is itself platform-inconsistent — its Win32 build maps the same bytes
   differently — so treating it as *the* spec would have been wrong regardless).
2. **Fixed — missing small icons before Main's section headers, and the missing rule above
   "But why hypertext?".** Same root cause, both are `Graphic.Line` records (not images): node 0
   has 15 of them at specific character-cell `y` rows next to each section header; node 1 has one
   under its title and one right above "But why hypertext?" (confirmed via the throwaway
   instrumentation, not assumed). `HtmlRenderer`/`EpubRenderer` only ever handled `Graphic.Image`
   and silently dropped `Line`/`Box`/`RoundedBox` entirely. Both renderers now bucket a node's
   graphics by row (`HtmlSpans.graphicsByRow`) and interleave each row's markup right before that
   row's `<p>`, instead of dumping all graphics before all text. A `Line`/`Box`/`RoundedBox`'s fill
   pattern and arrow-direction bits have no confirmed visual mapping (`doc/format-notes.md`'s
   "Line/box/rbox `Data` byte" entry — corpus filenames don't line up with the decoded flags, no
   rendering oracle exists) — rather than fabricate a specific shape, every such graphic sharing a
   row collapses into one plain `<hr/>` (`HtmlSpans.nonImageGraphicMarkup`), so the row's decorative
   intent survives without pretending to know its exact shape. `Box`/`RoundedBox` still render as
   the same generic rule as `Line` — not the bordered frame they likely represent — since neither
   the user's report nor the corpus data gave enough to implement that with confidence; flagged as
   a known remaining gap, not silently dropped.
3. **Fixed — Main node's two-column layout, indentation and inter-column spacing collapsed.** The
   `.hyp` format's lines are a fixed-width character-cell grid: indentation and column gaps are
   literal runs of space characters, not markup. Both renderers' `<body>` now sets
   `white-space:pre-wrap;font-family:monospace` (`HtmlSpans.HTML_BODY_STYLE`, one shared constant)
   instead of relying on the reader's default whitespace-collapsing/proportional-font rendering.
4. **Not a renderer bug, but a real bug found while fixing #2**: `--reflow` (`Commands.dump` wiring
   was already correct) joins hard-wrapped lines into fewer paragraphs, which shifts every row
   number after the join point — and `reflow()` was leaving `Node.graphics`' row-based `y`
   untouched, so combining `--reflow` with the new row-interleaving from #2 silently misplaced
   every graphic. Fixed in `Reflow.kt`: `reflowWithRowMap` now also returns the original-row →
   reflowed-row mapping, and `reflow()` carries every graphic forward to its paragraph's new row
   via `Graphic.remappedTo` (`.copy(y = ...)` for `Line`/`Box`/`RoundedBox`, a reconstructed
   `Graphic.Image` since it isn't a data class) before that mapping was in place, verified by
   regenerating the real corpus EPUB with `--reflow` and confirming the rule above "But why
   hypertext?" landed on the right paragraph both with and without the flag.
5. **Not fixed, flagged to the user instead of guessed at**: the user's other observation for the
   "Introduction" node's top rule and the Main node's arrow markers looking like small icons rather
   than a rule in hypviewer specifically — hypview's own reference renderers (HTML/GTK/PDF/XML, per
   research into `~/git-repos/hypview`, constants/behavior only, no code lifted — clean-room
   boundary respected) all draw these as real vector graphics (Cairo strokes, HTML5 canvas+JS, PDF
   vector ops), which this project's plain reflowable-text renderers have no equivalent for and
   aren't going to grow one for; the generic `<hr/>` from fix #2 is the deliberate, documented
   ceiling here.

Security review: no findings. This round's changes are output-safety hardening (the escaping fix
closes a real XML-well-formedness gap) and pure in-memory data transforms (row bucketing, row
remapping) — no new I/O, parsing of untrusted input, or interpreter-boundary crossing beyond the
existing `HtmlSpans.escapeHtml` sink, which is strictly safer after this round than before it.

### Next tasks (not started — pick up in a fresh session)

1. **Render `Graphic.Line`/`Box`/`RoundedBox` as real images in EPUB/HTML, not a generic
   `<hr/>`.** Currently every such graphic sharing a row collapses into one plain rule
   (`HtmlSpans.nonImageGraphicMarkup`) because there's no confirmed visual mapping for the fill
   pattern / arrow-direction bits — see `doc/format-notes.md`'s "Line/box/rbox `Data` byte" entry
   (corpus filenames don't line up with the decoded flags, no rendering oracle exists) and this
   round's finding #5 above (hypview's own renderers draw these as real vector graphics — Cairo
   strokes, HTML5 canvas+JS, PDF vector ops — which this project has no equivalent for). The task:
   rasterize each `Graphic.Line`/`Box`/`RoundedBox` to a small PNG (reusing/extending
   `ImageEncoder`/`StoredPngEncoder`'s pattern) sized from its `width`/`height` character cells, and
   embed it the same way `Graphic.Image` already is (`OEBPS/images/...` + manifest `<item>` for
   EPUB, data URI for `HtmlRenderer`) — plugged into the *same* row-interleaving machinery added
   this round (`HtmlSpans.graphicsByRow`), replacing `nonImageGraphicMarkup`'s `<hr/>` fallback with
   an actual `<img>`. Before drawing anything, revisit the semantic-mapping gap: the clean-room
   policy (`doc/PLAN.md`'s locked "spec sources" decision — `hypview`'s `.c` files and its
   `cp_*.h`/`hyp/*.h` tables are out of bounds, constants-only from `hyp.h`/`hypfmt.ui`) still
   applies here exactly as it did to the charset table fixed this round, so the arrow-direction /
   fill-pattern visual style needs an independently-sourced convention (or a deliberately
   simple/neutral one — e.g. a plain solid rule/box outline — chosen and documented as such) rather
   than reverse-engineering hypview's drawing code.
2. **Generalize this round's "fixed-grid canvas" pattern**, not just apply it ad hoc per bug. The
   takeaway from this round: a `.hyp` node is a character-cell canvas, not free text —
   (a) whitespace is data (disable collapsing, use a fixed-width font — done this round via
   `HtmlSpans.HTML_BODY_STYLE`); (b) any positioned decoration (image, line, box) should be keyed to
   its row and interleaved into the text stream at render time, not dumped as a block before/after
   the content (done this round via `HtmlSpans.graphicsByRow`); (c) any transform that changes row
   count (reflow, wrapping, and — relevant once task 1 above lands — a graphic that itself spans
   multiple rows, like the `y=55,height=3` `RoundedBox` seen in node 1 of the corpus fixture, which
   nothing yet accounts for) must carry a row-remapping table forward so positioned elements stay
   correct (done for `--reflow` this round via `Reflow.kt`'s `reflowWithRowMap`/`Graphic.remappedTo`).
   Worth writing up explicitly (e.g. in `doc/guide/concepts.md` or a new `doc/format-notes.md`
   entry) as a named pattern before task 1 adds a third row-consuming feature that would otherwise
   re-derive it ad hoc a third time.

### Other context for whoever picks this up

- Root-causing this round's bugs used a throwaway `jvmTest` (not committed) that opened
  `Corpus.open("st-guide_orig_en")` and printed `node.graphics`/`node.lines` directly — reach for
  the same technique before guessing at graphic positions/counts again; the real fixture's node 0
  ("Main") has 15 `Graphic.Line`s and node 1 ("Introduction") has 2 `Graphic.Line` + 2 `Graphic.Box`
  + 1 `Graphic.RoundedBox`, none of which task 1 above renders yet (still generic `<hr/>`).
  `st-guide_orig_en.hyp` is vendored at
  `hypp-cli/src/commonTest/resources/corpus/st-guide_orig_en.hyp`.
- A regenerated `st-guide_orig_en.epub`/`.html` (with `--reflow`, plus the epub's contents
  extracted to a sibling directory) was left for by-eye inspection in
  `/private/tmp/claude-501/-Users-mrolappe-studio-hypp/688f720a-9d59-46ba-a591-bff525c1856e/scratchpad/epub-html-check`
  — a session-scoped scratch path, not a permanent location; regenerate via
  `./gradlew run -Pargs="dump <fixture> --format <epub|html> --out <path> --reflow"` from
  `hypp-cli/` if it's gone.
- The five original bug-report items are otherwise fully closed: control-character XML safety,
  row-interleaved rules for `Graphic.Line`/`Box`/`RoundedBox` (generic shape pending task 1),
  monospace/`pre-wrap` layout preservation, and the `--reflow` + graphic-row interaction bug found
  along the way. Only the *visual fidelity* of task 1 (real icons/lines/boxes instead of a generic
  rule) remains open.

## Task 1 done: Graphic.Line/Box/RoundedBox render as real inline SVG (2026-08-22)

Closes this doc's "Next tasks" task 1 above. The visual-mapping gap was resolved using only
in-bounds, first-party sources: the HCP compiler's own `@line`/`@box`/`@rbox` command-reference
nodes and the "Füllmuster" fill-pattern demo page in `hcp_orig_de.hyp` (viewed via the public
`hypview.cgi` mirror — sanctioned in `doc/PLAN.md` as a by-eye sanity check, not an automated
oracle — plus a user screenshot of the rendered "Füllmuster" page). Findings:

- **Arrows** (`<Attr>` 0/1/2/3 = none/start/end/both) exactly match hypp's existing bit0/bit1
  decoding — confirms it was already correct. The `lines.hyp` filename-mismatch note in
  `doc/format-notes.md`'s "Line/box/rbox `Data` byte" entry is now understood to be mislabeled
  filenames in that third-party corpus, not a decoding bug — that entry should be considered
  resolved, not left as an open gap, next time it's read.
- **Line style** 1-7: solid, long dash, dots, dash-dot, dash, dash-dot-dot, dotted (standard
  GEM/VDI polyline styles) — 0 (unset) renders the same as 1 (solid).
- **Fill pattern** 0-8: confirmed by screenshot to be a monotonic hollow-to-solid density
  gradient, not a distinct hatch shape per level.
- **Compositing**: the HCP doc states objects (including text) draw in "OR mode" — translucent,
  never obscuring what's underneath. Implemented as `mix-blend-mode:multiply` on every emitted
  shape.

Per user direction: inline SVG (sized in `ch`/`em`, scales with the monospace grid, no
character-cell-to-pixel constant needed) is the path wired into `HtmlRenderer`/`EpubRenderer`; a
separate, reusable pixel rasterizer was also built (not wired into either renderer yet) so a
future consumer needing real pixels doesn't duplicate PNG-encoding logic.

New files: `VectorGraphic.kt` (bit-decoded values → rendering-ready dash/fill-level mapping),
`VectorGraphicSvg.kt` (SVG renderer + shared `<defs>` of 9 fill patterns + 2 arrow markers),
`VectorGraphicRaster.kt` (pixel rasterizer, `ponytail:` no anti-aliasing / single-radius corners —
upgrade to a proper scanline rasterizer if a consumer needs higher fidelity). `StoredPngEncoder`
gained `encodeRgba(width, height, rgba)`, extracted from `encode(ImageNode)`, as the shared PNG
terminus for both `ImageNode` and the new rasterizer. `HtmlSpans.nonImageGraphicMarkup` (the old
`<hr/>` fallback) is now `vectorGraphicMarkup`, wired into both renderers exactly where the old
function was. All Kotlin targets (`jvm`, `macosArm64`, `wasmWasi`) pass; manually verified against
`st-guide_orig_en.hyp` node 1's real 2 Line + 2 Box + 1 RoundedBox (the `y=55,height=3`
RoundedBox cited in this doc's task 2 renders correctly outside `--reflow`, with `rx="1.0"`).
Security review of the round's diff found nothing reportable — every new SVG attribute value is
numeric (`Int`/`Double`/a fixed internal dash-pattern map), never raw file text, so there's no
XML/attribute-injection path even though the underlying ints are file-controlled.

**Newly discovered, out of scope for this round — needs a decision before fixing:**
`Node.kt:207`'s `val width = u8(pos + 5)` parses `Graphic.Line.width` as an *unsigned* byte, but
the HCP `@line` doc specifies X-length as signed, `-127..126` (negative = line drawn bottom-left
to top-right instead of top-left to bottom-right). So `Graphic.Line.width` can never actually be
negative today — the "negative dx" branch in `VectorGraphicSvg`/`VectorGraphicRaster` (this
round's new code) is currently dead with real parsed data. Not fixed here because it's a parser
change outside this task's approved scope (rendering, not decoding), and changing the sign
interpretation could ripple into `NodeTest`'s existing assertions. Whether `lines.hyp`'s corpus
actually exercises a byte value >127 for this field (i.e. whether this gap is corpus-observable or
only a latent spec-conformance gap) hasn't been checked yet.

Task 2 (the fixed-grid-canvas write-up + multi-row-graphic/reflow row-remapping gap) is still open,
unchanged by this round.

## Task 2 done: fixed-grid-canvas write-up + multi-row-graphic reflow fix (2026-08-22)

Closes this doc's "Next tasks" task 2 above. Two parts:

1. **Write-up**: new "A node is a fixed-grid canvas, not free text" section in
   `doc/guide/concepts.md`, naming the pattern generalized across this round's earlier fixes
   (whitespace-is-data, row-interleaved decorations, row-remapping-on-transform) so a future
   row-count-changing transform reaches for the same shape instead of re-deriving it.
2. **Fix**: `Reflow.kt`'s `Graphic.remappedTo` only remapped a graphic's start row (`y`), leaving
   `height` (a row *count* for `Graphic.Box`/`Graphic.RoundedBox`, a row *delta* `dy` for
   `Graphic.Line`) unchanged — so a multi-row graphic whose rows all merged into one reflowed
   paragraph still claimed its old, now-meaningless row span (the `y=55,height=3` `RoundedBox` in
   the corpus fixture was the motivating case). Fixed by mapping *both* the start row and the
   graphic's end row (`y + height - 1` for Box/RoundedBox, `y + height` for Line, matching
   `VectorGraphic.toVectorGraphic`'s dy semantics) through the same `rowMap`, then recomputing
   `height` from the distance between the two mapped rows instead of copying it through. New tests
   in `ReflowTest.kt` cover a `Box` and a `Line` each collapsing into a single reflowed row.
   `./gradlew clean build` green on all three `hypp-cli` targets (`jvm`/`macosArm64`/`wasmWasi`).
   Security review: no findings — pure in-memory integer arithmetic over already-validated `Graphic`
   fields, no new I/O or output sink.

## Post-plan follow-ups (2026-08-15)

The plan's 11 phases are done; these are follow-up tasks done afterward, not
part of the phase roadmap:

1. **includeBuild integration check** — done. See
   `doc/progress/phase-01-skeleton.md` § Remaining and `doc/LEARNINGS.md` §
   "Post-plan follow-up: includeBuild integration check".
2. **Unknown-escape files named** — done. See
   `doc/progress/phase-11-wild-sweep.md` and `doc/LEARNINGS.md` §
   "Post-plan follow-up: unknown escapes by file".
3. **CLI consumer design space** — brainstormed, no decisions. See
   `doc/cli-design-space.md`.
4. **Library documentation for human and agentic/AI consumers** — see
   `doc/guide/` (`overview.md`, `concepts.md`, `api.md`).

## User-reported EPUB/PDF rendering bugs (2026-08-22): two fixed, one open

Diagnosed from a user's visual comparison of the generated EPUB/PDF against `hypview`'s own
rendering of `st-guide_orig_en.hyp` ("Main" and "Introduction" nodes). Five symptoms reported,
tracing back to two root causes plus one open structural gap:

1. **Fixed — `Graphic.Line`'s `height` off-by-one.** `height` is a 1-based row *count*, the same
   as `Graphic.Box`/`Graphic.RoundedBox`'s (its bounding box is `height` rows tall), not a row
   *delta*. `VectorGraphic.kt`'s `Graphic.Line.toVectorGraphic()` was passing `height` straight
   through as the SVG endpoint delta, so every single-row horizontal separator (`height = 1`, the
   common case — confirmed against 8 of `lines.hyp`'s 10 demo lines and all 15 of "Main"'s TOC
   separators) rendered as a visible diagonal instead of flat. Fixed by using `height - 1` as the
   delta; `Reflow.kt`'s row-remapping for `Graphic.Line` brought in line with the same count
   semantics (it previously used a different off-by-one than Box/RoundedBox, which happened to be
   self-consistent before this fix but not after).
2. **Fixed — one `<p>` per text row.** `HtmlRenderer`/`EpubRenderer` wrapped every row of a node's
   fixed-grid text in its own `<p>...</p>`, stacking a browser/e-reader paragraph margin between
   every single line — a `.hyp` node's blank lines are already literal blank rows in the grid, so
   this doubled up on spacing the source file didn't call for. Fixed: a run of rows with no graphic
   between them now shares one `<p>`, its rows joined by `\n` (preserved by the existing
   `white-space:pre-wrap` body style); a `<p>` only closes/reopens where a graphic must be
   interleaved between two rows.
3. **Open — no horizontal (`x`) offset, no row-overlay.** `Graphic.x` is parsed and carried on
   every model object but no renderer ever reads it — every decoration renders flush to the left
   margin regardless of its real column. Worse, a graphic is emitted as its own block-level element
   *before* the `<p>` for the row it decorates, never layered on top of it — so a `Graphic.Box`
   meant to visually surround a line of text (e.g. the "Have fun with ST-Guide!!!" box, confirmed
   in the generated markup: box, then a blank `<p></p>`, then the text, as three separate stacked
   blocks) can never actually surround it. This is very likely also behind the "extra triangular
   lines" reported in "Main"'s TOC — an arrow-only decoration meant to sit as a short indent marker
   at a specific column instead renders as a full-width phantom line in the wrong place. User chose
   the fix direction: rework `HtmlRenderer`/`EpubRenderer` to lay out each node as a CSS grid (text
   rows as grid rows) with every graphic absolutely positioned by its real `x`/`y`/`width`/`height`
   in `ch`/`em` units and layered via `z-index` + the existing `mix-blend-mode:multiply`, instead of
   the current block-sequential interleaving. Not started — worth a spot-check on Apple
   Books/Kindle EPUB rendering once built, since `position:absolute` support inside reflowable EPUB
   content varies by reader.

Commit `8935200` (items 1-2). Verified against the real `st-guide_orig_en.hyp` fixture (not just
unit tests): the line above "But why hypertext?" now renders `y1="0" y2="0"`, and the
"Introduction"/"Main" nodes' paragraph spacing collapsed to match the source file's own blank-line
spacing.

**New bug found incidentally while regenerating the EPUB for review (not fixed, not part of the
original 5-symptom report): internal links to `EXTERNAL_REF` targets render as broken `node-<N>.xhtml`
hrefs.** `EpubRenderer`'s span rendering (`HtmlSpans.renderSpan(span) { target -> "node-${target.value}.xhtml" }`)
always builds an internal cross-file href from `Span.link.target`, without checking whether that
`NodeIndex` actually resolves to a `Node` this document renders its own page for. `st-guide_orig_en.hyp`
has 22 `EXTERNAL_REF` index entries (links to nodes in *other* `.hyp` files, e.g. `hcp.hyp/Main`,
`reflink.hyp/Main`) that correctly have no corresponding `Node`/xhtml file — `HypDocument.resolve()`
already distinguishes these as `ResolvedTarget.ToExternalRef` vs `ResolvedTarget.ToNode`, but
`EpubRenderer` never calls it, so every such link points at a `node-<N>.xhtml` file that was never
written. Confirmed via Calibre's `ebook-convert` (`OEBPS/node-86.xhtml` etc. reported "not found"
for all 21 of the 22 external-ref targets that fall in-range; `EXTERNAL_REF`/`SYSTEM` entry indices
78-100). Likely fix: use `document.resolve(target)` in `EpubRenderer`'s `linkHref`, and either drop
the `<a>` wrapper (render plain text) or point at an anchor with the external file/node name for
`ToExternalRef`, since no `.REF` file is loaded to know a real destination path. Not investigated
further or fixed — found while regenerating output for a visual review, not the round's task.
