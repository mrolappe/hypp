# Phase 14 — `hypp-cli` module scaffold + `Renderer` abstraction skeleton

**Status: green.**

## What was built

- **`hypp-cli/`** — a separate Gradle build (own `settings.gradle.kts`, `gradlew`/`gradlew.bat`/
  `gradle/wrapper/*` copied from the root build so it stands on its own), composite-building the
  root via `includeBuild("..")` with the `dependencySubstitution` fix already proven in
  `doc/LEARNINGS.md` (`substitute(module("de.rholambdapi:hypp-jvm")).using(project(":"))`).
- **`hypp-cli/build.gradle.kts`** — KMP plugin, `jvm()` only, `implementation("de.rholambdapi:hypp-jvm:0.1.0-SNAPSHOT")`
  on `jvmMain`, `commonTest` → `kotlin("test")`, and a hand-rolled `fatJar` (`Jar` task, `zipTree`
  over `jvmJar`'s output plus the jvm main compilation's runtime dependency files, dedup via
  `DuplicatesStrategy.EXCLUDE`). Confirmed to bundle both `hypp-cli`'s own classes and `hypp-jvm`'s
  (`HypDocument.class` present in the fat jar alongside `IdentityRenderer.class`).
- **`Renderer.kt`** (`commonMain`, package `de.rholambdapi.hypp.cli.render`) — `interface Renderer`,
  `data class RenderedFile(val path: String, val bytes: ByteArray)`, `interface ArchiveRenderer`,
  and the empty `renderers: Map<String, Renderer> = emptyMap()` registry (filled in Phase 15).
  `Renderer`/`ArchiveRenderer` take the real core type, `de.rholambdapi.hypp.HypDocument`.
- **`IdentityRenderer.kt`** (`commonMain`, same package) — `object IdentityRenderer : Renderer`
  rendering `document.nodes.size.toString()`, the trivial real implementation the round-trip test
  exercises.
- **`Io.kt`** (`commonMain`) — `expect fun readBytes(path: String): ByteArray` / `expect fun
  writeBytes(path: String, bytes: ByteArray)`.
- **`Io.jvm.kt`** (`jvmMain`) — `actual` implementations via `java.io.File`.
- **`IdentityRendererTest.kt`** (`jvmTest`) — builds a minimal two-node `HypDocument` (same
  fixture-construction style as `ResolvedTargetTest.kt`), looks `IdentityRenderer` up from a
  test-local `Map<String, Renderer>`, asserts `render(document) == "2"`, then round-trips that
  string through `writeBytes`/`readBytes` against a temp file.

## Deviation from plan

- **No `application` plugin.** The plan/task spec called for it, but Kotlin 2.4.10's KMP plugin
  actively refuses to configure alongside it: `checkKotlinGradlePluginConfigurationErrors` fails
  with *"'application' Plugin Incompatible with 'org.jetbrains.kotlin.multiplatform' Plugin"* (it
  transitively applies the `java` plugin, which KMP also rejects). This is a hard incompatibility
  in the tool, not a config mistake — the suggested alternatives (a separate `java`-plugin
  subproject, or the newer JVM-binaries DSL with a `mainClass`) both need a `Main.kt`, which is
  explicitly out of scope for this phase. Dropped `application` entirely; the `fatJar` task doesn't
  need it (it reads `jvmJar`'s output directly). Revisit in Phase 16 when `Main.kt` exists and a
  `mainClass` can actually be set — via the JVM-binaries DSL or a small `run` task, whichever fits
  what Phase 16 needs.
- **No shared version catalog.** `hypp-cli` is a separate composite-build entry point (not a
  subproject of root), so it can't see root's `gradle/libs.versions.toml`. Declared the Kotlin
  plugin version directly (`kotlin("multiplatform") version "2.4.10"`, matching root's catalog
  entry) rather than duplicating a one-entry catalog file.
- **Own Gradle wrapper.** The task's verification line reads `./gradlew hypp-cli:build`, which
  reads as a subproject task path — but `hypp-cli` is deliberately *not* a subproject (per the
  spec's own instruction to leave root `settings.gradle.kts` untouched); it's the includer, not the
  included, in the composite build. So it needs its own wrapper to be runnable standalone. Copied
  `gradlew`/`gradlew.bat`/`gradle/wrapper/*` from root (same Gradle 8.8) rather than inventing a new
  mechanism.
- **TDD note:** `IdentityRenderer.kt` was written before the first test run in this session, but
  the red step was still exercised for real: the renderer file was moved aside and `./gradlew
  jvmTest` re-run, confirming the failure was exactly `Unresolved reference 'IdentityRenderer'`
  (composite-build resolution and everything else already compiling) before restoring it for green.

## Verification

Run from `hypp-cli/` (its own wrapper, since it's the composite build's entry point):

- `./gradlew jvmTest` → **BUILD SUCCESSFUL**, `IdentityRendererTest.rendersAndRoundTripsThroughIo`
  passes.
- `./gradlew build fatJar` → **BUILD SUCCESSFUL**; fat jar at
  `hypp-cli/build/libs/hypp-cli-0.1.0-SNAPSHOT-all.jar` contains both
  `de/rholambdapi/hypp/cli/render/IdentityRenderer.class` and `de/rholambdapi/hypp/HypDocument.class`
  (confirmed via `jar tf`), proving the composite build actually bundles `hypp-jvm`'s compiled
  output, not just resolves it at compile time.
- No `publishToMavenLocal` was needed — `includeBuild`'s `dependencySubstitution` resolves
  `de.rholambdapi:hypp-jvm:0.1.0-SNAPSHOT` straight against the in-progress root build's project
  output, confirming the `doc/LEARNINGS.md` fix generalizes beyond the earlier throwaway consumer.
- Root repo (`~/studio/hypp`, outside `hypp-cli/`) left untouched: `git status --short` shows only
  `hypp-cli/` as untracked before this commit.

## Remaining / deferred

- Phase 15: real renderers (HTML/Markdown/etc.) fill the `renderers` registry, tested against the
  micro-corpus.
- Phase 16: `Main.kt`, `ArgParser.kt`, `Commands.kt`, and revisiting how (or whether) to get a
  runnable `run`/native entry point now that `application` is off the table.
- Phases 17–19: GraalVM native-image, `wasmWasi`, `macosArm64` rounds — all build on this scaffold
  unmodified per the plan's design (`commonMain` renderer code is target-agnostic already).
