# Phase 17 — CLI Round B (GraalVM native-image)

**Status: green.**

## What was built

- **`hypp-cli/build.gradle.kts`** — added `nativeImageCli`, an opt-in `Exec` task:

  ```kotlin
  val nativeImageCli by tasks.registering(Exec::class) {
      dependsOn(fatJar)
      commandLine("native-image", "-jar", fatJar.get().outputs.files.singleFile.path, "hypp-cli")
  }
  ```

  `dependsOn(fatJar)` so the jar it points `native-image` at is always current. Not wired into
  `build`/`check` — same pattern as the root project's `corpusSweep` (`build.gradle.kts` lines
  ~58–66): a bare `tasks.registering(...)`, no `tasks.named("build") { dependsOn(...) }` /
  `tasks.named("check") { dependsOn(...) }` anywhere. Requires a local GraalVM with `native-image`
  on `PATH`; nothing else in the build depends on that being true.

## Why opt-in

`native-image` is a heavyweight, environment-specific tool (a full GraalVM install, AOT
compilation minutes-long even for small jars) that most contributors and CI won't have. Making it
opt-in keeps `./gradlew build`/`check` fast and hermetic, matching the existing `corpusSweep`
precedent (network-dependent, also excluded from the default lifecycle).

## Verification

- `./gradlew hypp-cli:tasks --all` lists `nativeImageCli` as a registered task.
- `./gradlew build` (in `hypp-cli/`) — **BUILD SUCCESSFUL**, unaffected: `nativeImageCli` did not
  run as part of `build`/`check` (confirms it's correctly opt-in, not accidentally wired into any
  lifecycle task).
- `nativeImageCli` itself was **not run** — no GraalVM installed in this environment (`native-image`
  not on `PATH`, confirmed before starting). Per the plan, actually producing `./hypp-cli` and
  running it against a corpus fixture is local-only verification, not covered by this task or CI.

## Security-relevant sink

None. The new task's command line is built entirely from Gradle-internal values (`fatJar`'s output
file path, a fixed `"hypp-cli"` image name) — no user-, document-, or environment-derived data
reaches `commandLine(...)`. `security-review` run over the diff confirmed no findings.

## Remaining / deferred

- Actually running `nativeImageCli` with a real GraalVM and validating the produced `./hypp-cli`
  binary against corpus fixtures — deferred to whoever has GraalVM installed locally.
- Phases 18–19: `wasmWasi` and `macosArm64` build-target rounds, same additive pattern.
