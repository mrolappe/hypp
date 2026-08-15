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
