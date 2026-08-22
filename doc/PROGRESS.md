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
