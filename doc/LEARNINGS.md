# hypp — learnings

Read at the start of every round; included verbatim in every delegated task
prompt. Format: what happened, why, the fix or mitigation, the generalisable
lesson.

## Phase 1

- **`wasmJs`/`wasmWasi` DSL needs opt-in.** Declaring `wasmJs { }` /
  `wasmWasi { }` in `build.gradle.kts` without `@OptIn(ExperimentalWasmDsl::class)`
  compiles but emits an opt-in warning. Fix: `@file`-level import of
  `org.jetbrains.kotlin.gradle.ExperimentalWasmDsl` and
  `@OptIn(ExperimentalWasmDsl::class)` on the `kotlin { }` block. Lesson:
  treat these Wasm-target warnings as build config to fix immediately, not
  noise to tolerate — they will keep reappearing every build otherwise.
- **`maven-metadata.xml` on Maven Central needs the artifact ID, not the
  Gradle plugin ID.** `org/jetbrains/kotlin/kotlin-multiplatform/` 404s;
  `org/jetbrains/kotlin/kotlin-gradle-plugin/` has the real version list.
  Lesson: when checking latest Kotlin version, query `kotlin-gradle-plugin`.

## Phase 2

- **Facts hand-derived from the tiny corpus don't always generalize —
  get a real file into the loop immediately.** `empty.hyp`/`textattr.hyp`
  both end their index table with a type-255 EOF sentinel, which is what
  made "itableCount includes a trailing sentinel" look like a safe general
  rule. `hcp_orig_en.hyp` (a real 57 KB document) has no sentinel at all.
  The fix generalizes cleanly (derive each entry's length against the next
  entry's `seek`, or the file's own byte length when there is no next
  entry) but the *only* thing that caught the wrong assumption was the
  phase-2 integration test opening a real file, not the two hand-verified
  unit tests. Lesson: don't treat the plan's own micro-corpus facts as the
  full picture — the "real consumer as early as feasible" rule in
  `doc/PLAN.md` exists precisely because unit tests on tiny fixtures can
  all pass while the general rule they were derived from is still wrong.
  See `doc/format-notes.md` for the full resolution and evidence.
- **The extended-header terminator is a full 4-byte `id=0, length=0` pair,
  not a bare 2-byte `id=0`.** The prose spec's wording ("terminated by id
  0") is genuinely ambiguous about whether the terminator still carries its
  `length` field. Reading it as bare `id=0` leaves reader position short by
  2 bytes, silently misaligning the start of the data region on *every*
  file (both tiny corpus files landed exactly 2 bytes before their first
  entry's recorded `seek`). Lesson: when a spec says a list is
  "terminated by X", check empirically whether the terminator record's
  *shape* is the full record type or a truncated one — don't assume the
  shorter reading. See `doc/format-notes.md`.
- **Multiplatform test-resource loading isn't solved by default.**
  `src/commonTest/resources/` doesn't reliably reach `wasmJs`/`wasmWasi`
  test execution without extra Gradle wiring. For a small corpus, embedding
  bytes as base64 string literals (stdlib `kotlin.io.encoding.Base64`,
  chunked to stay under the JVM class file's 64 KB per-string-constant
  limit) sidesteps the problem entirely and works identically on all three
  targets. Real resource loading is still an open toolchain question for
  whenever a corpus file gets too large to embed this way.
