# Phase 11 — wild sweep

**Status: green.** Model: Sonnet throughout (task + findings interpretation;
no Opus/Haiku available this session, same as phases 8-10).

## What was built

- `HypDocument.kt` refactored (no behaviour change — `clean allTests` green
  before and after on all three targets): the container-decoding portion of
  `open()` (magic check, header, index table, extended headers) factored out
  into `internal fun parseContainer(bytes): RawContainer?`, and the
  compressed/raw-object decode logic factored into
  `internal fun decompressEntry(bytes, entry): ByteArray?`. `open()` now
  calls both. Done so the sweep tool below reuses the exact same
  container-parsing code `open()` uses for its raw-byte scan, rather than
  re-deriving it.
- `src/jvmTest/kotlin/de/rholambdapi/hypp/CorpusSweep.kt` — a `fun main`,
  not a `@Test`, run via the new opt-in `./gradlew corpusSweep` Gradle task
  (`build.gradle.kts`; not wired to `build`/`check`). Downloads the full
  `.hyp` listing from `https://tho-otto.m68k.eu/hypview/` (regex-extracts
  `/hyp/*.hyp` links, 702 found), caches each file under
  `build/corpusSweep/cache/` (gitignored, so reruns after the first are
  network-free), opens every file through the public `HypDocument.open()`,
  and prints a feature histogram plus a targeted raw-byte scan for `ESC
  0xa4` occurrences (using `parseContainer`/`decompressEntry` directly,
  since the public model doesn't expose undecoded node bytes).

## Sweep results (702/702 files, full corpus)

```
files in listing: 702, fetch failures: 0, opened: 702, crashes: 0
open() failures: {}
entry type counts: {0=100486, 1=3972, 2=15643, 3=11302, 4=39, 5=2, 6=420, 7=16}
node kind counts: {POPUP=3972, TEXT=100411}
compiler version counts: {2=140, 3=352, 4=45, 5=161, 6=4}
compiler os counts: {2=692, 5=10}
charset counts: {(default)=565, UTF-8=3, atarist=133, russian-atarist=1}
extended header id counts: {1=682, 2=155, 3=59, 4=701, 5=670, 6=672, 7=227, 8=680, 9=596, 10=140, 11=409, 30=137, 31=132}
diagnostic counts: {DecompressionFailed=76, UnknownEscape=51778, UnsupportedCharset=1, UnterminatedLine=1741}
```

**No crash on any of the 702 files** — the "total parse never fails"
invariant (claimed since phase 1, hardened for short input in phase 9) holds
across the full public corpus, not just the vendored micro-corpus and the
two large hand-picked documents.

## Findings against the plan's open risks

1. **`0xa4` typewriter vs. `0xa5`/`0xa6` colour overlap — resolved.**
   Current zero-parameter implementation confirmed; see
   `doc/format-notes.md`'s new entry for the full evidence (45 occurrences,
   all in one file, all immediately followed by a structural boundary byte,
   never a dispersed numeric-parameter-like spread).
2. **Node type 8 (`TYPE_CLOSE`) — never observed.** Absent from the entry
   type histogram across all 702 files (types seen: 0-7 only, no 8). Nothing
   to resolve; the format constant stays as documented, exercised only by
   hand-constructed tests, same status as before the sweep.
3. **Extended headers 30/31.** Id 30 (`@charset`) is implemented and seen
   137 times, all matching the phase-4 resolution. Id 31 (`@language`) is
   real and common in the wild (132 occurrences) but was already
   out-of-scope for v1 (captured losslessly as `ExtendedHeader.Unknown(31,
   ...)`, per the "nothing discarded silently" design in
   "Accommodating the deferred work"). No action needed; a future language
   feature has real data to build against when it lands.

## Other things the sweep surfaced (not phase-11 blockers)

- **`russian-atarist` charset (1 occurrence)** — an unaliased `@charset`
  name, correctly produces `UnsupportedCharset` rather than crashing or
  guessing. Confirms the phase-4 charset-resolution fallback works on
  real-world data the vendored corpus never exercised.
- **`compilerOs == 5` (10 files)** — a compiler/OS id not seen in the
  micro-corpus (`2` is the overwhelming majority). `Header.compilerOs` is
  stored as a raw `Int` with no enum, so this needed no code change to
  handle; noted here in case a future consumer wants to interpret it.
- **`DecompressionFailed` (76 objects across 702 files)** — a small
  fraction of the corpus's ~130,000 data-bearing entries. Consistent with
  the "total parse never fails" design (the containing document still opens;
  only that one node/image is omitted, recorded as a diagnostic). Not
  investigated further — phase 3's `blockSize == 0` malformed-stream
  rejection is one plausible cause among several (truly corrupt archived
  files are also plausible for a decades-old public corpus); revisit only if
  a real consumer needs those specific nodes.
- **`UnknownEscape` (51,778 across the corpus, concentrated in a subset of
  files)** — most of hypp's understood escape range is exercised
  correctly; some documents evidently use additional formatting escapes
  (layout/indentation-shaped, based on a manual look at a few examples)
  outside the currently-modeled set. Out of scope for phase 11 (which
  targeted the specific `0xa4` ambiguity, now resolved) — a candidate for a
  future phase if a consumer needs those documents to render without
  unknown-escape noise.

## What remains

Nothing planned. Phase 11 was the plan's last phase
(`doc/PLAN.md` § Implementation phases). The two deferred-but-designed-for
items — `.REF` support and write/round-trip — remain deferred, as decided;
see `doc/PLAN.md` § "Accommodating the deferred work".
