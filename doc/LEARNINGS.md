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

## Phase 2

- **Facts hand-derived from the tiny corpus don't always generalize —
  get a real file into the loop immediately.** `empty.hyp`/`textattr.hyp`
  both end their index table with a type-255 EOF sentinel, which is what
  made "itableCount includes a trailing sentinel" look like a safe general
  rule. `hcp_orig_en.hyp` (a real 57 KB document) has no sentinel at all.
  The fix generalizes cleanly (derive each entry's length against the next
  entry's `seek`, or the file's own byte length when there is no next
  entry) but the *only* thing that caught the wrong assumption was the
  phase-2 integration test opening a real file, not the two hand-verified
  unit tests. Lesson: don't treat the plan's own micro-corpus facts as the
  full picture — the "real consumer as early as feasible" rule in
  `doc/PLAN.md` exists precisely because unit tests on tiny fixtures can
  all pass while the general rule they were derived from is still wrong.
  See `doc/format-notes.md` for the full resolution and evidence.
- **The extended-header terminator is a full 4-byte `id=0, length=0` pair,
  not a bare 2-byte `id=0`.** The prose spec's wording ("terminated by id
  0") is genuinely ambiguous about whether the terminator still carries its
  `length` field. Reading it as bare `id=0` leaves reader position short by
  2 bytes, silently misaligning the start of the data region on *every*
  file (both tiny corpus files landed exactly 2 bytes before their first
  entry's recorded `seek`). Lesson: when a spec says a list is
  "terminated by X", check empirically whether the terminator record's
  *shape* is the full record type or a truncated one — don't assume the
  shorter reading. See `doc/format-notes.md`.
- **Multiplatform test-resource loading isn't solved by default.**
  `src/commonTest/resources/` doesn't reliably reach `wasmJs`/`wasmWasi`
  test execution without extra Gradle wiring. For a small corpus, embedding
  bytes as base64 string literals (stdlib `kotlin.io.encoding.Base64`,
  chunked to stay under the JVM class file's 64 KB per-string-constant
  limit) sidesteps the problem entirely and works identically on all three
  targets. Real resource loading is still an open toolchain question for
  whenever a corpus file gets too large to embed this way.

## Phase 3

- **A test that asserts a size the implementation was *told* asserts
  nothing.** lh5 has no end-of-stream marker: the decoder is given the
  uncompressed size and stops when it has produced that many bytes. The
  obvious implementation allocates `ByteArray(uncompressedSize)` up front —
  at which point the plan's red test ("decompresses to exactly 293 bytes")
  passes for *any* body, including an empty one. Fix: make failure
  representable (`decompress` returns `ByteArray?` and yields null unless it
  filled the buffer without reading past the end of the compressed region),
  and add a test that halves the input and demands null. Lesson: before
  writing an assertion, ask what the implementation would have to do to fail
  it. If the answer is "nothing", the assertion is decoration — either
  strengthen it or give the implementation a way to say no.
- **A bit reader over a compressed region needs zero-fill *and* an overrun
  flag.** Zero-fill past the end is required (encoders pad the last byte, and
  a Huffman decode routinely peeks past the symbol it needs), but a reader
  that only zero-fills lets a wrongly-parameterised decoder read an endless
  stream of zero bits and produce plausible garbage instead of failing.
  Tracking "did we cross the end" costs one boolean and turns silent nonsense
  into a clean rejection. Lesson: lenient reads at a buffer boundary are fine
  as long as the leniency is *recorded* somewhere the caller can check.
- **Public secondary sources contradict each other on `-lh5-`'s window size**
  (8 KiB vs 16 KiB — see `doc/format-notes.md`). The corpus settled it in
  seconds, because a wrong window size changes the width of a count field in
  every block header and so nothing decodes at all. Lesson: when independent
  public descriptions of a format disagree, pick the reading that fails
  *loudly* if wrong and let a real file arbitrate — don't spend the round
  hunting for a more authoritative document.
- **Restricting the integration sweep to entries that actually have data is a
  spec decision, not a test convenience.** Types 2 and 4–8 have no object in
  the data region, so their derived `compressedLength` is a meaningless
  difference between two unrelated seek offsets. That rule belongs on the
  domain type (`IndexEntry.hasData`), not as a filter written out longhand in
  the test — otherwise every future consumer re-derives it and one of them
  gets it wrong.
- **Assert the *shape* of a corpus, not just "enough of it".** The first cut
  of the sweep asserted `decompressed > 50`; replacing that with the exact
  per-document counts (106 and 78, of which 2 and 15 are images) turned it
  into a test that also pins the node-type breakdown — and the image counts
  are the first end-to-end check of phase 2's `next`-overload size rule,
  which until now nothing exercised.

## Phase 4

- **A field's *type constant* in `hyp.h` doesn't tell you the field's
  on-disk *shape*.** The plan assumed extended header id 30 (`@charset`)
  held a byte from `hyp.h`'s `HYP_CHARSET` enum. Hex-inspecting the corpus
  showed it's actually a NUL-terminated descriptor string (`"atarist\0"`) —
  the enum is hypview's post-parse internal value, not the file's encoding.
  Lesson: even for the "constants only, in-bounds" parts of `hyp.h`, verify
  against a real file before trusting the shape an identifier's name
  suggests. See `doc/format-notes.md`.
- **When the vendored corpus doesn't exercise every value a field can take,
  say so and cite the fallback source rather than guessing.** No vendored
  `.hyp` file uses a Latin-1 or UTF-8 `@charset`, so their exact alias
  spellings (`"latin1"`, `"utf-8"`, ...) have no corpus evidence — they come
  from the current UDO manual's charset descriptor table, which is public,
  independent of hypview, and authoritative for what the compiler that
  writes this field actually emits. Recorded as such in
  `doc/format-notes.md` rather than silently presented as corpus-verified.
- **A newly-appearing "Internal compiler error" on `wasmJs` after touching
  unrelated code is worth a `clean` before debugging further.** Adding the
  charset code triggered `NoSuchElementException: Key ... indexOf@... is
  missing in the map` deep in `compileTestDevelopmentExecutableKotlinWasmJs`
  — looked like a real regression, but `./gradlew clean` made it disappear;
  it was a stale incremental-compilation cache, not a code issue. Lesson:
  Kotlin/Wasm IC caches are fragile enough that a `clean` build should be
  the first diagnostic step for a wasm-only compiler crash, before assuming
  the new code is at fault.

## Phase 5

- **`@JvmInline` needs an explicit `import kotlin.jvm.JvmInline` (or the
  fully-qualified name) in `commonMain` — it compiles without one on `jvm`
  but fails on `wasmJs`/`wasmWasi` with "Unresolved reference".** The JVM
  target's Kotlin compiler carries extra default imports (`kotlin.jvm.*`)
  that the other targets don't. `NodeIndex.kt` passed `compileKotlinJvm`
  silently and only broke at `compileKotlinWasmJs`. Lesson: a bare
  `@JvmInline` (or anything else from `kotlin.jvm`) in `commonMain` needs
  its import written out explicitly — don't trust a JVM-only compile to
  prove common code is target-clean; run (or at least compile) all three
  targets before considering a round done, not just `jvmTest`.
- **A prose spec's item-by-item enumeration order ("a) ... b) ... c) ...")
  is not necessarily wire order.** `hypfmt.ui` lists a text node's prologue
  as graphics, then cross-references, then data blocks, then window title,
  then object table — but `hcp_orig_en.hyp`'s first node emits its window
  title before its graphics. The fix (parse prologue records as an
  unordered, self-identifying set, stopping at the first byte that isn't a
  recognized prologue escape) turned out to be more robust anyway. Lesson:
  when a format's records are individually self-identifying (each carries
  its own type tag), don't assume a spec's listing order is a parsing
  order — check a real file before hand-deriving field offsets from
  enumeration position, the same lesson as phase 2/4's "verify against a
  real file" but for structural order rather than field shape or byte
  encoding.
- **When a prose spec describes a byte-avoidance encoding scheme without
  giving the arithmetic, work it out from the scheme's stated purpose
  before reaching for the GPL-licensed reference implementation.**
  `hypfmt.ui` says base-255 fields are "present to a base of 255 and a
  value of 1 is added to both bytes" — enough to derive
  `(hi - 1) * 255 + (lo - 1)` directly (a two-digit base-255 number, each
  digit biased +1 to avoid a zero byte) without opening `hyp.h`'s
  `dec_from_chars` at all. Corpus values (image indices, cross-reference
  targets) confirmed it first try. Lesson: a prose spec's plain-English
  description of *why* an encoding exists is often enough to derive the
  arithmetic clean-room; reach for the constants-only reference file only
  when the prose is silent, not as the first move.
- **Don't force a real corpus's descriptive filenames to validate a bit
  layout the file itself doesn't actually let you check.** `lines.hyp`
  names its ten line placements after what they're supposed to visually
  show ("arrow end", "arrow start", "both arrows", ...), which looks like
  free validation for the prose spec's bit0/bit1/rest decomposition of the
  line-graphic data byte — but the decoded flags don't line up with the
  labels (both "arrow end" entries and both diagonal entries decode
  identically; "both arrows" decodes to no arrow flags at all). There's no
  rendering oracle to arbitrate, so the right move was to implement the
  spec's stated bit layout literally, assert the literal decoded values,
  and record the mismatch rather than reverse-engineer a reinterpretation
  to make the labels fit. Lesson: a corpus filename is a hint, not
  evidence — only trust a real file's *bytes* as evidence, never a
  human-chosen name attached to them, unless there's an independent way
  (a working renderer, a second independent spec) to confirm the human's
  intent actually matches the encoding.
