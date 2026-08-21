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
| 19 | CLI Round D (`macosArm64`) | green |

Per-phase detail: `doc/progress/phase-NN-<name>.md`. Phases 12–19 are planned
in `doc/PLAN-12-19.md` (approved 2026-08-18, not `doc/PLAN.md`'s original
11-phase roadmap — a follow-on plan, same convention). Execution is by
delegating each step to sub-agents with model overrides per that plan's
per-step assignments, not self-implemented directly — see that file's
"Execution mode" note.

**2026-08-21: Xcode.app installed and its license accepted on this machine — Phase 19 finished for
real.** The `onlyIf` opt-in guard added 2026-08-19 was deleted (`macosArm64` is a first-class
target again, same footing as `jvm()`/`wasmWasi()`, matching plan decision 4/13), and
`linkReleaseExecutableMacosArm64` now succeeds. The real linked binary
(`hypp-cli/build/bin/macosArm64/releaseExecutable/hypp-cli.kexe`) was run directly (no VM/
interpreter) against `st-guide_orig_en.hyp`: `dump --format html` produced well-formed HTML with
embedded base64 PNGs, and `extract-images` wrote all 15 valid PNGs (`file` confirmed), matching the
JVM/wasmWasi targets' verified counts on the same fixture. A new `macosArm64SmokeTest` Gradle
`Exec` task (mirroring `wasmWasiSmokeTest`'s idiom, one dev-machine shell script rather than a
WASI host) now runs both checks automatically and is wired into `check`. `./gradlew clean build`
is green end to end, including this new task. `doc/PLAN-12-19.md`'s follow-on plan (phases 12–19)
is now **complete**. See `doc/progress/phase-19-macos-arm64.md`'s "Finished" section for the full
writeup.

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
