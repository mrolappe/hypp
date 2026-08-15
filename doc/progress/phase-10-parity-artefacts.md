# Phase 10 — Parity artefacts

**State: green.**

## Completed

- `doc/model-spec.md` (new): the Rust-port source of truth — every model type, field, variant tag,
  the base-255/hex/charset scalar conventions, all 7 `Diagnostic` variants with their trigger
  conditions, and 7 cross-cutting invariants (total-parse guarantee, link-label-is-span-text,
  absolute-not-delta attributes, lossless-unknown-data, EOF-sentinel exclusion, etc.). Ends with the
  canonical JSON schema, field-by-field, as the exact contract a Rust writer must match.
- `src/commonTest/kotlin/de/rholambdapi/hypp/CanonicalJson.kt` (new): hand-rolled deterministic JSON
  value model + pretty-printer (2-space indent, declaration-order fields, never a hash-map order) and
  `HypDocument.toCanonicalJson()`. No serialization library added — the only requirement is a stable,
  human-diffable rendering, which a ~150-line recursive printer covers; `kotlinx.serialization` would
  need reflection/plugin wiring for no benefit here. Runs identically on `jvm`/`wasmJs`/`wasmWasi`
  since it's pure `commonTest` Kotlin.
- `src/jvmTest/kotlin/de/rholambdapi/hypp/ParityGoldenTest.kt` (new source set, `jvmTest`): 10 tests,
  one per micro-corpus fixture — opens it, serializes, asserts two independent opens+serializes of
  the same bytes produce identical JSON (stability), then compares against the checked-in golden
  under `doc/goldens/`. JVM-only because it needs real filesystem access to read the golden files;
  `wasmJs`/`wasmWasi` have no uniform file-read story (the existing corpus fixtures work around this
  by embedding as base64 constants — see `TestCorpus.kt` — which isn't practical for the goldens,
  the largest of which is ~860 KB).
- `doc/goldens/*.json` (new, checked in): one golden per fixture (`empty`, `textattr`, `colors`,
  `linkattr`, `image`, `limage`, `limage2`, `lines`, `hcp_orig_en`, `st_guide_orig_en`), generated via
  a temporary `writeGoldens()` test method (run once, output spot-checked against `TextTest.kt`'s
  known-good span breakdown for `textattr.hyp`, then removed before commit — not left in the source
  tree as permanent scaffolding).

## Decisions

- **No serialization library.** See above — `kotlinx.serialization` isn't installed, and adding it
  for a one-shot deterministic writer is exactly the "already-installed dependency" rung this doesn't
  clear; a flat recursive `Json` sealed-interface + printer is shorter and has zero toolchain risk
  across three KMP targets.
- **Golden comparison is `jvmTest`-only, per the plan's own verification section** (`doc/PLAN.md`:
  "`./gradlew jvmTest` — micro-corpus assertions and golden JSON comparison"). The writer stays
  portable `commonTest` so a future consumer on any target can still call `toCanonicalJson()`
  directly; only the *file* comparison needs a real filesystem.
- **`styleBits` is emitted raw (the packed `Int`), not decoded into named booleans.** Keeps the JSON
  as a direct, unambiguous mirror of the wire encoding (documented bit-by-bit in `model-spec.md`)
  rather than duplicating decode logic that's already covered by `TextTest.kt`; a Rust port decodes
  the same bits, it doesn't need hypp's Kotlin accessor names echoed back.
- **`ImageNode.pixels` (decoded palette-index bytes), not raw plane data, is what the golden
  captures.** The plane-to-pixel unpacking is the non-trivial part of the format (word-aligned
  rows, bit-per-plane composition) — that's exactly what a Rust port's decoder must reproduce
  bit-for-bit, so the golden has to check the *decoded* output, not the wire bytes it started from.

## Tests added

75 tests on `jvm` (was 65 + 10 new `ParityGoldenTest`), 69 unchanged on `wasmJs`, 65 unchanged on
`wasmWasi` (goldens are jvm-only per the decision above). Full `clean allTests` green on all three
targets.

## Remaining

- Phase 11 (wild sweep) is next per the plan — opt-in corpus-download Gradle task, feature histogram,
  and the `0xa4`/`0xa5`/`0xa6` escape-overlap resolution.
