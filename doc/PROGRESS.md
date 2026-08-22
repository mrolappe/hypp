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
