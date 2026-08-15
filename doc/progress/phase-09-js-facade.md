# Phase 9 — JS façade

**State: green.**

## Completed

- `src/wasmJsMain/kotlin/de/rholambdapi/hypp/js/HyppJs.kt` (new, `wasmJs`-only): a flat,
  handle-based `@JsExport` API. `hyppOpen(base64: String): Int` decodes and opens a document,
  returning a handle (or -1 on failure) into a module-level `HashMap<Int, HypDocument>`; every
  other function takes that handle plus plain `Int` indices and returns a primitive or `String`.
  Covers entries, node kind/title/lines, spans (text, style bits, link kind/target/line number),
  graphics (kind + per-variant fields, packed where needed) and diagnostics (tag + node index +
  a generic numeric/text secondary field) — the full "spans, sealed graphics and diagnostics"
  scope the plan asked for.
- `src/wasmJsTest/kotlin/de/rholambdapi/hypp/js/HyppJsTest.kt`: thorough coverage of every
  exported function, called as plain Kotlin — runs on the real `wasmJs` target via `wasmJsNodeTest`
  (part of `check`), 4 new tests.
- `src/wasmJsTest/js/facadeSmokeTest.mjs` + the `wasmJsFacadeSmokeTest` Gradle task (`build.gradle.kts`,
  wired into `check`): a real external Node script that imports the compiled `hypp.mjs` and
  reconstructs a node's text from the flattened span calls — the "something outside Kotlin calls
  it" proof the plan's integration table asks for, which `HyppJsTest.kt` alone cannot give (a
  same-module Kotlin call bypasses the generated JS bindings).
- `wasmJs { binaries.library() }` added to `build.gradle.kts` — required for `@JsExport` to
  produce an importable JS/`.mjs` module rather than a self-running executable.
- **Bug fix, `HypDocument.open`:** rejects input under 4 bytes before reading the magic, instead
  of throwing `IndexOutOfBoundsException`. Found by the façade's own test suite (`hyppOpen` on 3
  garbage bytes) — the JS boundary is the first place arbitrary/adversarial-length input actually
  reaches `open()`; every existing fixture is a well-formed file at least as long as the header.
  Root-cause fixed in `HypDocument.open` itself (one guard before the read), not patched at the
  `hyppOpen` call site, so every caller — JVM included — gets the same total-parse guarantee.

## Decisions

- **Kotlin/Wasm 2.4.10's `@JsExport` supports only primitive, `String`, `external` and function
  types — no arrays, no exported classes.** Discovered empirically (see Learnings): a `ByteArray`
  parameter, an `IntArray`/`Array<String>` return, and an `@JsExport`-annotated class all fail to
  compile with "Only external, primitive, string, and function types are supported in Kotlin/Wasm
  JS interop." This reshaped the whole façade: bytes go in as a base64 `String` (reusing the same
  encoding phase 1 already uses for the embedded test corpus), and "array form" becomes a
  `*Count` function plus indexed getters — a flat/C-style API — rather than literal returned
  arrays. This is a stronger, toolchain-forced version of the plan's own "tag-int + array form"
  phrasing, not a departure from its intent.
- **State lives behind an opaque `Int` handle, not a returned document object.** Exported classes
  aren't supported at all in this Kotlin version, so there's no way to hand JS a live reference to
  a `HypDocument`; a handle into a module-level map is the standard shape for this constraint (the
  same pattern `wasm-bindgen`-style APIs use) and needed no new abstraction to introduce.
- **A link's label getter was deliberately omitted.** `hyppSpanText` already returns it — the
  model's own invariant ("a link's label is also its span's text") means a separate accessor would
  just be a second name for the same call.
- **No `hyppClose`/handle-release function.** Nothing in phase 9's scope needs one (no consumer
  yet opens and discards many documents in one process); the map only grows for the lifetime of
  one wasm module instance. Add one if that ever matters.
- **Production, not development, library distribution backs the Gradle-wired smoke test.**
  `assemble` already builds `compileProductionLibraryKotlinWasmJs` for `wasmJsJar`/publishing;
  wiring the smoke test to the *development* distribution instead made Gradle's task-validation
  fail (`wasmJsProductionLibraryCompileSync` and `wasmJsNodeDevelopmentLibraryDistribution` both
  write `build/wasm/packages/hypp/kotlin` with no declared ordering between them). Depending on
  the production distribution reuses a task `build` already runs, so there's no such conflict.

## Tests added

69 tests on `wasmJs` (was 65), 65 unchanged on `jvm`/`wasmWasi` (the façade is `wasmJsMain`-only,
per the plan). `HyppJsTest.kt`, 4 tests:

- `openReturnsAFreshHandlePerCallAndMinusOneOnFailure` — also the test that found the
  `HypDocument.open` truncation bug above.
- `flattensTextattrHypSpanBySpan` — entry/node/line/span accessors, including the packed
  `TextStyle` bits, against the same fixture `TextTest.kt` already asserts span-by-span.
- `flattensLinkattrHypLinksWithTargetAndKind` — a link span's kind/target/line-number, and that a
  type-7 quit dummy entry correctly reports `hyppNodeExists == false` while still resolving as a
  link target.
- `graphicAndDiagnosticAccessorsReturnSentinelsWhenAbsent` — the `-1`/`""` sentinel contract on a
  node with neither.

`facadeSmokeTest.mjs` (run by `wasmJsFacadeSmokeTest`, not `kotlin.test`): opens `textattr.hyp`
from real Node, reconstructs line 1's text ("Dies ist heller Text.") by iterating
`hyppLineSpanCount`/`hyppSpanText`, exactly the pattern a real JS consumer would use.

## Remaining

- Graphics/diagnostics flattening is implemented and unit-tested (`HyppJsTest.kt`) but not
  exercised by the external Node smoke test — no vendored fixture makes that a cheap addition
  (the graphics fixtures are large embedded blobs; see phase 7's note on this). Not corpus-blocked
  the way phase 7's multi-plane image test was, just not yet worth the file size — revisit if a
  real JS consumer needs it exercised end-to-end.
- Phase 10 (parity artefacts) is next per the plan.
