# Phase 1 — Skeleton

**State: green.**

## Completed

- KMP module (`de.rholambdapi:hypp:0.1.0-SNAPSHOT`) targeting `jvm`,
  `wasmJs` (nodejs), `wasmWasi` (nodejs).
- Kotlin 2.4.10 pinned explicitly in `gradle/libs.versions.toml` (not the
  Gradle-bundled 1.9.22).
- Gradle wrapper pinned to 8.8, per the environment facts in `doc/PLAN.md`.
- `maven-publish` configured, publishing to `mavenLocal()`.
- Apache-2.0 `LICENSE`, `README.md`, `.gitignore`.
- GitHub repo `github.com/mrolappe/hypp` already existed (created
  2026-08-10, empty) — reused as `origin` rather than created fresh.
- `doc/PROGRESS.md`, `doc/progress/`, `doc/LEARNINGS.md` scaffolded.

## Tests added

- `SkeletonTest.compilesAndRunsOnEveryTarget` (`commonTest`) — trivial
  assertion, run via `./gradlew build`: green on `jvmTest`, `wasmJsTest`
  (Node), `wasmWasiTest` (Node WASI).

## Decisions

- `wasmJs { nodejs() }` and `wasmWasi { nodejs() }` rather than a browser
  target — no DOM dependency in this library, and it avoids a
  karma/headless-Chrome toolchain for CI. Revisit only if a browser-specific
  consumer needs it.
- `./gradlew build` also runs `publishToMavenLocal` successfully (verified
  manually this round, ahead of the "1, revisited" integration-test row in
  the plan) — confirms KMP variant metadata publishes correctly this early.

## Remaining

- The "1, revisited" integration test (a throwaway consumer project
  resolving the published artifact) is deferred to whichever later round
  the plan schedules it — `publishToMavenLocal` succeeding is a good sign
  but is not the same test.
