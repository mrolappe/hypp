# hypp — progress index

See `doc/PLAN.md` for the full plan. One entry per phase, updated at the end
of every round.

| Phase | Name | State |
|---|---|---|
| 1 | Skeleton | green |
| 2 | Container | green |
| 3 | lh5 decompression | green |
| 4 | Charsets | green |
| 5 | Node prologue | green |
| 6 | Text and spans | green |
| 7 | Images | green |
| 8 | Document API | green |
| 9 | JS façade | green |
| 10 | Parity artefacts | green |
| 11 | Wild sweep | green |
| 12 | `.REF` parsing | green |
| 13 | Traversal API: `resolve()` | green |
| 14 | `hypp-cli` scaffold + `Renderer` abstraction | green |
| 15 | Six renderers | green |
| 16 | CLI commands + Round A (JVM fat jar) | green |
| 17 | CLI Round B (GraalVM native-image) | green |
| 18 | CLI Round C (`wasmWasi`) | green |
| 19 | CLI Round D (`macosArm64`) | amber — code green, linking opt-in pending local Xcode install |

Per-phase detail: `doc/progress/phase-NN-<name>.md`. Phases 12–19 are planned
in `doc/PLAN-12-19.md` (approved 2026-08-18, not `doc/PLAN.md`'s original
11-phase roadmap — a follow-on plan, same convention). Execution is by
delegating each step to sub-agents with model overrides per that plan's
per-step assignments, not self-implemented directly — see that file's
"Execution mode" note.

Phase 19's code is complete and `hypp-cli:compileKotlinMacosArm64` succeeds, but linking
(`linkReleaseExecutableMacosArm64`) and therefore running the binary against a corpus fixture is
blocked in this environment: only Xcode Command Line Tools are installed, and Kotlin/Native's
macOS/iOS toolchain hard-requires a full `Xcode.app` (`xcrun xcodebuild -version` must succeed).
See `doc/progress/phase-19-macos-arm64.md` for the full investigation and its "Made opt-in"
follow-up section. **User decision (2026-08-19): rather than install Xcode, made the
`macosArm64` link tasks opt-in** — `hypp-cli/build.gradle.kts` now guards
`linkDebugExecutableMacosArm64`/`linkReleaseExecutableMacosArm64` (and the test-link variant)
with `onlyIf` so they're skipped when pulled in transitively by `build`/`check`, but still run
normally when invoked directly by name (`./gradlew hypp-cli:linkReleaseExecutableMacosArm64`) —
this deviates from plan decision 4/13's "first-class target" framing in favor of the same
"opt-in, not part of build/check" posture Phase 17's `nativeImageCli` already uses. `./gradlew
hypp-cli:build`/`check` are green again. `doc/PLAN-12-19.md`'s follow-on plan is still **not**
being called complete — the last phase's real "Done" bar (produce and run a real native binary)
still hasn't been met; once Xcode is installed and the binary verified, this row and this note
should be updated to green and the plan marked complete, matching the convention already used for
the original 11-phase `doc/PLAN.md` below.

## Post-plan follow-ups (2026-08-15)

The plan's 11 phases are done; these are follow-up tasks done afterward, not
part of the phase roadmap:

1. **includeBuild integration check** — done. See
   `doc/progress/phase-01-skeleton.md` § Remaining and `doc/LEARNINGS.md` §
   "Post-plan follow-up: includeBuild integration check".
2. **Unknown-escape files named** — done. See
   `doc/progress/phase-11-wild-sweep.md` and `doc/LEARNINGS.md` §
   "Post-plan follow-up: unknown escapes by file".
3. **CLI consumer design space** — brainstormed, no decisions. See
   `doc/cli-design-space.md`.
4. **Library documentation for human and agentic/AI consumers** — see
   `doc/guide/` (`overview.md`, `concepts.md`, `api.md`).
