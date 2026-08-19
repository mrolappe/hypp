# Phase 15 — Six renderers

**Status: green.**

## What was built

All in `hypp-cli/src/commonMain/kotlin/de/rholambdapi/hypp/cli/render/` unless noted; the
`renderers` registry in `Renderer.kt` is still `emptyMap()` — wiring it up is Phase 16's job, not
this one's.

- **`HtmlSpans.kt`** — `object HtmlSpans { renderSpan(Span); escapeHtml(String) }`, ported from the
  reference `hyp2html` in the root `hypp` module's `commonTest`. Nesting is `<u><i><b>text</b></i></u>`
  (bold innermost) — the port surfaced that the brief's assumed "bold outermost" was wrong; the test
  was corrected to match the real reference rather than the other way round.
- **`ImageEncoder.kt`** (interface) + **`StoredPngEncoder.kt`** — a real, spec-valid PNG encoder
  using only RFC-1951 stored (uncompressed) deflate blocks, hand-rolled CRC-32/Adler-32, no
  platform zip/deflate dependency (works in `commonMain` on every future target). Round-tripped
  through `javax.imageio.ImageIO.read` as the decode oracle in `jvmTest`.
- **`HtmlRenderer.kt`** — `class HtmlRenderer(imageEncoder: ImageEncoder = StoredPngEncoder) : Renderer`,
  same node/image/paragraph structure as the reference `hyp2html`, `data:image/png;base64,...` URIs
  instead of the reference's BMP.
- **`MarkupSyntax.kt`** — shared walker: `data class MarkupSyntax(boldOpen/Close, italicOpen/Close,
  underlineOpen/Close, link: (label, target) -> String, heading: (level, text) -> String, escape:
  (text) -> String)` + `fun renderMarkup(document, syntax): String`. Bold outermost / underline
  innermost (the mirror image of `HtmlSpans`'s nesting — each renderer's own convention, not a
  shared invariant). Link spans bypass `escape` and style-wrapping entirely; the `link` lambda owns
  escaping its own label.
- **`MarkdownRenderer.kt` / `AsciiDocRenderer.kt` / `OrgRenderer.kt`** — each just a `MarkupSyntax`
  value over `renderMarkup` (CommonMark `**`/`*`/raw-`<u>`, AsciiDoc `*`/`_`/`[.underline]#…#`, Org
  `*`/`/`/`_`). Org's `escape` only backslash-escapes a *leading* emphasis character rather than
  doing full word-boundary detection — a deliberate, noted simplification (Org's boundary rule is
  genuinely fiddly; not worth it for this phase).
- **`AnsiStyle.kt`** (`sgrFor(TextStyle)`, 8-colour SGR by nearest ANSI name, `DARK_*` sharing its
  non-dark counterpart's base digit) + `StyledSegment`/`StyledLine`/`styledLines(Node)` (a reusable
  structured intermediate, not yet flattened) + **`AnsiRenderer.kt`** (the flattening `Renderer`).
- **`EpubRenderer.kt`** — `class EpubRenderer(imageEncoder: ImageEncoder = StoredPngEncoder) : ArchiveRenderer`
  producing `mimetype`, `META-INF/container.xml`, one `OEBPS/node-N.xhtml` per node (via
  `HtmlSpans`), `OEBPS/nav.xhtml`, `OEBPS/content.opf` — no graphics embedded yet (`imageEncoder` is
  an unused placeholder for that future extension, noted explicitly rather than silently dropped).
  No ZIP packaging here — that's `Zip.kt`'s job.
- **`Zip.kt`** (`jvmMain` only) — `fun zip(files: List<RenderedFile>): ByteArray` via
  `java.util.zip.ZipOutputStream`, round-tripped through `ZipInputStream` in its test.

Micro-corpus fixtures vendored into `hypp-cli/src/commonTest/resources/corpus/` (duplicated from
the root `hypp` module's, per the plan's own note that Gradle doesn't share `commonTest` resources
across modules), with a small `Corpus.open(name)` test helper (`jvmTest`) parsing them via
`HypDocument.open`.

## Execution mode

Delegated to sub-agents in two dependency-ordered waves, each agent working in an isolated
`git worktree` (`Agent(..., isolation: "worktree")`) rather than the shared checkout, so parallel
agents editing disjoint files couldn't stomp each other's staging area or Gradle build output.
Agents committed locally but did not push; the coordinating session merged each worktree branch
into `main`, ran the full build once per wave, then pushed.

- **Wave 1** (steps a/a′/e/g independent of each other, step c independent of all of them):
  Sonnet, one agent for `HtmlSpans` + `StoredPngEncoder` + `AnsiStyle`/`AnsiRenderer` + `Zip`
  (four small, disjoint, same-tier pieces bundled into one agent rather than four, since they don't
  interact); Opus, a separate agent for the `MarkupSyntax` shared walker (higher design-judgment
  step, kept solo). Both landed clean, merged with no conflicts.
- **Wave 2** (steps b/f need a+a′ from wave 1; step d needs c from wave 1): Sonnet for
  `HtmlRenderer` + `EpubRenderer`; Haiku for the three markup-dialect renderers (mechanical given
  `MarkupSyntax` already existed). The Sonnet agent hit a session-limit interruption mid-task, after
  committing `HtmlRenderer` but before committing the already-written, already-correct
  `EpubRenderer`/its test — recovered by inspecting the worktree directly (files present and
  correct on disk, build green) and committing on its behalf rather than re-running the whole task.

## Fix applied after merge

A background security scan of the pushed wave-1 commit flagged `AnsiRenderer`: it wrote
`node.name` and span text straight into ANSI-escaped terminal output with no sanitization. Since
`.hyp` files are untrusted input a user points the CLI at (Phase 16's `dump --format ansi`), a
crafted node name or span text containing a raw `ESC` byte could inject arbitrary terminal escape
sequences into the victim's terminal. Fixed directly (not re-delegated — a two-line, low-risk
pinpoint fix): a `sanitize()` filtering C0 control bytes (`0x00`–`0x1F`) and DEL (`0x7F`) out of
document-sourced text, applied only at the final terminal-output flattening step in
`AnsiRenderer.render` — deliberately *not* in `styledLines`, which stays a faithful, unsanitized
structured intermediate for a hypothetical future TUI consumer that might handle raw text safely
through its own rendering layer. Committed as `d394343`, verified green, pushed before wave 2 was
dispatched.

## Verification

`cd hypp-cli && ./gradlew clean build jvmTest` → **BUILD SUCCESSFUL**, all renderer tests green,
run once after each wave's merge and once more after the final merge.

## Remaining / deferred

- Phase 16: wire the `renderers` registry, `ArgParser.kt`/`Commands.kt`, `Main.kt`, JVM fat-jar
  Round A.
- `EpubRenderer` doesn't embed images yet — flagged above, not blocking Phase 16 (its `dump`
  command's `--format epub` path doesn't require images to work).
- Phases 17–19: GraalVM native-image, `wasmWasi`, `macosArm64` — no renderer changes expected, per
  the plan's design.
