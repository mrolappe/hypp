# hypp — Kotlin Multiplatform library for Atari ST `.HYP` (ST-Guide) documents

> **Status: approved, not started.** Produced by a `/grill-me` interview
> session on 2026-08-15. Round 1 (phase 1) begins only on an explicit
> go-ahead in a new session — see "Start condition" at the end of this file.
> This copy is the durable record; the original lives at
> `~/.claude/plans/i-want-to-create-mutable-bengio.md`.

## Context

`~/studio/hypp` is an empty git repo. The goal is a library that parses ST-Guide
`.HYP` hypertext files and exposes a **rich, enumerable object model** — nodes,
lines, styled spans, links, images, graphic placements — that is comfortable to
use from Kotlin compiled to WebAssembly.

Why a new library rather than extending what exists: the current
`hyp-parser` (Codeberg, vendored as a submodule into `ij-hyp` and
`kmp-hyp-ag-view`) is **Kotlin/JVM only**, so `kmp-hyp-ag-view` can only consume
it from `desktopMain` — its `wasmJs` target is commented out precisely because
of this. That parser also stops short of the actual goal: node content is
`data class Node(name, content: List<String>)` (raw lines) and the only consumer
is `htmlFromNodeContent(...): String`. There is no span, link, or image model at
all. hypp builds that missing layer, as a proper KMP library.

**hypp is written clean-room from the format specification.** No code is taken
from `hyp-parser`, `hypview`, or any other project under `~/studio`.

A Rust implementation sharing the same domain abstractions is intended later.
v1 pays a small, deliberate cost to make that port verifiable rather than
aspirational.

---

## Locked decisions

| Decision | Choice |
|---|---|
| Language / v1 | Kotlin Multiplatform first; Rust port later |
| Targets | `jvm`, `wasmJs`, `wasmWasi` (Android consumes the `jvm` artifact) |
| Module shape | One module; `@JsExport` façade confined to `wasmJsMain` |
| Dependencies | **None** in `commonMain` — Kotlin stdlib only |
| Text model | Flat span runs: `Line.spans: List<Span>`, `Span(text, style, link?)` |
| Charset | Decoded to `String` at parse time; v1 ships Atari ST (default), Latin-1, UTF-8 |
| Images | Bitplanes → indexed pixels + palette, with `toRgba()`; decoded lazily |
| Eagerness | Text nodes eager and immutable; image pixels lazy + memoised |
| Link refs | Node **indices** + document accessors — acyclic value tree |
| Errors | Total parse + typed `Diagnostic` sealed hierarchy (no message strings) |
| Verification | Vendored micro-corpus with hand-written assertions + opt-in 704-file wild sweep |
| Rust parity | Markdown model spec + canonical JSON golden dump from tests |
| Distribution | `maven-publish` → `mavenLocal`; consumers use submodule + `includeBuild` |
| Licence | Apache-2.0 |
| Scope | Read-only `.HYP`; `.REF` and write/round-trip accommodated architecturally, not implemented |
| Method | **TDD — test-first, every phase, no exceptions** |
| Remote | `github.com/mrolappe/hypp` (repo does not exist yet; created in round 1) |
| Round end | Record progress + learnings → commit → push → **stop** |

---

## Specification sources (in-bounds)

1. `~/git-repos/hypview/doc/en/hypfmt.ui` (298 lines) and `doc/de/hypfmt.ui` —
   the original HCP "Technical" chapter. **Primary source.**
2. `~/git-repos/hypview/include/hyp.h` — constants only (escape codes, charset
   ids, extended-header ids, node types, limits). Facts, not expression.
3. Web: `https://tho-otto.m68k.eu/hypview/` — the mirror. `reflink_orig_en.hyp`
   there is the `.REF` format spec (for the deferred phase). Render any `.hyp`
   as HTML via `hypview.cgi` when a construct needs clarifying.

**Out of bounds:** every `.c` file in hypview, and all `~/studio` project code.

### Format facts already established

Verified empirically against `tests/textattr.hyp`:

```
0..11    header            'HDOC', itableSize:u32, itableCount:u16, compilerVer:u8, compilerOs:u8
12..     index table       itableCount entries, immediately after the header
         extended headers  id:u16, length:u16, data[]  — terminated by id 0
         data region       lh5-compressed objects at each entry's seek offset
```

- `itableCount` **includes a trailing type-255 (EOF) sentinel** whose `seek`
  equals the file length. This is what makes the derived length rule work.
- Object length is derived: `seek[i+1] − seek[i]`. There is no length field.
- Index entry: `len:u8, type:u8, seek:u32, compDiff:u16, next:u16, prev:u16,
  toc:u16, name:cstring`, **padded to an even total length**.
- For **image** entries `next` is overloaded as size bits:
  `uncompressed = compressed + (next << 16) + compDiff`. `next` is therefore
  *not* a navigation link on image entries and must not be exposed as one.
- Multi-byte inline values are base-255 with **+1 added to both bytes**
  ("dec255"), so no NUL bytes appear in node data.
- Node types: 0 internal, 1 popup, 2 external ref, 3 image, 4 system, 5 rexx
  script, 6 rexx command, 7 quit, **8 close**, **255 EOF**. Types 2 and 4–8 have
  **no data in the data region**.
- Extended header ids 0–11 are in the prose; **30 = charset** and
  **31 = language** are in `hyp.h` only. Unknown ids must be skipped silently
  (explicit spec requirement).

### Node data layout — strict prologue, then text

| | escape | content |
|---|---|---|
| a | `0x32`–`0x35` | graphics: image / line / box / rounded box — x, y, width, height in **character cells**; `x == 0` means centred |
| b | `0x30` | up to 12 cross-reference blocks |
| c | `0x28`–`0x2f` | data blocks; `0x2f` is the dithermask and immediately precedes its image |
| d | `0x23` | window title, NUL-terminated, padded to even |
| e | `0x31` | object table (`@tree`) |
| f | — | text: NUL-terminated lines |

Within text: `ESC ESC` = literal ESC · `0x24`/`0x25` link, link+line ·
`0x26`/`0x27` alink, alink+line · `0x64`–`0xa3` **absolute** attribute
bit-vector (`code − 0x64`: 1 bold, 2 light, 4 italic, 8 underlined, 16 outlined,
32 shadowed) · `0xa4` documented as unknown/no visual effect · `0xa5`/`0xa6`
fg/bg colour (16-colour palette).

Links carry their own display text: a length byte of `32 + n`; when it is
exactly 32, the target's node name is used instead. A link therefore never
straddles a style change — which is why flat spans suffice.

**Known ambiguity to take a position on:** `hyp.h` says typewriter is `0xa4`
with range `0xa4`–`0xe3`, yet fg/bg colour are `0xa5`/`0xa6` inside that range.
Resolve empirically via the wild sweep; record the decision in the model spec.

---

## Domain model (`commonMain`, package `de.rholambdapi.hypp`)

Sketch, not final signatures.

```kotlin
class HypDocument {
    val header: Header
    val extendedHeaders: List<ExtendedHeader>   // sealed; Charset/Language included
    val charset: HypCharset
    val entries: List<IndexEntry>               // file order, EOF sentinel excluded
    val nodes: List<Node>                       // fully parsed, types 0 and 1
    val images: List<ImageNode>                 // type 3
    val diagnostics: List<Diagnostic>

    fun entry(index: NodeIndex): IndexEntry?
    fun node(index: NodeIndex): Node?
    fun image(index: NodeIndex): ImageNode?
    val defaultNode: NodeIndex?
    fun tableOfContents(): TocEntry             // derived on demand

    companion object { fun open(bytes: ByteArray): OpenOutcome }
}

sealed interface OpenOutcome {
    data class Success(val document: HypDocument) : OpenOutcome
    data class Failure(val reason: OpenFailure) : OpenOutcome   // typed, not a string
}

data class Node(
    val index: NodeIndex, val name: String, val kind: NodeKind,   // TEXT | POPUP
    val windowTitle: String?,
    val graphics: List<Graphic>,
    val crossReferences: List<CrossReference>,
    val dataBlocks: List<DataBlock>,
    val objectTable: List<ObjectTableEntry>,
    val lines: List<Line>,
)

data class Line(val spans: List<Span>)
data class Span(val text: String, val style: TextStyle, val link: Link?)

@JvmInline value class TextStyle(val bits: Int) {   // 0..5 attrs, 8..11 fg, 12..15 bg
    val isBold: Boolean; val isLight: Boolean; val isItalic: Boolean
    val isUnderlined: Boolean; val isOutlined: Boolean; val isShadowed: Boolean
    val foreground: HypColor; val background: HypColor
    companion object { val Normal: TextStyle }
}

data class Link(val kind: LinkKind, val target: NodeIndex,
                val lineNumber: Int?, val label: String)   // LINK | ALINK

sealed interface Graphic {                         // x/y/width/height in char cells
    data class Image(...) : Graphic                // + imageIndex, centred, ditherMask
    data class Line(...) : Graphic                 // + arrowAtStart/End, lineStyle
    data class Box(...) : Graphic                  // + fillPattern
    data class RoundedBox(...) : Graphic
}

class ImageNode(val index: NodeIndex, val name: String,
                val width: Int, val height: Int,
                val planeCount: Int, val planePresent: Int, val planeFilled: Int) {
    val pixels: ByteArray                          // lazy, memoised; one palette index/pixel
    fun toRgba(palette: Palette = Palette.AtariSt): ByteArray
}

sealed interface Diagnostic {
    val location: Location                         // nodeIndex? + byteOffset
    data class UnknownEscape(val code: Int, ...) : Diagnostic
    data class UnknownExtendedHeader(val id: Int, val length: Int, ...) : Diagnostic
    data class UnsupportedCharset(val id: Int, ...) : Diagnostic
    data class DanglingNodeReference(val target: Int, ...) : Diagnostic
    data class NodeDataOverrun(...) : Diagnostic
    data class DecompressionFailed(...) : Diagnostic
    data class UnterminatedLine(...) : Diagnostic
    data class CrossReferenceLimitExceeded(val count: Int, ...) : Diagnostic
}
```

**Invariants:** the graph is acyclic — links hold indices, never `Node`
references. Nothing in the model holds a reference to the source `ByteArray`
except `ImageNode`'s undecoded planes. All declared sizes are bounds-checked
against the actual file length before any allocation, so a hostile file cannot
exhaust a wasm heap.

---

## Files

```
hypp/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  LICENSE                                   Apache-2.0
  README.md
  doc/model-spec.md                         source of truth for the Rust port
  doc/format-notes.md                       spec gaps + resolutions, with evidence
  doc/PROGRESS.md                           overall index, links to each phase file
  doc/progress/phase-01-skeleton.md         one file per phase
  doc/progress/phase-02-container.md        ...
  doc/LEARNINGS.md                          errors, mitigations, fixes, learnings
  src/commonMain/kotlin/de/rholambdapi/hypp/
    HypDocument.kt  Header.kt  IndexEntry.kt  ExtendedHeader.kt
    Node.kt  Line.kt  Span.kt  TextStyle.kt  Link.kt  Graphic.kt
    ImageNode.kt  Palette.kt  Diagnostic.kt  NodeIndex.kt
    internal/ByteReader.kt                  big-endian + dec255 reads
    internal/Lh5.kt                         lh5 decompressor
    internal/NodeDataParser.kt              prologue + text/span assembly
    internal/charset/AtariSt.kt  Latin1.kt  Utf8.kt
  src/wasmJsMain/kotlin/de/rholambdapi/hypp/js/
    HyppJs.kt                               @JsExport flattened façade
  src/commonTest/kotlin/...                 assertions + canonical JSON writer
  src/commonTest/resources/corpus/          vendored micro-corpus (<100 KB)
  src/commonTest/resources/golden/          canonical JSON goldens
```

Toolchain: Gradle 8.8 is installed (sdkman), JDK 21 default; pin the wrapper and
use a current Kotlin 2.x — `wasmWasi` requires Kotlin 2.0+.

---

## Working method

### TDD — test-first, without exception

Every phase runs Red → Green → Refactor:

1. **Red.** Write the failing test *first*, with expected values derived from
   the specification or from hex-inspecting the corpus file — never from what
   the implementation happens to produce. Run it, watch it fail for the right
   reason. A test that passes before the implementation exists is a bug in the
   test.
2. **Green.** Smallest implementation that passes.
3. **Refactor.** Only with tests green.

The corpus files are tiny by design (76 B – 1.6 KB), so expected values are
genuinely hand-derivable. **Golden files are never regenerated to make a test
pass** — a golden changes only when a spec reading changes, and that change gets
recorded in `doc/format-notes.md` with its evidence.

### Integration tests, as early as feasible

Unit tests alone will produce a parser that is correct in pieces and unusable as
a whole. Every phase's output must acquire a **real consumer** — something that
drives the public API end-to-end over a real document — at the earliest point
that is possible, not at the end.

The standing integration suite runs on **all three targets** (`jvm`, `wasmJs`,
`wasmWasi`), because a parser that only works on the JVM misses the entire
point of the library.

| From phase | Real consumer that must exist and stay green |
|---|---|
| 2 | `HypDocument.open()` over the full 56 KB `hcp_orig_en.hyp` — every index entry enumerated, every offset in bounds, no diagnostics beyond the expected set |
| 3 | Every text node in `hcp_orig_en.hyp` and `st-guide_orig_en.hyp` decompresses to its derived length — the whole corpus, not one node |
| 6 | **`hyp2text` renderer** in `commonTest`: walks documents → nodes → lines → spans → links and emits plain text. ~100 lines, and it is the first thing that would expose an awkward API. Snapshot-tested. |
| 7 | `hyp2html` extension of the same renderer, emitting styled spans and embedding images as data URIs — this is also the artefact used for the by-eye cross-check against `hypview.cgi` already in the plan |
| 9 | A JS-side test that calls the `@JsExport` façade from JavaScript on `wasmJs` and reconstructs a node's text from the flattened arrays. The façade is untested until something outside Kotlin calls it. |
| 10 | Golden JSON produced by walking the public API only — never internals |
| 1, revisited | `publishToMavenLocal`, then a throwaway consumer project resolving the published artifact and opening a document. Verifies the KMP variant metadata, which nothing else does. |

`hyp2text` / `hyp2html` are deliberately test-source-set utilities, not a
published demo module: they are consumers, not product, and they earn their keep
twice by also serving the cross-check.

### Model assignment

Each task names the model that should implement it. Tasks are split so the
expensive model is spent only where format ambiguity or algorithmic subtlety
actually lives; transcription, wiring and boilerplate go to cheaper models.

| Model | Used for |
|---|---|
| **Opus** | Format ambiguity, algorithms, model/API design, interpreting corpus evidence |
| **Sonnet** | Parsing against a layout that is already pinned down, build config, Gradle tasks |
| **Haiku** | Table transcription, mechanical flattening, boilerplate, doc scaffolding |

### Delegation rules

When handing a task to a cheaper model, supply **only** what that task needs,
stated precisely:

- The byte layout or escape semantics for *that* task, quoted exactly — not a
  pointer to "the spec".
- The exact expected values for the test, and which corpus file they come from.
- The signatures of existing types it must use, not the whole model.
- The current `doc/LEARNINGS.md` entries (always — see below).

Do **not** pass: the whole plan, unrelated phases, or "read the codebase and
figure it out". If a task cannot be specified that concisely, it is not yet
broken down far enough — or it belongs to Opus.

### Round protocol

A round is one phase (or one clearly-bounded part of a phase). At the end of
every round, in this order:

1. Append to `doc/progress/phase-NN-<name>.md`: what was completed, tests added
   and their status, decisions taken, what remains.
2. Update `doc/PROGRESS.md` — the overall index linking every phase file, with
   each phase's state (not started / in progress / green).
3. Append to `doc/LEARNINGS.md` any error hit, its cause, the mitigation or fix,
   and the generalisable lesson. Format each entry so it is directly actionable
   by a later implementer.
4. Commit and push.
5. **Stop.** Do not roll into the next phase.

`doc/LEARNINGS.md` is read at the start of every round and included verbatim in
every delegated task prompt, so a mistake made once is not repeated by a fresh
context. Round 1 creates all three documents.

---

## Implementation phases

Each phase ends green before the next begins. "Red" lists the tests written
first; "Green" the implementation they drive.

**1 — Skeleton.** *(Sonnet)*
Red: a trivial test compiled and run on all three targets.
Green: KMP module with `jvm`/`wasmJs`/`wasmWasi`, `maven-publish` to
`mavenLocal`, Apache-2.0 `LICENSE`, wrapper pinned. Create the GitHub repo, add
`origin`, first push. Scaffold `doc/PROGRESS.md`, `doc/progress/`,
`doc/LEARNINGS.md` *(Haiku)*. Proves the wasm targets build **before** any
parser exists — the cheapest possible place to discover a toolchain problem.

**2 — Container.** *(Sonnet)*
Red: assert against `textattr.hyp` the exact table verified in this plan —
itableSize 34, itableCount 2, entry 0 `len=20 type=0 seek=110 diff=174
name="Main"`, entry 1 the type-255 sentinel at `seek=229`; and `empty.hyp`
(76 B) yields zero real nodes.
Green: `ByteReader` (big-endian + dec255), header, index table with even
padding and sentinel handling, extended headers with silent skip-unknown,
derived object lengths, the image `next`-overload size rule.
Delegate with: the byte-layout block from this plan and those expected values.

**3 — lh5 decompression.** *(Opus)*
Red: decompressing "Main" from `textattr.hyp` yields exactly 293 bytes
(119 compressed + compDiff 174).
Green: lh5 decoder written from public LHA `-lh5-` documentation.
**The single largest risk in the plan** — not splittable, and not delegable to a
cheaper model. Sub-task *(Sonnet)*: the bit-reader, specified independently and
tested on its own before the Huffman/sliding-window logic lands.

**4 — Charsets.** *(Haiku for tables, Sonnet for wiring)*
Red: known accented strings from the German documents decode correctly;
an unknown charset id produces `UnsupportedCharset`, not a failure.
Green: Atari ST / Latin-1 / UTF-8 tables, `@charset` selection, Atari ST as the
default when the header is absent. Table transcription is pure data entry —
give Haiku the source table and the target array shape, nothing else.

**5 — Node prologue.** *(Sonnet)*
Red: `image.hyp`, `limage.hyp`, `limage2.hyp`, `lines.hyp` — graphic
placements with expected x/y/width/height, the dithermask block preceding its
image, `x == 0` meaning centred.
Green: graphics, cross-references, data blocks, window title, object table.
Delegate with: prologue table (a–e) and escape codes `0x23`, `0x28`–`0x35`.

**6 — Text and spans.** *(Opus)*
Red: `textattr.hyp`, `colors.hyp`, `linkattr.hyp` asserted **span by span** —
exact text, exact `TextStyle`, exact `Link`.
Green: line splitting, `ESC ESC`, absolute attribute vectors, fg/bg colour,
link/alink with and without line numbers, the `32 + n` label rule including the
"exactly 32 → use target node name" case.
This is the library's whole point and where the format's subtleties concentrate;
keep it on Opus.

**7 — Images.** *(Sonnet)*
Red: `image.hyp` decodes to expected dimensions and a known pixel run; a plane
marked in `planeFilled` expands correctly without being present in the data.
Green: planar → indexed pixels honouring `planePresent`/`planeFilled`, ST
palette, `toRgba()`, lazy + memoised.

**8 — Document API.** *(Opus for shape, Sonnet for fill-in)*
Red: accessors resolve, a link to a type-7 quit entry resolves to its index
entry rather than failing, TOC nests as expected.
Green: `entry`/`node`/`image`, `defaultNode`, derived TOC from
`toc`/`next`/`prev` plus extended header 9, diagnostics collection.

**9 — JS façade.** *(Haiku)*
Red: an exported call from JS returns the flattened arrays for a known node.
Green: `@JsExport` flattening of spans, sealed graphics and diagnostics into
tag-int + array form, in `wasmJsMain` only. Mechanical once the model is fixed —
give Haiku the model signatures and the flattening convention.

**10 — Parity artefacts.** *(Opus for the spec, Haiku for the writer)*
Red: golden JSON round-trips stably for the whole micro-corpus.
Green: `doc/model-spec.md` as the source of truth for the Rust port (types,
variants, field names, invariants, diagnostic codes); canonical JSON writer in
`commonTest`; goldens checked in.

**11 — Wild sweep.** *(Sonnet for the task, Opus for the findings)*
Green: opt-in Gradle task downloading the 704 corpus files, asserting no crash
and emitting a feature histogram (escape codes, charsets, node types, compiler
versions). Opus then interprets it to resolve the `0xa4` typewriter vs
`0xa5`/`0xa6` colour overlap, and records the resolution with its evidence in
`doc/format-notes.md`.

### Accommodating the deferred work

Not implemented, but not designed out:

- **`.REF`** — keep `HypDocument` free of any assumption that a link target is
  in *this* document; `Link.target` is already an index paired with the entry's
  type, and external-reference entries (type 2) carry their name. A future
  `RefFile` resolves names across documents without changing the model.
- **Write / round-trip** — parsing preserves everything needed to re-emit:
  unknown extended headers keep their raw bytes, unknown escapes are recorded
  as diagnostics with byte offsets, data blocks keep raw payloads. Nothing is
  discarded silently. Writing then needs an lh5 *compressor* and an emitter, but
  no model change.

---

## Verification

- `./gradlew build` — compiles and tests on `jvm`, `wasmJs`, `wasmWasi`.
- `./gradlew jvmTest` — micro-corpus assertions and golden JSON comparison.
- `./gradlew wasmJsTest wasmWasiTest` — same suite, proving the parser is
  genuinely platform-free.
- The integration suite (table above) runs as part of the same targets: real
  documents opened end-to-end, `hyp2text`/`hyp2html` snapshots, and the
  JavaScript-side façade test on `wasmJs`.
- `./gradlew corpusSweep` (opt-in, network) — all 704 documents parse without
  crashing; prints the feature histogram.
- `./gradlew publishToMavenLocal`, then point `ij-hyp` at it via submodule +
  `includeBuild` and confirm it resolves.
- Spot-check a rendered node against `hypview.cgi` output **by eye** — as a
  sanity check on the clean-room reading, not as an automated oracle.

## Risks

1. **lh5 decompression (phase 3)** — the one component that is genuinely hard
   and cannot be validated incrementally. Mitigated by the 293-byte checkpoint
   on a 229-byte file.
2. **Atari ST charset table** — a wrong table produces plausible-looking but
   subtly wrong text. Verify against known strings in the German documents.
3. **Undocumented corners** — `0xa4` typewriter vs `0xa5`/`0xa6` colour overlap,
   node type 8, extended headers 30/31. The wild sweep is the mitigation;
   until it runs these are diagnostics, not failures.

---

## Start condition

Approval of this plan does **not** start implementation. Round 1 (phase 1)
begins only on an explicit separate go-ahead.

---

## Appendix: exact fetch locations (discovered, not yet vendored)

Base URL: `https://tho-otto.m68k.eu` (mirror of the late Thorsten Otto's
hypview web service; `hyptestdir` on the home page resolves to `/hyp/tests/`).

Targeted micro-corpus, all confirmed fetchable and tiny:

| file | URL | size |
|---|---|---|
| `empty.hyp` | `/hyp/tests/empty.hyp` | 76 B |
| `textattr.hyp` | `/hyp/tests/textattr.hyp` | 229 B |
| `colors.hyp` | `/hyp/tests/colors.hyp` | 249 B |
| `linkattr.hyp` | `/hyp/tests/linkattr.hyp` | — |
| `lines.hyp` | `/hyp/tests/lines.hyp` | — |
| `image.hyp` | `/hyp/tests/image.hyp` | 1655 B |
| `limage.hyp` | `/hyp/tests/limage.hyp` | — |
| `limage2.hyp` | `/hyp/tests/limage2.hyp` | — |
| `b<a>d &f*i?l%e:n'a"m@e.hyp` | `/hyp/tests/` + that literal name | pathological filename quoting test |
| `hcp_orig_en.hyp` | `/hyp/hcp_orig_en.hyp` | 57785 B |
| `hcp_orig_de.hyp` | `/hyp/hcp_orig_de.hyp` | — (also present locally, see below) |
| `st-guide_orig_en.hyp` / `_de.hyp` | `/hyp/st-guide_orig_{en,de}.hyp` | — (also present locally) |
| `reflink_orig_en.hyp` | `/hyp/reflink_orig_en.hyp` | 8731 B — **the `.REF` format spec**, for the deferred phase |
| `reflink_orig_de.hyp` | `/hyp/reflink_orig_de.hyp` | — |

The full corpus listing (704 `.hyp` links) is on the home page,
`https://tho-otto.m68k.eu/hypview/` — parse `submitUrl("/hyp/....hyp")` calls
out of the HTML to enumerate it for the wild sweep (phase 11).
`hypview.cgi?url=<path>&charset=UTF-8` renders any of them to HTML — useful for
the by-eye cross-check mentioned in Verification.

Also already present locally (already-downloaded copies, same content as the
`_orig_` files above, safe to use for phase-2/3 integration tests without a
network fetch): `~/studio/kmp-hyp-ag-view/doc/{1st_conv,hcp,st-guide}_orig_{de,en}.hyp`.

**Provenance note for phase 1 (or whenever the micro-corpus is vendored):** add
a `src/commonTest/resources/corpus/README.md` recording that these files
originate from Thorsten Otto's hypview test corpus (GPL-2 project; the
**files themselves** are third-party test data, not GPL'd source — record the
origin URL per file for clean provenance).

## Appendix: local specification sources, exact paths

- `~/git-repos/hypview/doc/en/hypfmt.ui` (298 lines) / `doc/de/hypfmt.ui` (311
  lines) — primary prose spec, "Technical" chapter of the HCP docs, in
  ST-Guide source (`.ui`) form.
- `~/git-repos/hypview/include/hyp.h` (1147 lines) — constants only.
- `~/git-repos/hypview/hyp/cp_*.h` — 256-entry charset tables (not to be used
  directly per the clean-room decision on this repo's `hyp.h`/hypview code;
  source charset tables independently from unicode.org / public Atari charset
  docs instead, per the locked "spec sources" decision above).

## Appendix: environment facts (as of 2026-08-15, for the next session's setup step)

- Gradle: 8.8, via sdkman (`~/.sdkman/candidates/gradle/current/bin/gradle`).
- JDK: 21.0.2 Temurin is `java -version` default; JDK 11 (Corretto) also
  installed via `/usr/libexec/java_home -V`. Kotlin/Wasm targets need JDK 11+;
  use the JDK-21 default unless a target forces otherwise.
- Kotlin 1.9.22 is what `gradle --version` reports as bundled; the plan
  requires Kotlin 2.x for `wasmWasi` — pin an explicit current 2.x Kotlin
  plugin version in `build.gradle.kts`, do not rely on the Gradle-bundled one.
- `cargo`/`rustc` with `wasm32-unknown-unknown` + `wasm-bindgen` are installed
  (relevant only once the later Rust port starts; `wasm-pack` is *not*
  installed).
- `gh` CLi availability for round 1's GitHub repo creation: not yet checked in
  this session — verify with `command -v gh` at the start of round 1.
