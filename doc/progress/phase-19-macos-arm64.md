# Phase 19 — CLI Round D (`macosArm64`)

**Status: green — Xcode.app installed and licensed 2026-08-21, real binary linked, run against
corpus fixtures, and covered by an automated `macosArm64SmokeTest` wired into `check`. See
"Finished" section at the end for the closing writeup; everything above it is the original
code-complete-but-blocked session, kept for the record.**

## What was built

- **Root `build.gradle.kts`** — added `macosArm64()` to the `kotlin {}` block. Not called out
  explicitly by the plan text (which only lists `hypp-cli/build.gradle.kts` and
  `hypp-cli/src/macosArm64Main/...` as touched files), but required: `hypp-cli`'s new
  `macosArm64Main` source set needs `hypp`'s API compiled for `macosArm64` to link against, the
  same reason `jvmMain`/`wasmWasiMain` each need the corresponding root target. Confirmed by
  running `./gradlew :publishToMavenLocal` and inspecting `~/.m2/repository/de/rholambdapi`:
  the target publishes as **`de.rholambdapi:hypp-macosarm64`** — note no hyphen before `arm64`,
  unlike `wasmWasi`'s kebab-cased `hypp-wasm-wasi`. Verified empirically, not assumed, same
  discipline as phase 18's wasmWasi coordinate check.
- **`hypp-cli/build.gradle.kts`** — `macosArm64 { binaries.executable { entryPoint =
  "de.rholambdapi.hypp.cli.main" } }`, plus a `macosArm64Main.dependencies { implementation(
  "de.rholambdapi:hypp-macosarm64:0.1.0-SNAPSHOT") }` block, mirroring `jvmMain`/`wasmWasiMain`.
  The explicit `entryPoint` was required (see "Deviations" below).
- **`hypp-cli/settings.gradle.kts`** — added the matching `dependencySubstitution` entry for
  `de.rholambdapi:hypp-macosarm64` → `project(":")`.
- **`hypp-cli/src/macosArm64Main/kotlin/de/rholambdapi/hypp/cli/Io.native.kt`** (new) —
  `actual readBytes`/`writeBytes` via `platform.posix` (`fopen`/`fseek`/`ftell`/`fread`/`fwrite`/
  `fclose`), `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` at file level (required —
  see below). Also a small `printError`/`printErrorLine` pair using `platform.posix.fputs` against
  `platform.posix.stderr`, since `java.lang.System` doesn't exist on Kotlin/Native (`print`/
  `println` only cover stdout).
- **`hypp-cli/src/macosArm64Main/kotlin/de/rholambdapi/hypp/cli/Main.kt`** (new) — composition
  root, same shape as `jvmMain`'s: real `args: Array<String>` (see finding below), `StoredPngEncoder`
  (no `ImageIoPngEncoder`, `javax.imageio` is JVM-only), `archiveRenderers = emptyMap()` and a
  `zip = { error("epub not supported on macosArm64") }` stub (plan decision 6), `kotlin.system.
  exitProcess` for exit codes.

## What was verified empirically (not assumed)

- **Kotlin/Native stdlib has no `java.io`-style file API for this Kotlin version (2.4.10)** —
  confirmed by attempting the compile with `platform.posix` interop; it needed only one opt-in
  annotation (`ExperimentalForeignApi`) to work, and no simpler stdlib alternative surfaced in the
  compiler's declaration search. `platform.posix` is the correct, current answer.
- **`fun main(args: Array<String>)` receives real argv on `macosArm64`, unlike wasmWasi** — this
  target's `Main.kt` calls `parseArgs(args)` directly with no `wasiCliArgs()`-style workaround,
  matching the normal Kotlin/Native contract (this rests on Kotlin/Native's documented `main`
  contract; the actual argv-passing could not be exercised end-to-end in this environment because
  linking never completed — see "Blocked" below. If it turns out empirically wrong once a binary
  runs, this is the one thing to re-check first).
- **`kotlin.system.exitProcess` is available in `macosArm64Main`** — compiled clean, no opt-in
  needed, same call as `jvmMain`.
- **A new opt-in was required that Phase 18 didn't need**: `binaries.executable()` on a
  `macosArm64` target with `main()` inside a non-root package fails to link with `Could not find
  '/main' function` unless `entryPoint` is set explicitly in `build.gradle.kts`. JVM's `main`
  doesn't have this constraint (any top-level `fun main` works via its synthetic `MainKt` class);
  Kotlin/Native's native linker looks for a root-package `main` by default. Fixed by adding
  `entryPoint = "de.rholambdapi.hypp.cli.main"` to the `binaries.executable {}` block.

## Blocked: linking requires a full Xcode.app install, not just Command Line Tools

`./gradlew hypp-cli:linkReleaseExecutableMacosArm64` (and `linkDebugExecutableMacosArm64`, which
`./gradlew hypp-cli:build` pulls in by default once `macosArm64 { binaries.executable() }` exists)
fails on this machine:

```
e: An error occurred during an xcrun execution. Make sure that Xcode and its command line tools are properly installed.
Failed command: /usr/bin/xcrun xcodebuild -version
```

Investigated rather than assumed a fix exists:

- `xcode-select -p` → `/Library/Developer/CommandLineTools` — only the CLT package is installed;
  no `Xcode.app` exists anywhere on this machine (`/Applications` has no Xcode entry;
  `pkgutil --pkgs` shows a stale `com.apple.pkg.Xcode` receipt from a since-removed install; the
  files it claims to own, e.g. `/Applications/Xcode.app`, don't exist).
- Decompiled the relevant class from `kotlin-native-compiler-embeddable.jar`
  (`AppleConfigurablesImpl$xcodePartsProvider$2`, `Apple.kt`): unless JetBrains'
  internal-only `InternalServer.isAvailable()` returns true (it won't, outside JetBrains' own CI),
  the compiler unconditionally calls `Xcode.Companion.findCurrent()`, which shells out to
  `xcrun xcodebuild -version`. The `ignoreXcodeVersionCheck` konan.properties flag only skips the
  *minimum-version* comparison after Xcode is found — it does not skip the requirement that Xcode
  be found at all. There is no property or environment variable that bypasses this for a
  Command-Line-Tools-only install; full Xcode.app is a hard requirement of Kotlin/Native's Apple
  targets, confirmed from the compiler's own bytecode rather than assumed from prior versions.
- Did not attempt to install full Xcode (multi-GB, requires an interactive Apple ID / App Store
  sign-in) — that's a call for whoever owns this machine, not something to do unattended mid-task.

**Consequence beyond the one target task**: because `macosArm64 { binaries.executable() }` is a
first-class KMP target (plan decision 4 — same footing as `jvm()`/`wasmWasi()`, not an opt-in
`Exec` task like Phase 17's `nativeImageCli`), the Kotlin Gradle plugin wires its link tasks into
the default `assemble`/`build`/`check` lifecycle automatically. That means, on this machine right
now, **`./gradlew hypp-cli:build` also fails** (at `linkDebugExecutableMacosArm64`), not just the
explicit release-link task — even though `jvm`/`wasmWasi` compilation and their own tests still
succeed up to that point. `compileKotlinMacosArm64` itself (compiling this phase's Kotlin source,
no linking) succeeds cleanly on its own, confirming the code is correct; only the native-linking
step needs Xcode.

This matches the plan's own verification note ("Phases 17–19: manual local run of the produced
artifact ... each explicitly flagged as needing local tooling ... not asserted by CI here") for
the *run-the-binary* step, but goes further than Phases 17/18 in also blocking the base `build`
task, because unlike GraalVM's `nativeImageCli` (opt-in, never wired into `build`/`check`),
`macosArm64` is meant to be a normal target, per decision 4/13.

## Security note

Same sink as `jvmMain`/`wasmWasiMain`: `extract-images` and `dump --out` write files via
`writeBytes(path, bytes)`, `path` coming from `RenderedFile.path`, already sanitized upstream by
`Commands.kt`'s `sanitizeImageFileName` (untouched by this phase). `Io.native.kt`'s new
`actual writeBytes`/`readBytes` are a structurally identical read/write-a-file-at-a-given-path
operation to the other two targets — `fopen`/`fread`/`fwrite`/`fclose` on the same string, no
shell invocation, no format-string risk (`path` is passed as the `%s`-style filename argument to
`fopen`, never interpolated into a format string), and the read/write loops are bounded by the
actual file size (`ftell`) or the caller-supplied `ByteArray.size` — no unbounded copy from
attacker-controlled `.hyp` content. `security-review` skill run over the full round's diff; see
"Security review" below.

## Security review

Ran the `security-review` skill over the diff (`Io.native.kt`, `Main.kt`, the three
`build.gradle.kts`/`settings.gradle.kts` edits). No findings:

- `readBytes(path)`'s `path` is `command.file`, a CLI argument — trusted input per this review's
  own precedent (CLI flags/arguments are not attacker-controlled in this threat model), same as
  the identical `readBytes(file)` call already shipped in `jvmMain`/`wasmWasiMain`'s `Main.kt`.
- `writeBytes(path, bytes)`'s `path` is `RenderedFile.path`, built by `Commands.kt`'s
  `sanitizeImageFileName` (unchanged by this phase) before this code ever sees it — same
  upstream sanitizer already relied on by the other two targets.
- `fopen(path, "rb"/"wb")` — the mode string is a fixed literal, never derived from input; no
  format-string or argument-injection surface (`fopen` takes a filename string directly, not a
  shell command).
- No new process/shell invocation, no deserialization, no new network surface. The three
  `build.gradle.kts`/`settings.gradle.kts` edits are declarative Gradle config (a target
  declaration, a dependency coordinate, a substitution rule) with no dynamic/attacker-influenced
  input.
- `ftell`'s `Long` result is cast to `Int` for `ByteArray(size.toInt())`; a file larger than
  `Int.MAX_VALUE` would misbehave, but that's a resource/DoS concern (out of scope per this
  review's exclusions), not a memory-safety issue — Kotlin/Native's `ByteArray` allocation is
  bounds-checked, a negative/huge size throws rather than corrupting memory.

Given the diff's small size (~70 lines across two new files plus 10 lines of Gradle config) and
that it's structurally identical to already-reviewed sinks in `jvmMain`/`wasmWasiMain`, this was
done as a direct, single-pass review rather than the skill's full multi-agent pipeline — the
pipeline's phased identify/filter workflow is proportioned for larger or more novel diffs.

## Verification performed

- `./gradlew :publishToMavenLocal` (root) — succeeded, confirmed the `hypp-macosarm64` coordinate.
- `./gradlew hypp-cli:compileKotlinMacosArm64` — **succeeded**, confirms the new Kotlin source is
  correct.
- `./gradlew hypp-cli:linkReleaseExecutableMacosArm64` — **failed**, blocked on missing Xcode.app
  (see above), not a code defect.
- `./gradlew hypp-cli:build` — **failed** at `linkDebugExecutableMacosArm64`, same root cause.
- Running the produced binary against a real corpus fixture (`dump`, `extract-images`) — **not
  done**, since no binary was produced.

## Made opt-in (2026-08-19, follow-up round)

Given the choice between installing full Xcode.app or making the link tasks opt-in, the user
chose **opt-in** — restore a green default build now rather than gate progress on a multi-GB,
Apple-ID-gated install. `hypp-cli/build.gradle.kts` now has:

```kotlin
tasks.withType<KotlinNativeLink>().configureEach {
    if (name.contains("MacosArm64")) {
        onlyIf("only runs when requested directly (needs full Xcode.app to link)") {
            gradle.startParameter.taskNames.any { requested -> path.endsWith(requested) || name == requested }
        }
    }
}
```

Unlike Phase 17's `nativeImageCli` (a hand-rolled `Exec` task never wired into `build`/`check` in
the first place), `macosArm64`'s link tasks are generated by the Kotlin Gradle plugin itself and
auto-wired into `assemble`/`build`/`check` — there's no plugin option to opt a target's binaries
out of the default lifecycle, so the guard is a task-level `onlyIf` keyed on whether the task was
named directly in the Gradle invocation, rather than a task that simply doesn't exist until asked
for. Verified both directions:

- `./gradlew hypp-cli:build` — link tasks show `SKIPPED`, **BUILD SUCCESSFUL**.
- `./gradlew hypp-cli:linkReleaseExecutableMacosArm64` (direct) — still runs, still fails on the
  same pre-existing Xcode gap (`xcrun xcodebuild -version` error) — confirms the guard only
  suppresses the *default-lifecycle* path, not direct invocation, matching the plan's "Done"
  criterion once Xcode is available.

This is a deviation from plan decision 4/13's framing of `macosArm64` as a first-class target on
the same footing as `jvm()`/`wasmWasi()` — it's now opt-in like GraalVM's native-image round. If
Xcode is later installed on this machine (or CI gains a macOS runner with full Xcode), the guard
can be deleted to restore first-class status with no other code changes needed.

## Remaining / deferred (as of the original 2026-08-19 session — see "Finished" below)

- Install full Xcode.app on this machine (owner decision — multi-GB download, Apple ID sign-in),
  then re-run `linkReleaseExecutableMacosArm64` and exercise the binary against
  `src/commonTest/resources/corpus/st-guide_orig_en.hyp` (dump) and a fixture with images
  (extract-images), confirming exit codes and output the way Phases 16/18's smoke tests do.
- Once a binary can be produced, worth adding a `macosArm64SmokeTest` `Exec` task (same idiom as
  `wasmWasiSmokeTest`) running the release executable directly against a corpus fixture and wiring
  it into `check`, per the task's guidance to prefer a real automated check over "I ran it once by
  hand" — not done yet since there's nothing to run it against.
- `linuxX64`/`mingwX64` and any CI cross-compilation matrix remain out of scope (plan decision 13).

## Finished (2026-08-21)

User installed Xcode.app and reported it done. Verification and remaining follow-up:

- **License gate, found and cleared.** `xcrun -f ld` initially failed with "You have not agreed to
  the Xcode license agreements" even with `Xcode.app` present — a separate gate from the
  Command-Line-Tools-only blocker this phase originally hit. Not something this session could clear
  (`sudo xcodebuild -license` needs an interactive terminal/password, refused via a non-interactive
  `Bash` call with a clear error rather than attempting a workaround). The user ran it directly in
  their own terminal; `xcrun -f ld` then resolved to
  `/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/ld`.
- **Guard removed.** Deleted the `tasks.withType<KotlinNativeLink>().configureEach { onlyIf { ... } }`
  block and its now-unused `KotlinNativeLink` import from `hypp-cli/build.gradle.kts` — restores
  `macosArm64` to a first-class target on the default `build`/`check` lifecycle, per the doc's own
  note that this was the intended outcome once Xcode became available.
- **Real link succeeded.** `./gradlew hypp-cli:build` now runs `linkDebugExecutableMacosArm64` /
  `linkReleaseExecutableMacosArm64` / `linkDebugTestMacosArm64` / `macosArm64Test` for real (no
  longer `SKIPPED`) and reports `BUILD SUCCESSFUL`. `./gradlew clean build` from scratch also green
  (54 tasks, 21s).
- **Binary run directly against real corpus fixtures**, confirming the two empirical claims this
  doc flagged as unverified pending a working link:
  - `dump src/commonTest/resources/corpus/st-guide_orig_en.hyp --format html` → well-formed HTML
    (`<!doctype html>` present) with embedded base64-encoded PNG image data — confirms `fun
    main(args: Array<String>)` does receive real argv on this target, matching the doc's earlier
    "rests on Kotlin/Native's documented contract, not yet exercised" note.
  - `extract-images src/commonTest/resources/corpus/st-guide_orig_en.hyp --out <dir>` → 15 files,
    each confirmed a valid PNG via `file` — matches the count already verified for the JVM target in
    Phase 16 and the wasmWasi target in Phase 18 on the same fixture (all falling back to the
    synthetic `image-<index>.png` name, since this fixture's real index-entry names don't survive
    `sanitizeImageFileName`).
- **`macosArm64SmokeTest` added**, closing the "Remaining" item above rather than leaving it
  deferred again: a Gradle `Exec` task in `hypp-cli/build.gradle.kts` that runs the just-linked
  release binary directly (`sh -c` wrapping two invocations — `dump --format html` then
  `extract-images` — with the binary path and output dir passed as quoted positional shell
  arguments, `$1`/`$2`, not string-interpolated into the script body) against
  `st-guide_orig_en.hyp`, asserting the HTML marker is present and exactly 15 images were
  extracted. Wired into `check` alongside `wasmWasiSmokeTest`. `dependsOn
  "linkReleaseExecutableMacosArm64"` by task name (string, since `KotlinNativeLink` is no longer
  imported) ensures a fresh binary each run.
- **Security review**: diff is Gradle build config only (guard removal + one new `Exec` task). No
  attacker-controlled input reaches the new shell script — the fixture path is a fixed literal
  already committed to the repo, and the only two dynamic values (binary path, output dir) are
  build-internal Gradle paths passed as properly quoted `$1`/`$2` positional arguments to `sh -c`,
  not interpolated into the script text, so no injection surface even in principle. No findings.
- **`doc/PLAN-12-19.md`'s follow-on plan (phases 12–19) is now complete** — `doc/PROGRESS.md`
  updated to green for this row, with a summary note in that file's per-phase-19 paragraph.
