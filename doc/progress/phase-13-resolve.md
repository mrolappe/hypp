# Phase 13 — Traversal API: `resolve()`

**Status: green.**

## What was built

- **`ResolvedTarget.kt`** (new) — `sealed interface ResolvedTarget` with `ToNode`, `ToImage`,
  `ToExternalRef`, `ToSystemAction`, `Missing`, plus `HypDocument.resolve(target: NodeIndex):
  ResolvedTarget`. Dispatches on `entry(target)?.type`: null → `Missing`; `TYPE_INTERNAL`/
  `TYPE_POPUP` → `ToNode` via `node()`; `TYPE_IMAGE` → `ToImage` via `image()`;
  `TYPE_EXTERNAL_REF` → `ToExternalRef` via `IndexEntry.externalRef()`; every remaining type
  (`TYPE_SYSTEM`/`TYPE_REXX_SCRIPT`/`TYPE_REXX_COMMAND`/`TYPE_QUIT`/`TYPE_CLOSE`) → `ToSystemAction`,
  collapsed into the `else` branch since they carry no accessor beyond the raw `IndexEntry`.
  Reuses the existing `entry()`/`node()`/`image()` accessors on `HypDocument` — no new lookup logic.

## Deviation from plan

`doc/PLAN-12-19.md`'s Phase 13 sketch used a nested `IndexEntry.ExternalRef`. That type was never
introduced in this codebase — Phase 12 instead added `ExternalRef` as a top-level data class in
`IndexEntry.kt` alongside the `externalRef()` extension. `ResolvedTarget.ToExternalRef` uses that
top-level `ExternalRef`, matching the already-committed `ResolvedTargetTest.kt` (the actual spec
for this phase).

## Verification

`./gradlew jvmTest wasmJsTest wasmWasiTest` — all green, including `ResolvedTargetTest`
(`everyEntryTypeResolvesToItsVariant`, `outOfRangeIndexIsMissing`).

## Remaining / deferred (per plan)

- Phase 14+: `hypp-cli` scaffold, renderers, CLI commands — independent of this phase.
