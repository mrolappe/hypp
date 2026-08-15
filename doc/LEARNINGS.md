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

## Phase 6

- **A format's "no NUL bytes anywhere" invariant can have exceptions, and the
  exception is where the parser breaks.** Every multi-byte inline value in
  node data is base-255 with +1 on both bytes *specifically* so no NUL
  appears — which makes "split the text region on NUL, then interpret the
  escapes inside each line" look obviously safe. It isn't: the `0xa5`/`0xa6`
  colour escapes carry a **raw** palette index, and index 0 (white) is a
  literal `0x00`. `colors.hyp`'s first three NUL-delimited fragments are each
  a mid-parameter cut. Fix: consume every escape *and its parameter* before
  testing the next byte for the terminator. Lesson: when a format states an
  invariant like "no NUL bytes", check whether *every* field actually obeys
  it before building a two-pass parser on top of it — a single field that
  doesn't makes the cheap pass silently wrong, and it will be the one field
  the spec forgot to document.
- **Derive an undocumented encoding from a fixture whose *content* names its
  own expected values.** Neither `hypfmt.ui` nor `hyp.h` says anything about
  the colour escapes' parameter. `colors.hyp` settles it in one read because
  each line's text is the name of the colour that line's escape selects
  (`hello dark cyan world` next to index 13) — sixteen independent
  confirmations of both the parameter width and the index numbering, from
  data alone. Lesson: when a corpus file exists purely to exercise one
  feature, look at what its text *says* before reaching for more spec —
  a self-describing fixture is a stronger oracle than prose, and unlike
  `lines.hyp`'s filenames (phase 5) the evidence is in the bytes being
  parsed, not in a human-chosen name attached to them.
- **An entry with `compDiff == 0` is stored uncompressed — and only a fixture
  outside the two big documents showed it.** `linkattr.hyp`'s three short
  nodes hold their text verbatim at `seek`, with no lh5 framing; phase 3's
  sweep over `hcp_orig_en.hyp` and `st-guide_orig_en.hyp` never saw the case
  because neither document contains one. They surfaced as three
  `DecompressionFailed` diagnostics that were easy to read as "phase 3 has a
  bug" or to ignore as noise. Lesson: treat a diagnostic that only some
  fixtures produce as a finding to chase, not as an accepted cost — and don't
  read "the two real documents pass" as proof a rule generalises, which is
  phase 2's lesson pointing the other way (a big real file caught what the
  micro-corpus missed; here the micro-corpus caught what the big files
  missed). Both directions are needed.
- **A total parser can still throw, via a `require` in a value class.**
  `NodeIndex`'s `require(value >= 0)` turns any malformed base-255 field into
  an exception, defeating the "total parse + typed diagnostics" decision at
  three call sites (link target, cross-reference target, image index). Fixed
  once, in a shared local helper that returns `NodeIndex?` and records
  `DanglingNodeReference`, rather than at the one call site this phase added.
  Lesson: making illegal states unrepresentable puts a throw at every
  construction site — audit those sites whenever the constructor sits on a
  path fed by untrusted bytes.
- **When a parser bails out of one region, make sure it doesn't reinterpret
  the same bytes as the next region.** Phase 5's prologue recorded
  `NodeDataOverrun` and stopped, leaving `pos` where the truncated record
  began — harmless while the remainder was an opaque `textBytes`, but as soon
  as phase 6 started parsing that remainder, one malformed prologue byte
  produced a cascade of bogus `UnknownEscape` and `UnterminatedLine`
  diagnostics. Fix: advance to the end of the data on that path. Lesson: an
  error exit needs to leave the cursor somewhere the *next* stage can safely
  start from, not merely stop the current one — and an existing test that
  asserts an exact diagnostic list is what catches it.
- **Write an accented or control character into a test as a `\uXXXX` escape,
  not as the literal character.** Two assertions in this round's tests ended
  up holding a raw `0x1b` byte and a raw high-Latin byte in the Kotlin
  source, which then defeated exact-string edits (the tooling can't match
  what it can't see) and would have been invisible in review. Lesson: any
  test asserting a decoded character belongs in escape form in the source —
  it is the only form that survives a diff, an editor and a grep unchanged.

## Phase 7

- **A spec's own "(will be ignored)" annotation can be wrong about which
  reader should ignore the field.** `hypfmt.ui` marks the image object
  header's `width`/`height` "(will be ignored)", which reads as "don't trust
  these on decode" — but they're exactly right, confirmed by an exact
  byte-count match (`ceil(width/16)*2*height` equals the plane data length)
  across all four vendored images, and then by decoding a full image through
  them into a legible logo. Most likely the note means "ignored/regenerated
  by the compiler when it writes the file", not "unreliable to read". Lesson:
  an explicit "ignored" annotation is still a claim to verify against real
  bytes, not a instruction to skip the field — the same "verify against a
  real file" discipline as every prior phase, now applied to a spec that
  actively tells you not to bother.
- **Rendering a real image end-to-end is a stronger oracle than any unit
  assertion for a format with no ground truth in the corpus.** There's no
  documented RGB palette and no independently-known "correct" pixel array to
  assert against — but decoding `image.hyp`'s `rtr_logo.img` through the
  header, row-byte formula and bit order into a hand-rolled BMP produced a
  legible "Ardi Soft" logo (the compiler's own vendor name). A wrong bit
  order, wrong row-byte formula, or transposed width/height would have
  produced visible noise or a garbled image, not something that happens to
  spell out a real name. Lesson: when a format has no per-field oracle, build
  the actual consumer (`hyp2html`'s embedded image) before trusting synthetic
  unit tests alone — a real decoded artifact that makes human-recognisable
  sense is evidence no hand-picked assertion can substitute for.
- **A field with no corpus example at all still needs a test — write the
  bytes by hand.** No vendored image has `planeCount > 1` or any
  `planeOnOff`/`planeFilled` bit set, so "a plane marked filled expands
  without being present in the data" (an explicit phase-7 Red requirement)
  has no real file to assert against. Fix: hand-construct the header and
  plane bytes directly in the test, matching phase 5's lesson about not
  forcing corpus filenames to stand in for missing evidence — the synthetic
  test is honestly labelled as spec-literal, not corpus-confirmed, and the
  gap is recorded in `doc/format-notes.md` for the wild sweep to eventually
  fill in.

## Phase 9

- **Kotlin/Wasm's `@JsExport` (2.4.10) accepts only primitive, `String`, `external` and function
  types — no arrays, no exported classes — for *both* parameters and return types.** The plan's
  domain model and `HyppJs.kt`'s design were sketched before this was checked against the real
  toolchain. A `ByteArray` parameter, an `IntArray`/`Array<String>` return, and a plain
  `@JsExport`-annotated class each failed to compile with "Only external, primitive, string, and
  function types are supported in Kotlin/Wasm JS interop" — the class case even more bluntly
  ("This annotation is not applicable to target 'class'"). Fix: bytes go in as a base64 `String`
  (`hyppOpen`), state lives behind an opaque `Int` handle into a module-level map (no exported
  object to return one from), and "flattened arrays" become a `*Count` function plus indexed
  getters — the same C-style flat-array idiom, just enforced by the platform rather than chosen by
  taste. Lesson: the "verify against a real file" discipline every earlier phase applied to the
  file format applies equally to a plan's *toolchain* assumptions — a spike that just tries to
  compile the shape the plan describes, before committing to an API design, would have caught this
  in one Gradle run instead of after the model was already sketched.
- **`HypDocument.open` threw `IndexOutOfBoundsException` on input under 4 bytes, silently
  violating the "total parse never fails" invariant the model has claimed since phase 1.** Every
  corpus fixture is a well-formed file at least as long as the header, so nothing had ever called
  `open()` with genuinely too-short input — until `HyppJsTest`'s failure-path test did, since the
  JS façade is the first place arbitrary-length, caller-controlled bytes actually reach `open()`.
  Fixed once in `HypDocument.open` itself (`bytes.size < 4` → `OpenFailure.InvalidMagic`, before
  the first read), not at the `hyppOpen` call site — the same "fix it where all callers route
  through" lesson as phase 6's `NodeIndex` throw, now surfacing at the container level instead of
  node parsing. The rest of the container reader (index table, extended headers) has the same
  unaudited-truncation shape and is not yet hardened; deferred to phase 11's wild sweep rather than
  fixed speculatively here, since nothing has yet demonstrated it broken.
- **Two Kotlin/Wasm library-distribution flavours (`development`/`production`) write the same
  output directory, and Gradle's task-validation rejects running both without a declared
  ordering.** A custom task depending on `wasmJsNodeDevelopmentLibraryDistribution` fails
  `./gradlew build` once `assemble` also pulls in `compileProductionLibraryKotlinWasmJs` for
  `wasmJsJar` — both write `build/wasm/packages/<module>/kotlin`. Fix: point the custom task at
  the *production* distribution instead, reusing a task `build` already runs rather than adding a
  second, conflicting one. Lesson: when adding a task that consumes a Kotlin/Wasm distribution
  output, run `./gradlew build` (not just the new task in isolation) before considering it done —
  the conflict only appears once both flavours are in the same task graph.

## Phase 10

- **A brand-new `jvmTest` source set needs no explicit wiring to see `commonTest`'s `internal`
  declarations.** `TestCorpus` (in `commonTest`) is `internal`, and `ParityGoldenTest` (the first
  file ever placed under `src/jvmTest/`) reads it directly with no import/visibility error — Kotlin
  Multiplatform's `jvm()` target compiles `commonTest` + `jvmTest` into one JVM test compilation
  unit, so `internal` visibility is shared automatically once the directory exists. Nothing to add
  to `build.gradle.kts` beyond creating the folder.
- **A Gradle `Test` task's working directory is the module's project directory by default** (not
  the module's `build/` output). A relative `File("doc/goldens/$name.json")` in
  `ParityGoldenTest.kt` resolves correctly with zero configuration — confirmed by running the test
  before the goldens existed (clean failure: "missing golden") and after. Worth stating explicitly
  since it's easy to assume a test's CWD needs `System.getProperty("user.dir")` juggling or an
  explicit Gradle `workingDir` override when it doesn't.

## Phase 11

- **A raw-byte scan restricted to the wrong entry types drowns the signal in noise.** The first
  pass of the `ESC 0xa4` occurrence scan (for the typewriter-vs-colour ambiguity) matched every
  entry with data, including images — raw bitplane bytes coincidentally contain the two-byte
  sequence `0x1b 0xa4` often enough (122 hits across the corpus) to swamp the real signal.
  Restricting to text/popup entries only (the only ones actually escape-parsed) dropped the count
  to 45, all in one file, and turned a noisy result into a decisive one. Lesson: when scanning
  decompressed bytes independently of the structured parser, the entry-type filter that the
  structured parser applies implicitly (only types 0/1 go through `parseNode`) has to be applied
  explicitly too — "has a compressed object" (`IndexEntry.hasData`) and "is escape-parsed text"
  are different predicates, and phase 3's `hasData` was the wrong one to reach for here.
- **Reuse the production container-parsing code for investigative tooling, don't re-derive it.**
  The wild-sweep tool needs undecoded per-node bytes, which the public `HypDocument` model
  deliberately doesn't expose. Rather than hand-rolling a second index-table/header reader in the
  sweep tool (drifting risk, and exactly the "reinventing what's a few files over" trap), the
  relevant slice of `HypDocument.open()` was factored into two internal functions
  (`parseContainer`, `decompressEntry`) that both `open()` and the sweep tool call. `clean
  allTests` green before and after the extraction was the confirmation nothing changed behaviourally.
- **A histogram's most useful signal is often "which bucket is near-empty," not the biggest bucket.**
  The `0xa4` question was settled not by a large occurrence count but by the *opposite* — only 45
  hits in 702 real files, confined to one document — combined with a tight, four-value
  following-byte distribution ({ESC, space, `#`, NUL}) instead of the broad 0-15 spread a genuine
  parameter byte would produce (contrast `colors.hyp`'s fg/bg parameter, phase 6). Lesson: when
  gathering wild-corpus evidence for a format ambiguity, print the *distribution shape*, not just
  a total count — a parameterless code and a parameterised one are told apart by how spread out the
  "next byte" values are, not by how often the code appears.
