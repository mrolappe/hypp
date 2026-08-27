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

## CSS-grid/overlay rework: `Graphic.x` now honored, graphics layered not stacked (2026-08-23)

Closes the "Open — no horizontal (`x`) offset, no row-overlay" item from the 2026-08-22 bug-report
section above. `HtmlRenderer`/`EpubRenderer` previously interleaved every `Graphic` as a block-level
sibling *before* the `<p>` for the row it decorates (`HtmlSpans.graphicsByRow` + a per-renderer
paragraph-open/close loop, duplicated verbatim between the two files) — `Graphic.x` was parsed into
the model but never read by either renderer, and a graphic meant to visually surround its text (e.g.
the "Have fun with ST-Guide!!!" `Box`) rendered as three stacked blocks instead.

Fixed by replacing both renderers' duplicated loop with one shared `HtmlSpans.renderGrid(node,
linkHref, imageTag)`: every node's lines collapse to a single `<p style="margin:0">` (rows joined by
`\n`, unconditionally now — the only reason multiple `<p>`s existed was to interleave graphic markup
in DOM order, which is moot once graphics are positioned overlays), and every `Graphic` renders as a
sibling `<div style="position:absolute;z-index:1;top:<y>em;left:<x>ch">` — no CSS Grid; `top`/`left`
in `em`/`ch` on a `position:relative;line-height:1` container was simpler and needs no
`grid-row: span N` bookkeeping for multi-row graphics (a `RoundedBox` at `y=55,height=3` just gets
`top:55em`, since `VectorGraphicSvg` already sizes that graphic's own SVG `height="3em"`).
`Graphic.centered` (`x == 0`, already documented on the model) is now honored via
`left:50%;transform:translateX(-50%)` instead of silently rendering flush-left. `graphicsByRow` was
deleted (only caller was the removed loop). `Reflow.kt` needed no change — it already rewrites
`Graphic.y`/`height` before rendering, and `renderGrid` reads `y`/`x` fresh at render time, never
caching a pre-baked position.

Verified against the real `st-guide_orig_en.hyp` fixture (not just unit tests): regenerated HTML/EPUB
with and without `--reflow`. The `y=55,height=3` `RoundedBox` renders as one continuous absolutely
positioned box in both cases (`top:55em`/`top:27em` under `--reflow`, still one `height="3"` SVG, not
split). Real corpus graphics carry a wide spread of nonzero `x` values (428 positioned graphics
total, only 1 at `x=0`/centered) — the flush-left bug was real and pervasive, not a corner case. EPUB
XHTML re-verified well-formed with Python's `xml.dom.minidom` outside the JVM toolchain.
`./gradlew clean build` green on all three `hypp-cli` targets (JVM/`macosArm64`/`wasmWasi`).

Tests: `HtmlSpansTest`'s `graphicsByRowBucketsByYAndClampsOutOfRangeRows` (tested the deleted
function) replaced with direct `renderGrid` tests (no-graphic single-paragraph shape, real
positioning, centering, out-of-range clamping, multi-row-graphic needs only `top`).
`HtmlRendererTest`'s `expectedParagraphs()` helper — which re-derived the renderer's own row-bucketing
logic and cross-checked an exact `<p>`-tag count, a fragile test/impl duplication — replaced with
`expectedParagraph()` (always exactly one per node) plus a `document.nodes.size`-based count that no
longer needs to know anything about graphic positions, plus new positioning/centering tests.
`EpubRendererTest`'s `lineGraphicRendersAsInlineSvgBeforeItsRow` (asserted substring *ordering*, the
exact assumption this rework invalidates) rewritten to assert the wrapper's `top`/`left` style
directly; its `x=0` fixture is now a dedicated centering test, with a new nonzero-`x` test added for
literal-column placement. `CommandsTest`'s reflow test updated for the new `<p style="margin:0">`
literal. `VectorGraphicSvgTest`/`EpubRendererWellFormednessTest` needed no changes (position-agnostic).

Security review: no findings. The only newly interpolated model field is `Graphic.x` (an `Int` parsed
as an unsigned byte, same threat profile as the pre-existing `Graphic.y`) — `Int.toString()` cannot
produce attribute-breakout characters, so there's no new injection path into the `style="..."`
attribute it's placed in.

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

## Group A done: four SVG rendering-fidelity bugs fixed (2026-08-26)

Per `doc/PLAN` "there are issues at cozy floyd" (Group A, bugs 1/2/3/5). Same two files as Task 1
(`VectorGraphicSvg.kt`, `VectorGraphic.kt`), one focused commit per fix, TDD red-green throughout.

1. **Box stroke was clipped, boxes rendered smaller than their nominal footprint**
   (`VectorGraphicSvg.kt`, `Box.toSvg()`). The `<rect>` spanned the full `0,0,w,h` viewBox with
   `stroke-width="0.08"` centered on the path — SVG's default clipping cut the outer half of the
   stroke (0.04 units) on all 4 edges. Fixed by padding the viewBox by the full stroke width and
   offsetting the rect by half the stroke width (`x=y="0.04"`), while keeping the `<svg>`
   `width`/`height` (`ch`/`em`) at the box's original nominal `w`×`h`. New test
   `boxStrokeIsFullyContainedWithinTheViewBox` parses the rendered `viewBox`/`rect`/`stroke-width`
   and asserts the stroke's outer edge never exceeds the viewBox bounds.
2. **`RoundedBox` rendered as a pill/stadium shape on short boxes** (`VectorGraphic.kt`). The fixed
   `ROUNDED_BOX_CORNER_RADIUS_CELLS = 1.0` constant ignored the box's own height; SVG clamps `ry` to
   `height/2`, so a `height=1`/`height=2` `RoundedBox` (both present in the real corpus) rendered
   with fully-rounded stadium ends. Replaced with `roundedBoxCornerRadius(width, height) =
   min(1.0, min(width, height) / 4.0)`. New test
   `shortRoundedBoxCornerRadiusIsClampedToHalfHeightNotAFixedConstant` asserts `height=1`/`height=2`
   cases stay `<= height/2`.
3. **Arrow markers were real filled triangles but invisible on long lines**
   (`VectorGraphicSvg.kt`, `ARROW_START_MARKER`/`ARROW_END_MARKER`). No `markerUnits` meant the SVG
   default `markerUnits="strokeWidth"` applied, scaling the `markerWidth="4"` marker to an absolute
   `0.32` viewBox units against the line's `stroke-width="0.08"` — imperceptible on the corpus's
   100+-unit-long lines. Fixed by adding `markerUnits="userSpaceOnUse"` and sizing both markers at
   `markerWidth="0.8" markerHeight="0.8"` (absolute viewBox units, independent of stroke width). New
   test `arrowMarkersUseAbsoluteUserSpaceSizingSoTheyDontShrinkWithStrokeWidth` asserts both marker
   defs carry `markerUnits="userSpaceOnUse"`.
4. **A vertical line (`dx=0`) rendered as a 1-unit diagonal, not vertical**
   (`VectorGraphicSvg.kt`, `Line.toSvg()`). `val w = abs(dx).coerceAtLeast(1)` (needed to avoid a
   degenerate zero-width viewBox) was reused as `x2`, so `dx=0` produced `x2=1` instead of `x2=0`.
   Fixed by keeping the viewBox/`width` clamp (`w`) separate from the endpoint coordinate, which now
   derives from the real unclamped `abs(dx)` — `dx=0` now yields `x1==x2==0`. New test
   `zeroDxLineIsTrulyVerticalNotDiagonal` asserts `x1==x2` for a `dx=0, dy>0` line.

Verified against the real corpus fixture (not just unit tests): rendered `st-guide_orig_en.hyp`'s
"Lines, arrows and boxes" node via `HtmlRenderer` (`java -jar hypp-cli-all.jar dump ... --format
html`), with and without `--reflow`. Confirmed in the raw output: every `<rect>` now has
`x="0.04" y="0.04"` inside a `viewBox` padded by `0.08` (fix 1); the node's `RoundedBox` entries show
a spread of `rx`/`ry` values (`0.25`/`0.5`/`0.75`/`1.0`) driven by each box's own height rather than
a uniform `1.0` — the `height=1` box is `rx="0.25"`, well under a stadium shape (fix 2); both
`<marker>` defs in the shared `<defs>` carry `markerWidth="0.8" markerHeight="0.8"
markerUnits="userSpaceOnUse"` (fix 3). No `dx=0` line exists in this particular corpus node, so fix
4 is covered by its unit test only. `--reflow` output shows the same fixes intact (rounded-box radii
spread `0.25`–`1.0`, stroke padding, marker units all present), confirming `Reflow.kt`'s row-only
remapping doesn't disturb any of these fields, as expected.

`./gradlew hypp-cli:build` green across all three targets (`jvm`, `wasmWasi`, `macosArm64`),
including the `macosArm64SmokeTest`/`wasmWasiSmokeTest` real-binary checks.

Security review: no findings. All four changes are numeric SVG attribute arithmetic (stroke-width
padding, a min/clamp formula, fixed marker-unit strings, an abs-value endpoint) — no new
document-derived text reaches any output sink; every interpolated value is already the same
validated `Int`/`Double` model data this renderer emitted before.

## Group B done: `Graphic.Line.width` is a signed excess-128 x-length (2026-08-26)

Per `doc/PLAN` "there are issues at cozy floyd" (Group B, bug 4). Spike-then-fix, as the plan
required — `Node.kt` was not touched until the byte evidence was in.

**Symptom.** Every `Graphic.Line` parsed out of `st-guide_orig_en.hyp` had an implausible width:
"Main"'s lines were 128/130 on a page ≤64 columns; "Symbol bar"'s 14 short vertical connectors were
*all* exactly 128 (0x80) despite obviously differing lengths; the standard page rule was 199. Box
and RoundedBox widths in the very same nodes were clean small values, so the record offsets
themselves were not misaligned.

**Spike (B1).** New report-only raw-byte scan,
`src/jvmTest/kotlin/de/rholambdapi/hypp/LineGraphicScan.kt`, run via `./gradlew lineGraphicScan`
(network-free, not part of `build`/`check`; same style as `CorpusSweep.kt`'s `ESC 0xa4` scan). It
walks each decompressed node's prologue exactly as `parseNode` does — so record starts are real, not
scavenged — and dumps every `ESC 0x33/0x34/0x35` body tagged with its node name, alongside four
candidate reinterpretations of the width byte plus a document-wide histogram. Kept permanently (B5):
it is cheap, offline, and is the standing evidence behind the `doc/format-notes.md` entry.

**Confirmed root cause.** The line width byte is the HCP `@line` command's **signed x-length stored
excess-128** (`xLength = byte - 128`), not an unsigned column count and not two's complement.
`hcpcmds.ui` documents the parameter ranges as `X-offset: 1..255`, `X-length: -127..126`,
`Y-length: 0..254`; −127..126 biased by 128 lands on bytes 1..254, which is the format's usual
NUL-avoiding motive (as with the base-255 fields) applied to a signed one-byte field. `hypfmt.ui`'s
shared graphic-object field list says only "1 byte width of the object in characters" and never
reconciles the two — the documented gap.

Byte evidence (all from the scan; full detail in `doc/format-notes.md`):

- "Lines, arrows and boxes" has a ten-arrow fan sharing origin `x=17, y=10` with width bytes
  `112 113 115 119 124 132 137 141 143 144` → `-16 -15 -13 -9 -4 +4 +9 +13 +15 +16`, exactly
  symmetric about zero. Two's complement gives `112 … -124 -119 …` — not symmetric, which is why the
  earlier "just sign-extend it" hypothesis was correctly rejected.
- "Symbol bar"'s 14 connectors all decode to x-length **0** — purely vertical, which is precisely
  what a connector between an icon row and its caption is. Their real differing lengths live in
  `height` (3 or 5), so the suspicious uniformity turned out to be correct data read wrongly.
- The page rule at the top of 51 nodes is `[01 02 01 c7 01 31]` → `x=1, x-length=71, height=1`:
  columns 1..72, matching the fixture's page width (it was rendering 199 columns wide — the
  "too-wide horizontal rule" symptom).
- Box/RBox records interleaved at the same body offset are plain unsigned (`w=32/16/8/4/2` with
  `h=16/8/4/2/1`), and the line drawn across the largest box decodes to x-length 32 — exactly that
  box's width. **Box/RoundedBox therefore need no change and were left untouched.**
- Across the whole fixture no line width byte falls outside 112..199 (x-lengths −16..+71) — a tight
  cluster around 128 that no other reading explains.

**Fix (B3).** One line in `Node.kt`'s `ESC_LINE` branch: `width - LINE_X_LENGTH_BIAS` (128), with
the constant and a comment naming the gap. `Graphic.Line`'s KDoc now states that, alone among the
graphics, its `width` is signed. Nothing downstream needed adapting: `VectorGraphic.kt` already does
`dx = width` and `VectorGraphicSvg.kt` already handles negative `dx` (Group A's `dx=-10` case).

TDD red→green: new `NodeTest.stGuideLineWidthsDecodeAsSignedXLengths` asserts Symbol bar's 14
connectors are all 0 (not uniformly 128), Main's widths are `{0, 2}`, the arrow fan is the exact
symmetric sequence above, and every line width document-wide lands in the spec's −127..126. Confirmed
failing against the pre-fix parser before the change was made.

**Existing tests (B4).** No regressions. Every other `Graphic.Line` in the test suite is
hand-constructed (`HtmlRendererTest`, `HtmlSpansTest`, `EpubRendererTest`, `ReflowTest`,
`VectorGraphicTest`), so the parser change cannot reach them; `NodeTest.linesHypDrawsBoxesAndLines`
asserts `y` and the flag decomposition only, never `width`/`x`.

**Goldens.** `doc/goldens/{lines,hcp_orig_en,st_guide_orig_en}.json` regenerated. Diff reviewed
mechanically as well as by eye: **258 changed fields, all of them `"width"` inside a `line` graphic,
every delta exactly −128, no other key or value moved anywhere in the three files.** New width range
across the corpus: −20..71. Regeneration is now a documented escape hatch rather than a hand-edit —
`HYPP_REGENERATE_GOLDENS=1 ./gradlew jvmTest --rerun-tasks`, then read `git diff` as the review.

This is a `src/commonMain` core-library fix, so it is format-agnostic: it benefits any renderer that
ever reads `Graphic`, though today only HTML/EPUB do (Markdown/AsciiDoc/Org/ANSI drop `node.graphics`
by construction, and `Reflow.kt` passes `width` through untouched).

Verification: `./gradlew clean build` green across `jvm`/`wasmJs`/`wasmWasi`/`macosArm64`;
`hypp-cli`'s own `clean build` green including the `macosArm64SmokeTest`/`wasmWasiSmokeTest`
real-binary checks.

Security review: no findings. The change is internal integer decoding — one subtraction on a value
already parsed from the same byte — introducing no new output sink and no new document-derived text.
The result is *more* constrained than before (a bounded −127..126 instead of an unbounded 0..255),
and every consumer already clamps or takes `abs()`. The new `LineGraphicScan.kt` is a
`jvmTest`-scoped, offline, opt-in reporter over a vendored fixture; it prints only integers and
already-trusted entry names, and is excluded from `build`/`check`. `ParityGoldenTest`'s
`HYPP_REGENERATE_GOLDENS` hatch is a test-only, opt-in file write under `doc/goldens/` with a fixed
path — noted in-comment as never to be set in CI, since it would mask a real golden regression.

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

**Fixed** in commit `160a87b` (the Group E round below, bug 9), together with the popup bug that
shares its root cause. `ebook-convert` on the regenerated EPUB now reports zero "referenced file
not found" warnings, down from 22.

## Round: image bitplane pixel values are Atari ST hardware pens (plan Group C, Bug 6)

**Symptom (reported against `hypview`'s rendering of `st-guide_orig_en.hyp`, "Main" node):** the
round "documentation" stamp in the banner image renders red but should be green, and the subtitle
under the wordmark renders lilac/purple but should be black.

**Investigation (spike first, no code touched).** Added `src/jvmTest/.../ImagePlaneScan.kt` +
`./gradlew imagePlaneScan` — a report-only scan of every multi-plane image in the vendored fixture
(same shape as `LineGraphicScan`), printing a pixel-value histogram, per-value bounding boxes, a
downsampled by-value art dump, and a rendered PNG. The fixture has exactly one multi-plane image:
index 77, 528x153, `planeCount=4`, `planePresent=255`, `planeFilled=0`. Under the existing decode it
yields four values — 0 (69.55%), 4 (20.56%), 10 (7.65%), 15 (2.24%) — read as `HypColor` ordinals:
`WHITE`, `BLUE`, `DARK_RED`, `DARK_MAGENTA`. Exactly the reported red and purple.

**The plan's hypothesis (wrong concatenation order) was ruled out, not confirmed.** The art dump
showed the current order already produces a *coherent* picture — a wordmark on the left, a round
stamp on the right (`x = 364..510`), a thin subtitle band (`y = 115..137`) — not the noise a wrong
bit order would give; and `hypfmt.ui`'s "1st Plane / Optional 2nd Plane / ..." enumeration says
planes are stored whole and sequentially, which is what the code does. The decisive argument
against reordering: `WHITE` and `BLUE` are already *correct* (the blue wordmark was never
reported as wrong), and every candidate reordering that turns value 10 or 15 into green/black also
moves value 4 off blue. So `decodePixels()` is right and is unchanged by this round.

**Root cause.** A bitplane pixel value is an Atari ST *hardware palette register* ("pen"), not a
GEM VDI colour index. `HypColor`'s ordinal is a VDI index — correct for the `0xa5`/`0xa6` text
colour escapes, which really do carry VDI indices (phase 6, `colors.hyp`), but wrong for image
pixels. GEM permutes pens and VDI indices, differently per plane count; at 1 plane the permutation
is the identity, which is why every single-plane fixture always looked right and the bug stayed
hidden. `hypfmt.ui` never states what a pixel value means, so this was a real documented gap.

**Fix.** `Palette.forPlaneCount(planeCount)` applies the standard GEM pen→VDI table (4-plane row
corpus-confirmed here, 2-plane row the documented convention with no corpus case, others identity);
`ImageNode.toRgba()` defaults to it instead of `Palette.AtariSt`. `ImageNode.pixels` keeps its raw
pen values — that is what the bytes actually are, and the golden
(`doc/goldens/st_guide_orig_en.json`, which captures `pixels`) is therefore unchanged, as
`ParityGoldenTest` confirms. `HypColor`'s RGB table was **not** touched; it was out of scope and the
pen mapping alone resolved both symptoms.

**Verification.** Mapped pen→VDI, image 77's four values become `WHITE`/`BLUE`/`DARK_GREEN`/`BLACK`,
and the rendered PNG reads "ST-Guide" in blue over "fairware from holger weets" in black beside a
green "ST-Guide documentation" stamp — matching hypview by eye and fixing both reported colours with
a zero-free-parameter table. Two tests, both confirmed red against the pre-fix code and green after:
`ImageNodeTest.fourPlanePensAreBlockConcatenatedLowPlaneFirstAndResolveThroughTheGemPenMapping`
(hand-built 4x1 4-plane bytes hitting pens 0/4/10/15 — locks the plane order *and* the mapping) and
`hypp-cli`'s new `StGuideBannerColorTest`, which encodes the real image 77 through both
`StoredPngEncoder` and `ImageIoPngEncoder`, decodes the PNG back with `javax.imageio.ImageIO` as an
independent oracle, and asserts the exact per-colour pixel counts plus the stamp's and subtitle's
bounding boxes. `./gradlew clean build` green in both builds.

**Security review** (binary pixel decode into an image encoder — no text/attribute output sink):
clean. The only new indexing is `Palette.forPlaneCount`'s fixed 4- and 16-entry tables, indexed by a
pen value that comes from `decodePixels()`, which composes it from at most `planeCount <= 8` bit
positions; `Palette.colorAt` already uses `getOrElse` so an out-of-range pen degrades to black
rather than throwing. `decodePixels()` itself is unchanged, so its existing bounds behaviour is
untouched. No new unbounded array indexing, no new parsing of attacker-controlled lengths.

## Group D done: `@limage` is a *line* image, and centring is image-only (Bug 7, 2026-08-27)

Per `doc/PLAN` "there are issues at cozy floyd" (Group D, bug 7: st-guide's "Main" banner sits on
top of the node's table of contents). The plan expected D1 to find no better centring signal than
`x == 0` and D2 to fall back on a width heuristic ("centred only if the image fits the text
column"). D1 found something better than either, so D2 landed a spec-backed fix instead of a
heuristic — see `doc/format-notes.md` § "`x == 0` is the only centring signal…" for the full
write-up and evidence.

**D1 — what the two spec sources actually say.**

1. `x == 0` really is the only centring signal there is; neither `hypfmt.ui` ("X == 0 for centered
   images") nor `hyp.h` ("(0 == centered)") offers a second, more explicit flag. But both scope it
   to **images**: `hyp.h`'s `x_offset` comment gives `@line`/`@box`/`@rbox` a valid `x` of 1-255 and
   `@image`/`@limage` a valid `x` of 0-255. So `centered` was never meaningful on the `Graphic`
   interface — it is an `Image` property.
2. The graphic **`width` byte separates the format's two image commands**: `hypfmt.ui` annotates it
   "(width == 1 for @limage)", `hyp.h` says "value used internally: 0, or 1 for limage". The old
   `Graphic.Image` doc comment claimed `width`/`height` were "present on the wire but ignored by the
   format for images (real files carry 0 for both)" — wrong, and the reason bug 7 existed. The HCP
   command reference for `@limage`: images placed this way "will be treated by ST-Guide as lines
   (limage == line image), meaning that text cannot be placed to either the left or the right of
   them and it isn't necessary to insert blank lines below the image, as ST-Guide will automatically
   move the following text down".
3. `st-guide_orig_en.hyp` uses both commands, and only the distinction explains its pages: 1
   placement with `width == 1` (the 528×153 banner on "Main", `x = 0`, centred) and 28 with
   `width == 0` (the 32×24 toolbar icons). "Symbol bar" is the control case — its icons sit at rows
   11/13/15 and its own text has blank lines at exactly those rows, i.e. an overlay, as expected.
   "Main"'s 14 lines are a dense two-column TOC with no blank rows at all, so reading its banner as
   an overlay drops a 66-cell-wide, ~9-row-tall image straight onto the TOC. That *is* bug 7.

**D2 — model (`Graphic.kt`).** `centered` moved off the `Graphic` interface onto `Graphic.Image`,
and `Graphic.Image.isLineImage` (`width == 1`) added, both documented with their spec citations.
The old comment's "confirmed empirically" overclaim is gone: the four image fixtures it named
contain no contradicting case, so they never confirmed the interface-wide reading, they just failed
to falsify it. New corpus test `NodeTest.stGuideDistinguishesItsLimageBannerFromItsPlainImageIcons`
pins the 1-vs-28 split; `limageHypPlacesLineHeightImages`/`imageHypPlacesThreeCenteredAndOffsetImages`
now assert `isLineImage` too.

**D2 — renderer (`HtmlSpans.renderGrid`).** A line image is now emitted as a **block between two
containers**, splitting the node's lines at its row, so the browser pushes the following text down
exactly as ST-Guide does; a plain `@image` and every vector graphic stay absolute overlays as
before. Splitting into per-segment containers is also what keeps the overlays right: each is
positioned `top:<row − segment start>em` inside its own segment, so a line image that displaces the
rows below it displaces their overlays too — with no font-metric arithmetic anywhere, which is why
this needs no px-per-cell constant. Centred now means centred **on the node's own text column**
(`left:calc(<N>ch / 2)`), not `left:50%` of a viewport whose width the `.hyp` format knows nothing
about. Tests: `renderGridCentersAGraphicWhenXIsZero` kept (retargeted to an image, which is what
centring applies to), plus `renderGridPlacesAVectorGraphicAtItsRawXEvenWhenThatXIsZero`,
`renderGridFlowsALineImageBetweenTheRowsItSplitsInsteadOfOverlayingThem` and
`renderGridOffsetsAnOverlayBelowALineImageIntoItsOwnSegment`; the two renderer-level
`x == 0`-on-a-`Line` tests were corrected to expect `left:0ch`.

**D3 — width cap.** `HtmlSpans.imageSizeStyle(node)` emits
`style="width:auto;height:auto;max-width:<N>ch"` on every `<img>` in `HtmlRenderer`/`EpubRenderer`,
where `N` is `HtmlSpans.textColumnWidth(node)` = the node's longest line. `height:auto` is needed
because the `height` attribute is a presentational hint that would otherwise pin the original pixel
height while the width shrinks, distorting the image. The cap is omitted entirely when the node has
no text (`N == 0`), which would otherwise collapse the image to nothing. It is computed from the
`Node` being rendered, so `--reflow` (which `Commands.kt` applies before renderer dispatch) widens
it automatically, with no reflow branch.

**Verification** (real corpus, not just unit tests): `./gradlew run -Pargs="dump
src/commonTest/resources/corpus/st-guide_orig_en.hyp --format html --out …"`, with and without
`--reflow`. "Main" now renders as `<div …><p>` (its one leading blank line) `</p></div>` +
`<div style="width:64ch;text-align:center"><img …></div>` + `<div …><p>` (the 13 TOC lines) — banner
above the TOC, centred on the 64-cell text column, no overlap, and the 15 vector decorations moved
from rows 1/5/8/10 to 0/4/7/9 inside the second segment, i.e. still on their own text rows. Exactly
one `text-align:center` line-image block exists in the whole document, as the corpus predicts. The
`--reflow` cap tracks the joined lines: "Symbol bar" 71ch → 440ch, "Load file" 71ch → 829ch,
"Info Dialogue" 72ch → 696ch, etc. ("Main" stays 64ch in both, correctly — a TOC table has no
reflowable paragraphs.)

`./gradlew clean build` green in both projects across all targets (`jvm`, `macosArm64`, `wasmWasi`),
including the real-binary `macosArm64SmokeTest`/`wasmWasiSmokeTest`.

Security review: no findings. The one new output-sink value is a CSS length interpolated into a
`style` attribute, and it is an `Int` character count (`textColumnWidth`, a `sumOf {
it.text.length }` over spans) plus `graphic.x`/`graphic.y` — never document text, so no new
injection surface in the HTML/XHTML sink. Existing escaping (`HtmlSpans.escapeHtml`) is unchanged
and still on every text path. The renderer restructure adds bounded index arithmetic only: segment
bounds come from `y.coerceIn(0, node.lines.size)` and rows are read via `until` over
`node.lines.indices`, so a hostile `y` cannot index out of range, and a line image at a clamped row
produces at worst an empty segment. `imageSizeStyle` guards its own `N == 0` case. The review did surface one non-security correctness
nit, fixed in the same round: `isLastSegment` was derived as `end == node.lines.size`, which is also
true of the segment *before* a line image clamped to the last row — so an overlay whose `y` is past
the final row was drawn twice. It is now an explicit parameter, covered by
`anOverlayClampedPastTheLastRowIsStillDrawnOnlyOnceAlongsideALineImageThere`.

## Group E done: popups render as popups, external refs as stubs (bugs 8 + 9, 2026-08-27, `160a87b`)

Per `doc/PLAN` "there are issues at cozy floyd" (Group E). Both bugs, combined per the plan because
they share one root cause.

**Symptoms.** (8) A `NodeKind.POPUP` node — which ST-Guide shows in a transient window over the
current page — was emitted as an ordinary full page section (`<h2>` in HTML, its own
`node-<N>.xhtml` in EPUB). (9) A link to a `TYPE_EXTERNAL_REF` target (22 of them in
`st-guide_orig_en.hyp`, e.g. `hcp.hyp/Main`, `reflink.hyp/Main`) rendered as a dead `#<N>` fragment
/ dead `node-<N>.xhtml` href, because nothing resolves a reference into another `.hyp` file.

**Root cause (shared).** `IndexEntry.TYPE_POPUP`, `Node.kind` and `ResolvedTarget.ToExternalRef`
have existed in the model since phase 13, but neither HTML-shaped renderer ever called
`HypDocument.resolve()`. `HtmlSpans.renderSpan`'s `linkHref: (NodeIndex) -> String` callback only
ever saw a raw index, so it could not branch on what that index actually resolves to.

**E1 — the shared walker learns about targets.** `linkHref` is replaced by

```kotlin
internal typealias LinkMarkup = (text: String, target: NodeIndex) -> String
internal val fragmentLink: LinkMarkup = { text, target -> "<a href=\"#${target.value}\">$text</a>" }

fun renderSpan(span: Span, linkMarkup: LinkMarkup = fragmentLink): String
fun renderGrid(node: Node, linkMarkup: LinkMarkup = fragmentLink, imageTag: (Graphic.Image) -> String?): String
```

i.e. the renderer now owns the *whole anchor*, not just its href — necessary because a popup or an
external ref has no page to link to at all, and the substitute markup differs per output format.
`text` arrives already escaped, so a renderer can never forget to escape it. The `HypDocument`
itself stays out of the signature: the renderers already have it and close over it, which keeps the
default (`renderGrid(node) { … }`, used by ~15 existing tests) working unchanged.

**E2 — `HtmlSpans.stubContent`/`isStubTarget`.** `stubContent(document, target, linkMarkup,
imageTag)` returns the content to inline in place of a link, or null for an ordinary node: a popup
node's own grid (via `renderGrid`, so it is escaped exactly once and never re-escaped by the
caller), or a description of an external ref / viewer action, put through `escapeHtml()` —
`ExternalRef.fileName`/`nodeName` and `IndexEntry.name` are decoded straight from untrusted `.hyp`
bytes. `isStubTarget` answers the same question *without rendering*, which is the recursion
firebreak: a renderer's `LinkMarkup` asking "is this a stub?" is in general the same `LinkMarkup`
`stubContent` would render with, and two popups linking to each other would otherwise recurse
forever.

**E3 — `HtmlRenderer`.** Popup nodes are skipped in the page loop and emitted once each as
`<dialog id="popup-N">…<form method="dialog"><button>Close</button></form></dialog>`, together with
one dialog per external-ref/viewer-action entry, driven off `document.entries.indices`. Links to
them become `<a href="#" onclick="document.getElementById('popup-N').showModal();return false;">`.
`N` is an `Int` node index, never document text, so it interpolates into the handler safely.

**E4 — `EpubRenderer`.** JS is unreliable across e-readers, so EPUB uses CSS-only disclosure:
`<details><summary>{link text}</summary>{stub}</details>` inlined at each link site. Popup nodes no
longer get a `node-<N>.xhtml` file, nav entry, manifest item or spine entry (new
`HypDocument.pages` = `nodes.filter { it.kind == NodeKind.TEXT }`); their images are still
manifested, since a popup's content is now inlined into the pages that link to it. Content links
*inside* a stub stay plain cross-file links rather than nesting another disclosure — the same
recursion firebreak as above.

**Scope note — one extra target class.** `ResolvedTarget.ToSystemAction` (one `SYSTEM` entry in the
fixture, `#79`/`node-79.xhtml`) had the exact same defect and was the sole surviving
`ebook-convert` warning after the bug-9 fix, so it gets the same stub treatment ("Viewer action —
not available in this document: …", escaped). Same mechanism, four lines, and it is what takes the
Calibre check to zero.

**E5 — TDD.** Red first: the new assertions failed against the old API/behaviour before the fix.
New cases in `HtmlSpansTest` (stub content for popup/external-ref/system/ordinary/out-of-range
targets, popup content escaped *exactly once* — no `&amp;amp;`, link text escaped before it reaches
the renderer's markup), `HtmlRendererTest` (dialog emitted, popup *not* also a `<h2>` section,
well-formed `onclick`, ordinary links still plain fragments) and `EpubRendererTest` (no page
file/nav/spine entry for a popup, `<details>` inlined, no `node-<N>.xhtml` href for an external
ref). Both renderers carry a deliberate XSS-shaped case: an entry named
`<script>alert(1)</script>&evil/x` must appear escaped and must not produce a `<script>` tag.

**Verification** (real corpus, not just unit tests): `./gradlew run -Pargs="dump
src/commonTest/resources/corpus/st-guide_orig_en.hyp --format html|epub --out …"`.

- HTML: page sections 63 → 59 (the 4 popup nodes are no longer pages), 27 `<dialog>` elements
  (4 popups + 22 external refs + 1 viewer action), every `onclick` target has a matching dialog,
  and **0 dead `#<N>` fragments** — down from 21. All 22 external refs render as stub text.
- EPUB: 49 `<details>` disclosures, no `node-<N>.xhtml` href without a file behind it, no `<script>`
  anywhere, and `content.opf`/`nav.xhtml` reference no missing file.
- Calibre `/opt/homebrew/bin/ebook-convert st.epub conv.epub`: **zero** "referenced file not found"
  warnings, down from 22 (see the closed-out entry above).

`./gradlew clean build` green across all `hypp-cli` targets (`jvm`, `macosArm64`, `wasmWasi`),
including the real-binary `macosArm64SmokeTest`/`wasmWasiSmokeTest` and the strict-XML
`EpubRendererWellFormednessTest` over the whole fixture.

**Known cosmetic caveat.** A `<details>` block lands inside the grid's `<p style="margin:0">`, which
is invalid per HTML's content model (`<p>` takes phrasing content only) though perfectly well-formed
XHTML — Calibre and the strict `DocumentBuilder` both accept it. Splitting the paragraph at each
disclosure, the way line images already split it, is the fix if a stricter validator (epubcheck)
ever gets added to the build; it was not worth the restructure for this round.

Markdown/AsciiDoc/Org still render popups as plain sections and external refs as dead fragments —
that is Group F, deliberately a separate round. ANSI drops link information entirely and is deferred
behind its own prerequisite refactor.

**Security review** (run over this round's diff only). No HIGH or MEDIUM findings.

The round's genuinely new output sink is the stub text, and the review confirmed `escapeHtml()`
covers it completely: every path into `<dialog>`/`<details>` content is either
`escapeHtml(...)` applied at the point of construction (external ref, viewer action) or
`renderGrid` output, which routes every span through `renderSpan` → `escapeHtml` already. The
XSS-shaped regression tests (`anExternalRefsNameIsEscapedBeforeItReachesTheOutput`,
`aSystemActionsNameIsEscapedBeforeItReachesTheOutput`, plus the per-renderer
`anExternalRefsNameIsEscapedInsideItsDialog`/`...InsideItsDisclosure`) are the guard, not manual
reading. `escapeHtml` deliberately does not escape `"`/`'`, which holds only because **no
document-derived text reaches an attribute value anywhere in this diff** — the sole interpolations
into `id="popup-N"` and the `onclick` handler are `NodeIndex.value`/`entries.indices`, i.e. `Int`.
That is the invariant to preserve if a future round ever wants a document string in an attribute
(a `title=`, say); it would need attribute-level escaping first. The `<a>` text handed to a
renderer's `LinkMarkup` is escaped by `renderSpan` before the callback sees it, so a renderer
cannot forget.

The review did surface one non-XSS robustness regression, fixed in the same round:
`HypDocument.resolve()` used `node(target)!!`/`image(target)!!`, which throws when a malformed file
carries an internal/popup/image entry whose data failed to decompress (`Diagnostic.
DecompressionFailed` — the entry stays in `entries` with no `Node`/`ImageNode` behind it). That NPE
was latent while only `inspect` called `resolve()`; this round put it on the html/epub `dump` path
for *every* link, so a corrupt input would have crashed the renderer instead of rendering. Fixed at
the root — both accessors now fall through to `ResolvedTarget.Missing`, one guard in the shared
dispatch rather than one at each call site — with `anEntryWhoseObjectFailedToParseIsMissingRather
ThanACrash` in `ResolvedTargetTest`. `./gradlew clean build` green in both projects.

## Group F done: popups + external refs in Markdown/AsciiDoc/Org (2026-08-27, `e2ef14f`)

Per `doc/PLAN` "there are issues at cozy floyd" (Group F, the plan's final group). Group E fixed
bugs 8 + 9 for the two HTML-shaped renderers; the three plain-text dialects had the identical defect
behind an identical cause, and this round closes it. **This completes the plan** — every group
A–F is done and pushed; ANSI is deliberately left filed as a follow-up (see the end of this entry).

**Symptoms.** All four popup nodes in `st-guide_orig_en.hyp` were emitted as ordinary `## heading` /
`== heading` / `** heading` sections, so a transient over-the-page note read as a page of its own;
and all 22 `TYPE_EXTERNAL_REF` targets (plus the one `SYSTEM` entry) rendered as `[label](#N)` /
`link:#N[label]` / `[[#N][label]]` fragments pointing at anchors that no section ever emits.

**Root cause (the same one as Group E).** `MarkupSyntax.link: (label: String, target: Int) -> String`
only ever saw a raw index, exactly as `HtmlSpans`'s pre-E `linkHref` did, so nothing could branch on
what the index resolves to; and `renderMarkup` gave every node a heading unconditionally.

### F1 — the shared walker resolves targets, and the dialect owns the substitute

`MarkupSyntax` gains two hooks alongside `link`, one per target class that has no section to jump to:

```kotlin
val link:  (label: String, target: Int) -> String      // an ordinary node
val popup: (label: String, content: String) -> String  // a NodeKind.POPUP node
val stub:  (label: String, description: String) -> String  // external ref / system action
```

`renderMarkup` resolves each `Span.link` via `HypDocument.resolve()` and dispatches to one of the
three; popup nodes are filtered out of the top-level section loop, matching `HtmlRenderer`.

**How this differs from Group E's `HtmlSpans` shape, and why.** E gave the renderer one
`LinkMarkup` callback plus a separate `isStubTarget()` firebreak and a `stubContent()` that returns
finished markup. Here the *walker* decides which of three hooks to call and the dialect supplies
only the markup, because:

- These three renderers are declarative `MarkupSyntax` values, not classes with a `render` body —
  there is nowhere for them to call a `stubContent()`-style helper from, and no `document` in scope.
  Pushing the decision into the walker is what keeps them declarative.
- HTML/EPUB need a two-phase answer (`isStubTarget` during the page walk, `stubContent` in a
  separate `<dialog>`/`<details>` emission pass) because their popup markup lives somewhere other
  than the link site. All three text dialects inline at the link site, so one phase suffices.
- Consequently the firebreak is different too: an `insidePopup: Boolean` threaded through the walk,
  rather than a render-free predicate. Inside a popup a further popup degrades to its plain label,
  which terminates on mutually-linked popups (`popupsThatLinkToEachOtherDoNotRecurse`).

The *data* is shared with Group E as the plan asked — the same `ResolvedTarget` variants, the same
`ExternalRef.fileName`/`nodeName` pair, the same `IndexEntry.name` — but none of its markup;
`stubContent`'s HTML wording stays in `HtmlSpans`, and `MarkupSyntax` has its own shorter
parenthetical phrasing (`external reference: hcp.hyp/Main`) that suits an inline aside.

**One deliberate widening, since it is the same sink class.** `escape` now also covers the heading
text and the link label, not just span text. Both are raw `.hyp` bytes and both previously reached
the output unescaped — a pre-existing hole in the exact place this round is otherwise hardening, and
one word to close in the shared walker rather than three times in three dialects.

### F2/F3/F4 — the three dialects

| | popup | external ref / system action |
|---|---|---|
| Markdown | GFM alert: `**Label**` + blank line + `> [!NOTE]` block-quote | `**Label** _(external reference: hcp.hyp/Main)_` |
| AsciiDoc | `[NOTE]` attribute + `====` example block | `*Label* _(…)_` |
| Org | `#+BEGIN_QUOTE` … `#+END_QUOTE` | `*Label* /(…)/` |

No `#<n>`-equivalent fragment is emitted for a stub at all, since it would be dead by construction.

**Org: quote block, not `[fn:N]` footnote.** The plan offered either. A footnote needs a definition
parked at the end of the document plus a unique-`N` allocator — document-level state this stateless
per-span walk does not have, and it would have forced F1's `popup` hook to return two things (an
inline marker and a deferred definition) purely for Org's benefit. It would also put Org alone in
producing an out-of-line result. `#+BEGIN_QUOTE` needs neither, and matches where the other two
dialects put the content.

### F5 — TDD

Red first in every case. Dialect-agnostic walker behaviour in `MarkupSyntaxTest` (commonTest, so it
runs on all three targets): popup inlined and given no section, mutual-popup firebreak, external ref
and system action stubbed rather than linked, ordinary node still an ordinary link, and
`headingsLabelsAndStubDescriptionsAllGoThroughEscape`. Per-dialect assertions in the three
`*RendererTest` files off a shared `StubTargetFixture` (jvmTest) holding one of each target class —
the corpus has no entry name carrying dialect metacharacters, so the fixture parameterises
`refName`/`popupText` and that is how each renderer's escaping is exercised against its own
`escape`, never HTML's. Each also asserts the *old* behaviour is gone (`## Pop`, `link:#1[`,
`[[#1]`), which is what makes them regression tests rather than snapshots.

### Verification (real corpus, not just unit tests)

`./gradlew run -Pargs="dump src/commonTest/resources/corpus/st-guide_orig_en.hyp --format
markdown|asciidoc|org [--reflow] --out …"`, six renders, checked by script:

- **Headings 63 → 59** in all three formats — exactly the 59 `INTERNAL` entries; the 4 `POPUP`
  entries no longer get a section (`inspect` reports 59 INTERNAL / 4 POPUP / 22 EXTERNAL_REF /
  15 IMAGE / 1 SYSTEM = 101).
- **0 dead fragments** in all three formats, both with and without `--reflow`: every `](#N)` /
  `link:#N[` / `[[#N]` has a matching `<a id="N">` / `[#N]` / `:CUSTOM_ID: N`. All 52 surviving
  links resolve.
- 10 admonition/quote blocks (the 4 popups are linked from 10 sites), 39 external-ref stubs (22
  entries, 39 link sites), 1 viewer-action stub — identical counts across all six renders.
- Spot-checked output reads correctly, e.g. the `case insensitive` popup now appears as a `> [!NOTE]`
  quote inline in the search-function page rather than as a stray `## case insensitive` section.

`./gradlew clean build` green across all `hypp-cli` targets (`jvm`, `macosArm64`, `wasmWasi`).

### Security review (this round's diff only)

The stub text is again a real output sink — untrusted `.hyp` bytes into Markdown/AsciiDoc/Org — and
HTML escaping does *not* carry over, so each dialect's own `escape` was checked against its own
syntax rather than assumed. Two issues found, both fixed in-round:

1. **AsciiDoc: raw-HTML injection via `pass:[…]` (MEDIUM, fixed).** `escape` covered `*_#`, backtick
   and `+` but not brackets, so an entry named `pass:[<script>alert(1)</script>]` reached the
   `.adoc` intact and asciidoctor would render it as live markup on HTML conversion — AsciiDoc's
   one construct that promotes document text to markup. `[`/`]` are now escaped (`\[` keeps the
   bracket literal, so no macro or attribute list can form), which also stops a `]` in a link label
   closing `link:#N[…]` early. Guarded by
   `aRefNameCannotSmuggleRawHtmlThroughAsciiDocsPassthroughMacro`.
2. **Org: escaping only the leading character was no protection here (fixed).** The old rule
   backslashed a marker only at position 0, and a stub description *always* starts with `external
   reference: ` / `viewer action, …`, so a `*evil*` in a filename would never have been escaped at
   all. Org has no general escape character; emphasis only *opens* at a line start or after
   whitespace/opening punctuation, so `escape` now backslashes a marker in those positions and
   leaves inert closing markers alone. `ponytail:` comment records the remaining approximation
   (start-or-after-space vs. `org-emphasis-regexp`'s full pre-char set).

Markdown's `escape` was already correct for its own syntax (`\ ` backtick `* _ [ ] < >`; escaping
`<` is what keeps raw HTML out) and needed no change. No HIGH findings. The `#<N>` interpolations
are `Int` node indices throughout, never document text.

**Acknowledged, not fixed (pre-existing, outside this diff):** Org's `#+BEGIN_EXPORT html` block is
raw-HTML injection if a `.hyp` line *starts* with it. This round's two new sinks cannot reach it —
a heading is prefixed `** `, a stub description is prefixed by its own wording — so only ordinary
span text is exposed, which predates Group F. Fixing it needs line-position awareness that the
per-span `escape` hook does not have by design; filed for a future round.

### ANSI: still deferred, recommended as its own follow-up

`AnsiRenderer.kt`/`AnsiStyle.kt` were deliberately untouched, per the plan. `AnsiStyle.styledLines`
drops `Span.link` **entirely** today — links are not even styled differently, let alone pointed
anywhere — so there is nothing for a popup/external-ref fix to attach to. Recommend filing
**"ANSI renderer drops link information"** as its own round: it is a prerequisite refactor of
`styledLines`'s per-segment model, independent of and prior to any ANSI popup/external-ref work, and
was out of proportion to this one. The `> [!NOTE]`-equivalent for ANSI would then be an indented,
dim-styled block; the stub, plain text plus a parenthetical exactly as here.
