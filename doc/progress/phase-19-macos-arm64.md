# Phase 19 — CLI Round D (`macosArm64`)

**Status: amber — code complete and confirmed to compile; linking/running blocked in this
environment by a missing full Xcode.app install (only Xcode Command Line Tools are present).**

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

## Remaining / deferred

- Install full Xcode.app on this machine (owner decision — multi-GB download, Apple ID sign-in),
  then re-run `linkReleaseExecutableMacosArm64` and exercise the binary against
  `src/commonTest/resources/corpus/st-guide_orig_en.hyp` (dump) and a fixture with images
  (extract-images), confirming exit codes and output the way Phases 16/18's smoke tests do.
- Once a binary can be produced, worth adding a `macosArm64SmokeTest` `Exec` task (same idiom as
  `wasmWasiSmokeTest`) running the release executable directly against a corpus fixture and wiring
  it into `check`, per the task's guidance to prefer a real automated check over "I ran it once by
  hand" — not done yet since there's nothing to run it against.
- `linuxX64`/`mingwX64` and any CI cross-compilation matrix remain out of scope (plan decision 13).
