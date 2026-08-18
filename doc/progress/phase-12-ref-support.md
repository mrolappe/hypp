# Phase 12 — `.REF` parsing

**Status: green.** Parser implementation (Opus, `ba35526`) + property-based round-trip test (Haiku) + doc write-ups.

## What was built

- **`RefFile.kt`** — a complete `.REF` binary format parser with object model:
  - `RefEntry` sealed interface: `FileName(name)` / `NodeName(name)` / `AliasName(name)` / `LabelName(name, lineNumber)` / `DatabaseName(name)`.
  - `RefFileCatalog` — groups entries under a `FileName`, tracking `nodeNames`, `aliasNames`, `labels` (with line numbers), and `databaseName`.
  - `RefModule(entries: List<RefEntry>)` with `files(): List<RefFileCatalog>` grouping helper.
  - `RefFile(modules: List<RefModule>)` with `find(fileName, nodeName): RefFileCatalog?` lookup (case-insensitive, `.HYP`-suffix-tolerant).
  - `RefFile.parse(bytes: ByteArray): RefParseOutcome` — sealed `Success(refFile)` / `Failure(reason)` with three failure variants (`InvalidMagic`, `Truncated`, `UnknownEntryId`).
  - Bounds-checking before allocating (same hostile-input discipline as `HypDocument.open`); module length checked against available buffer; entry strings checked against module boundaries.
  - Two implicit design decisions: empty modules (length=0, count=0) are byte-identical to the 8-zero-byte terminator and end the file; files without an explicit terminator are accepted at a module boundary, matching `.HYP`'s own optional EOF sentinel convention.

- **`IndexEntry.externalRef(): ExternalRef`** — added to `IndexEntry.kt` (committed `ba35526`):
  - Data class `ExternalRef(val fileName: String?, val nodeName: String)`.
  - Splits `name` on first `/`; no-`/` case yields `fileName = null`.
  - Tested against all 16 real type-2 entries in `hcp_orig_en.hyp`, including both known anomalies.

- **`RefFileTest.kt`** — hand-constructed test suite covering:
  - Single module, multi-module, label with line number, grouping into catalogues, orphan entry handling.
  - Terminator-only empty file; zero-entry module (indistinguishable from terminator); case-insensitive/suffix-tolerant `find()`.
  - Failure cases: invalid magic, truncated module header, truncated entry, truncated label's line number, unknown entry id.
  - File ending without explicit terminator (accepted, matching `.HYP` convention); bytes after terminator (ignored).

- **`RefFilePropertyTest.kt`** — property-based round-trip test:
  - `RefEntryGenerator` produces 1-7 random entries per module, mixing file/node/alias/label/database types with safe ASCII strings (1-20 chars).
  - 200 fixed seeds, each generating 1-5 random modules.
  - Encodes generated `RefFile` to bytes per spec (via reused `entry()`/`module()`/`refBytes()` helpers from `RefFileTest`).
  - Parses back and asserts structural equality at module/entry level.
  - Additional smoke tests: empty file parses; single-module two-entry case round-trips exactly.

- **Documentation** (this file + additions to `doc/format-notes.md` and `doc/PROGRESS.md`):
  - `.REF` binary format layout confirmed per spec.
  - `TYPE_EXTERNAL_REF` entry count in `hcp_orig_en.hyp` corrected: 16 (not 18 planned).
  - Three parser design decisions (empty modules, optional terminator, label line-number bytes) recorded with rationale.

## Files touched

**Production:**
- `src/commonMain/kotlin/de/rholambdapi/hypp/RefFile.kt` — new.
- `src/commonMain/kotlin/de/rholambdapi/hypp/IndexEntry.kt` — added `ExternalRef` data class and `externalRef()` function.

**Test:**
- `src/commonTest/kotlin/de/rholambdapi/hypp/RefFileTest.kt` — existing hand-constructed suite (already present before this phase).
- `src/commonTest/kotlin/de/rholambdapi/hypp/RefFilePropertyTest.kt` — new property-based suite.
- `src/commonTest/kotlin/de/rholambdapi/hypp/IndexEntryTest.kt` — existing test of `externalRef()` against real corpus (already present).

**Documentation:**
- `doc/format-notes.md` — appended Phase 12 findings section.
- `doc/progress/phase-12-ref-support.md` — this file.
- `doc/PROGRESS.md` — updated to mark Phase 12 "green".

## Verification

**Test coverage:**
- `RefFilePropertyTest`: 200 fixed seeds, random 1-5 modules × 1-7 entries per module, symmetrical round-trip assertion.
- `RefFileTest`: 13 hand-constructed tests (structure, edge cases, failures).
- `IndexEntryTest`: 4 tests covering `externalRef()` split logic against real corpus.

**Platform targets:**
- `./gradlew jvmTest` — 18 tests pass.
- `./gradlew wasmJsTest` — 18 tests pass.
- `./gradlew wasmWasiTest` — 18 tests pass.

All three targets green, as required.

## Key decisions

1. **No filesystem resolver.** Parser only — no opening/reading referenced `.hyp` files by name. Consumers of `RefFile.find()` results are responsible for locating and opening those files. Out of scope per plan.

2. **No write/round-trip write.** Parsing only. Deferred to the plan's "out of scope" list, unchanged from `doc/PLAN.md`.

3. **Property-based test uses hand-rolled `kotlin.random.Random`** with fixed seeds, consistent with phase-11's existing convention (no test dependency). Generator respects entry-type constraints (labels carry line numbers; file entries come first in sequences for grouping semantics to work).

4. **Parser design choices** (empty modules, optional terminator, literal label-number reading) are conservative, spec-text-literal interpretations. No corpus files exist to confirm against; synthetic tests pin these choices for reproducibility.

## Remaining / deferred (per plan)

- **Phase 13:** Traversal API — `HypDocument.resolve(target: NodeIndex): ResolvedTarget`, dispatching on entry type. Depends on Phase 12 `IndexEntry.externalRef()` (now available).
- **Phase 14+:** CLI, renderers, fat jar — depend on this phase's model but independent of further `.REF` work.
