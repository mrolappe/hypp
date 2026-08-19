# Phase 16 — CLI commands + Round A (JVM fat jar)

**Status: green.**

## What was built

Two agents in parallel isolated `git worktree`s (same pattern as Phase 15) — `ArgParser.kt` has
no dependency on `Commands.kt`/`Main.kt` beyond a contract fixed up front, so both landed
independently and merged with zero conflicts.

- **`ArgParser.kt`** (`commonMain`, Haiku) — hand-rolled parser, no dependency, for the fixed
  4-command surface: `sealed interface Command` (`Dump(file, format, out)`, `Validate(file,
  strict)`, `Inspect(file)`, `ExtractImages(file, out)`), `class ArgParseException`, `fun
  parseArgs(args: Array<String>): Command`. Validates `--format` against the six known renderer
  names, applies each command's defaults (`format="html"`, `strict=false`, extract-images
  `out="."`), rejects unknown commands/flags/missing values. 37 tests in `ArgParserTest.kt`.
- **`Renderer.kt`** registry wiring (Sonnet) — replaced Phase 14's placeholder `val renderers =
  emptyMap()` with **factory functions**, `defaultRenderers(imageEncoder): Map<String, Renderer>`
  and `defaultArchiveRenderers(imageEncoder): Map<String, ArchiveRenderer>`, per decision 10
  (`doc/PLAN-12-19.md`): the `ImageEncoder` is the JVM composition root's choice, not
  `commonMain`'s, so the registry can't be a single fixed instance.
- **`ImageIoPngEncoder.kt`** (`jvmMain`, new) — `object ImageIoPngEncoder : ImageEncoder` using
  `javax.imageio.ImageIO` for real deflate compression, vs. `StoredPngEncoder`'s uncompressed
  stored blocks. `Main.kt` wires this in as the JVM default instead of `StoredPngEncoder`.
- **`Commands.kt`** (`commonMain`, new) — `dump`/`validate`/`inspect`/`extractImages` as plain
  functions over an already-`open()`ed `HypDocument`, no direct file I/O (a `CommandResult`/
  `OutputFile` result type carries `exitCode`/`stdout`/`stderr`/`files` back to the caller), kept
  commonMain-testable per the plan's verification note ("per-command tests ... against
  `Commands.kt` directly"). `dump` resolves against both registries, injecting `zip: (List
  <RenderedFile>) -> ByteArray` for the epub case since `Zip.kt` is jvmMain-only; `--format epub`
  without `--out` is a usage error, not a crash. `validate` classifies diagnostics per **decision
  15** ("CLI-local policy, not pushed into `commonMain`'s neutral model") — `UnsupportedCharset`/
  `DecompressionFailed`/`NodeDataOverrun` are **hard** (lost/corrupted content), everything else
  informational; exit 1 without `--strict` only on a hard diagnostic, exit 1 with `--strict` on
  any diagnostic. `inspect` prints header/extended-headers/TOC/entry-counts-by-type/image-count
  plus a `document.resolve()` (Phase 13) breakdown by `ResolvedTarget` kind, including a list of
  unresolved `ExternalRef`s (no `.REF` file loaded here, per the plan — demonstrates the model,
  not full cross-doc resolution).
- **`Main.kt`** (`jvmMain`, new) — the composition root: `parseArgs` → catch `ArgParseException`
  (exit 2) → `readBytes` → `HypDocument.open` → catch `OpenOutcome.Failure` (exit 1) → dispatch to
  `Commands.kt` with `ImageIoPngEncoder`/`::zip` injected → print stdout/stderr → `writeBytes` any
  output files → `exitProcess(result.exitCode)`.
- **`hypp-cli/build.gradle.kts`** — per the Phase 14 deviation (no `application`/`java` plugin,
  KMP hard-fails alongside it): `fatJar` now has a fixed `hypp-cli-all.jar` name and a `Main-Class:
  de.rholambdapi.hypp.cli.MainKt` manifest entry (Kotlin's synthetic class name for a top-level
  `fun main()`), plus a hand-rolled `run` `JavaExec` task (`./gradlew run -Pargs="dump <file>"`).
  `jvmTest` now `dependsOn(fatJar)` so the new fat-jar smoke test always runs against a freshly
  built jar as part of normal `build`/`check`, not as an opt-in task like `corpusSweep`.

## Security-relevant sink, addressed

`extract-images <file> [--out dir]` writes `<name>.png` under `--out`, where `name` comes from
`ImageNode.name` — attacker-controlled `.hyp` file content (the index-entry name), not operator
input. This is a path-traversal / absolute-path-escape sink once joined with the output directory.
Flagged explicitly in the delegation brief (per the 2026-08-19 workflow change — threat-model
output sinks at planning time, not just via a post-hoc scan) and mitigated by
`sanitizeImageFileName` (`Commands.kt`): splits the document-derived name on both `/` and `\`,
keeps only the last non-empty segment, and falls back to a synthetic `image-<index>` name if that
segment is empty, `.`, or `..`. `--out` itself is deliberately left unsanitized — it's trusted
operator-supplied CLI input, not document content, same trust boundary as `<file>`.

Covered by `CommandsTest.kt` (traversal, absolute-path, and empty-after-sanitize cases) and
confirmed manually against the real `st-guide_orig_en.hyp` corpus fixture: all 15 images extract
as valid PNGs (`file` confirms real PNG headers), all under synthetic `image-<index>.png` names —
this fixture's real image entries have index-table names that don't survive sanitization as
literal filenames, so every one legitimately falls back, not a test artifact.

A dedicated `security-review` pass over the round's full diff (Phase 16a + 16b merged) found no
other findings — no other new sink; `--out` for `dump`'s epub archive is likewise trusted operator
input, correctly left untouched.

## Verification

- `~/studio/hypp`: `./gradlew build` — **BUILD SUCCESSFUL**, all existing suites (jvm/wasmJs/
  wasmWasi tests, `wasmJsFacadeSmokeTest`) still green, untouched by this phase.
- `hypp-cli/`: `./gradlew build fatJar` — **BUILD SUCCESSFUL**, including the new
  `FatJarSmokeTest` (`ProcessBuilder`, runs `java -jar hypp-cli-all.jar` out-of-process end to
  end) as part of normal `jvmTest`.
- Manual end-to-end run of the built fat jar against real corpus fixtures for all four commands:
  `dump --format html`/`ansi` (correct rendered output), `validate` (`no diagnostics`, exit 0),
  `inspect` (header/TOC/entries/link-resolution summary), `extract-images` (15 real PNGs from
  `st-guide_orig_en.hyp`, `file` confirms valid PNG headers), `dump --format epub --out <path>`
  (produces a non-empty `.epub`), and a deliberate bad-args case (`--format bogus` → exit 2, usage
  message on stderr).

## Remaining / deferred

- Phases 17–19: GraalVM native-image, `wasmWasi`, `macosArm64` build-target rounds — additive per
  the plan, no renderer/`Commands.kt` changes expected (`--format epub` absent from the non-JVM
  targets' registries per decision 6).
