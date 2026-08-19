# Phase 18 — CLI Round C (`wasmWasi`)

**Status: green.**

## What was built

- **`hypp-cli/settings.gradle.kts`** — two more `dependencySubstitution` entries, verified
  empirically rather than assumed: `./gradlew :publishToMavenLocal` (root project) then
  `find ~/.m2/repository/de/rholambdapi` confirmed the wasmWasi target's Maven coordinate is
  `de.rholambdapi:hypp-wasm-wasi` (matches Kotlin's usual `<module>-<target>` convention, but
  confirmed, not guessed). A third substitution, `de.rholambdapi:hypp` (the root/common metadata
  coordinate), turned out to be required too — see "Surprise: commonMain needs its own dependency"
  below.
- **`hypp-cli/build.gradle.kts`** — `wasmWasi { binaries.executable(); nodejs() }` under
  `@OptIn(ExperimentalWasmDsl::class)` (same opt-in the root project's `build.gradle.kts` already
  uses for `wasmJs`/`wasmWasi`), plus `commonMain`/`wasmWasiMain` dependency blocks, plus a new
  `wasmWasiSmokeTest` `Exec` task wired into `check`.
- **`hypp-cli/src/wasmWasiMain/kotlin/de/rholambdapi/hypp/cli/Io.wasmWasi.kt`** (new) — hand-rolled
  raw `@WasmImport("wasi_snapshot_preview1", ...)` bindings for `path_open`, `fd_read`, `fd_write`,
  `fd_close`, `fd_filestat_get`, `fd_prestat_get`, `proc_exit`, `args_get`, `args_sizes_get`. See
  "What API was used and why" below for why this was necessary instead of a stdlib call.
- **`hypp-cli/src/wasmWasiMain/kotlin/de/rholambdapi/hypp/cli/Main.kt`** (new) — the wasmWasi
  composition root, structurally the JVM one with the substitutions the plan anticipated: no
  `ImageIoPngEncoder`/`zip` (JVM-only), `defaultRenderers()` called with no argument (gets
  `StoredPngEncoder`), `archiveRenderers = emptyMap()` so `--format epub` is absent (plan
  decision 6 — `dump()` in `Commands.kt` already falls through to `renderers[format] ?: error(...)`
  with no changes needed there), and command-line args read via `wasiCliArgs()` instead of trusting
  `main`'s own `args` parameter — see the args finding below.
- **`hypp-cli/src/wasmWasiTest/js/cliRunner.mjs`** (new) — a hand-written Node WASI host (same
  idiom as the root project's `wasmJsFacadeSmokeTest` → `facadeSmokeTest.mjs`), because Kotlin's
  own generated loader grants no filesystem access at all (see below). Preopens the `hypp-cli`
  project root at guest path `.`, runs `dump <fixture> --format html --out <path>` and
  `extract-images <fixture> --out <dir>` against the real corpus fixtures already committed at
  `src/commonTest/resources/corpus/`, and asserts on the actual files produced.
- **`wasmWasiSmokeTest`** (`Exec` task) — `dependsOn("compileProductionExecutableKotlinWasmWasi")`,
  runs `cliRunner.mjs` against the built `hypp-cli.wasm`, wired into `check` (unlike Phase 17's
  `nativeImageCli`, this needs no extra local tooling — Node's built-in `node:wasi` module, no
  `wasmtime` binary, no CLI flag on Node 26.7.0).

## What API was used and why

**There is no usable public file I/O in Kotlin 2.4.10's wasmWasi stdlib.** Confirmed by pulling
`kotlin-stdlib-wasm-wasi-2.4.10-sources.jar` from Maven Central and reading it directly (not
guessed from docs):

- `kotlin.wasm.wasi` (the package name that would sound most promising) is a literal one-line
  empty marker package — `wasmWasiMain/kotlin/wasm/wasi/Wasi.kt`'s entire content is a header
  comment plus `package kotlin.wasm.wasi`, used only for target-detection statistics. No
  declarations at all.
- The stdlib's own WASI syscalls exist (`wasmWasiMain/kotlin/io.kt`, `wasmWasiMain/kotlin/WasiError.kt`)
  and back `println`/`print`, but every one of them — `wasiRawFdWrite`, `WasiErrorCode`,
  `WasiError`, `wasiPrintImpl` — is `internal`, invisible outside the stdlib module itself.
  `readln()`/`readlnOrNull()` are literally `TODO("wasi")` in this version — unimplemented.
- What *is* public: `kotlin.wasm.WasmImport` (import a function from a wasm host module) and
  `kotlin.wasm.unsafe.{MemoryAllocator, Pointer, withScopedMemoryAllocator, UnsafeWasmMemoryApi}`
  (linear-memory access), both opt-in (`@ExperimentalWasmInterop`, `@UnsafeWasmMemoryApi`) but
  present and stable enough to build on — this is the same low-level toolkit the stdlib's own
  `io.kt` uses internally for stdout.

Given that, `Io.wasmWasi.kt` hand-writes the same small set of `wasi_snapshot_preview1` raw
imports the stdlib itself would need, following the exact shape of `kotlin.io`'s internal
`wasiRawFdWrite` (a `@WasmImport`-annotated `external fun`, called from inside
`withScopedMemoryAllocator`). This is not a novel technique — a third-party reference project,
[skuzmich/kotlin-wasi-bindings-experiments](https://github.com/skuzmich/kotlin-wasi-bindings-experiments),
has a fuller (code-generated) set of these same bindings and was used to cross-check struct
layouts (`Filestat`'s `size` field at byte offset 32, `Iovec`/`Ciovec` as an 8-byte
pointer+length pair) before writing the trimmed-down version actually needed here (open/read/
write/close a file, plus `args_get`/`proc_exit`. `path_open`, `fd_read`, `fd_write`, `fd_close`,
`fd_filestat_get`, `fd_prestat_get` are the WASI preview1 syscalls used; no reads outside
`with rights = FD_READ | FD_FILESTAT_GET` and no writes outside `rights = FD_WRITE`, matching the
principle of least privilege per opened file descriptor).

**Stability level**: `@WasmImport`, `MemoryAllocator`/`Pointer`/`withScopedMemoryAllocator` are all
documented as "Since Kotlin 1.8" on the public API reference (kotlinlang.org), just gated behind
opt-in annotations (`@ExperimentalWasmInterop`, `@UnsafeWasmMemoryApi`) rather than being fully
stabilized. The `wasi_snapshot_preview1` import module name itself is WASI Preview 1 — the version
Kotlin/Wasm-WASI currently targets ([KT-64568](https://youtrack.jetbrains.com/issue/KT-64568)
tracks the eventual move to Preview 2/WASI 0.2, which would obsolete this file's raw imports).

## Two things confirmed empirically, not assumed

1. **Kotlin's own generated Node loader grants no filesystem access.** After building
   `compileProductionExecutableKotlinWasmWasi`, the toolchain-generated
   `build/compileSync/wasmWasi/main/productionExecutable/kotlin/hypp-cli.mjs` reads:
   ```js
   const wasi = new WASI({ version: 'preview1', args: argv, env, });
   ```
   No `preopens` key at all — so `wasmWasiNodeRun`/`wasmWasiNodeTest` give the program zero WASI
   preopened directories. Any real file access needs a driver script the CLI's own build controls
   (`cliRunner.mjs`), matching the plan's note to adapt the `wasmJsFacadeSmokeTest` idiom rather
   than rely on the default node task.
2. **`fun main(args: Array<String>)` is always empty on this target.** Verified by a temporary
   debug line (`printErrorLine("args.size=${args.size}")`) built and run against the real `.wasm`:
   regardless of what the WASI host's `args` array contained, `args.size` was `0` inside `main`.
   Kotlin 2.4.10's wasmWasi entry point does not wire `args_get()` into `main`'s parameter the way
   the JVM launcher passes `argv`. `Main.kt` works around this by calling `wasiCliArgs()`
   directly (its own `args_get`/`args_sizes_get` bindings), dropping index 0 to match the
   WASI convention of `argv[0]` being a program name — the driver script passes
   `args: ["hypp-cli", ...userArgs]`, so `wasiCliArgs()` returns exactly `userArgs`, matching every
   other Kotlin target's `main(args)` (program name excluded).

## Surprise: commonMain needs its own dependency once there are two platform targets

Adding `wasmWasi` as hypp-cli's second target (alongside `jvm`) broke the build in an unexpected
place: `compileCommonMainKotlinMetadata` failed with dozens of "Unresolved reference: HypDocument"
errors across `Commands.kt`/`render/*.kt`, none of which changed in this phase. Cause: with only
one platform target, Kotlin's Gradle plugin uses a single-target shortcut that skips genuine common
-metadata compilation, so `commonMain`'s implicit reliance on `jvmMain`'s
`de.rholambdapi:hypp-jvm` dependency "just worked" by accident. A second target forces real
metadata compilation, which needs `commonMain` to declare its own dependency — on the *root*
`de.rholambdapi:hypp` coordinate (the common/metadata publication), not either platform variant.
Fixed with a third `dependencySubstitution` entry plus
`commonMain.dependencies { implementation("de.rholambdapi:hypp:0.1.0-SNAPSHOT") }`. Worth recording
in `doc/LEARNINGS.md`-style form for Phase 19 (`macosArm64`), which will hit the same thing when it
adds a *third* target.

## Preopen / path model

`cliRunner.mjs` preopens exactly one directory — the `hypp-cli` project root, at WASI guest path
`.` — mapped to `process.cwd()`. `Io.wasmWasi.kt`'s `findPreopenedDirFd()` discovers its file
descriptor by probing `fd_prestat_get` from fd 3 upward (not hardcoded, since WASI doesn't
guarantee preopens start at a specific number, even though 3 is the practical convention right
after stdio) and uses it as the `path_open` `dirFd` for every relative path. **This only resolves
relative paths** (`dump some/file.hyp`, not `dump /abs/file.hyp`) — an intentional, documented scope
cut: WASI's sandboxed-directory-fd model doesn't have a "whole filesystem" concept the way the JVM
does without preopening `/` itself (which would defeat the sandbox for no benefit here), and the
"done" criteria only requires a real fixture to dump correctly, which relative paths satisfy. Noted
as a known difference from the JVM target, not a bug.

## Security review

No new sink beyond what Phase 16 already covers. `writeBytes`'s path argument is the same trusted
`--out` CLI argument the JVM target already writes without sanitization (operator input, not
document content — same trust boundary noted in `doc/progress/phase-16-cli-commands.md`).
`extract-images`'s filename sanitization (`sanitizeImageFileName` in `Commands.kt`) is unchanged,
commonMain, and reused as-is. The new raw WASI bindings themselves take no untrusted input beyond
what's already validated by `ArgParser.kt`/`Commands.kt` before reaching `Io.wasmWasi.kt`. A
`security-review` pass over this round's full diff found no findings requiring changes (see
commit message / PR for the recorded outcome).

## Verification

- `hypp-cli/`: `./gradlew build` — **BUILD SUCCESSFUL**, including `wasmWasiSmokeTest` (now part of
  `check`) and the existing `wasmWasiNodeTest`/`jvmTest`/`fatJar` suites, all still green.
- `~/studio/hypp` (root): `./gradlew build` — **BUILD SUCCESSFUL**, unaffected (`wasmJsFacadeSmokeTest`
  still passes; nothing in the root project changed this phase).
- `wasmWasiSmokeTest` end to end against real corpus fixtures: `dump
  src/commonTest/resources/corpus/textattr.hyp --format html --out build/wasmWasiSmokeTest/dump.html`
  produces a file starting with `<!doctype html>` (matches Phase 16's JVM fat-jar smoke test's
  assertion); `extract-images src/commonTest/resources/corpus/st-guide_orig_en.hyp --out
  build/wasmWasiSmokeTest/images` produces 15 files, confirmed real PNGs via `file`.
- Manual spot-check beyond what the Gradle task covers: `validate` and `inspect` against
  `textattr.hyp`, run through the same hand-rolled Node WASI host — `validate` printed
  `no diagnostics` (exit 0), `inspect` printed the header/extended-headers/TOC/entry-counts/
  link-resolution summary, matching the JVM target's output shape from Phase 16's manual
  verification.

## Remaining / deferred

- Phase 19 (`macosArm64`): expect the same "commonMain needs its own `hypp` dependency" issue to
  resurface (three targets now) — already documented above so it isn't re-discovered from scratch.
  Kotlin/Native's own file I/O story is `kotlinx.cinterop`/POSIX interop, a different mechanism
  entirely from this phase's raw `@WasmImport` bindings — no code here is directly reusable there.
- Absolute-path support on wasmWasi (preopening `/` itself) was deliberately not built — out of
  scope per the plan's "done" criteria, and would weaken the WASI sandboxing model for a case the
  plan doesn't require. Revisit only if a real consumer needs it.
