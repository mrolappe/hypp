# hypp — overview

**hypp** is a Kotlin Multiplatform library for reading Atari ST `.HYP`
(ST-Guide/HCP) hypertext documents. It parses the binary container into a
rich, typed object model — nodes, styled text spans, links, images,
diagnostics — and exposes it identically on `jvm`, `wasmJs` and `wasmWasi`.

This page is the entry point for a new consumer, human or AI. It answers
"what is this, how do I get it, how do I use it in five lines." For the
domain model's shape and invariants, read [`concepts.md`](concepts.md)
next; for the full symbol-by-symbol reference, see [`api.md`](api.md).

## What it does and doesn't do

- **Reads** `.HYP` files (read-only; write/round-trip is deliberately out
  of scope for v1 — see `doc/PLAN.md` § "Accommodating the deferred work").
- **Never throws on malformed input.** Opening a document either succeeds
  or fails at the container level (bad magic bytes); everything else that
  can go wrong with an individual node, escape or reference is recorded as
  a non-fatal [`Diagnostic`](api.md#diagnostic) and parsing continues. This
  was verified against the full 702-file public `.hyp` corpus (zero
  crashes) — see `doc/progress/phase-11-wild-sweep.md`.
- **Does not interpret `.REF` files** (a companion cross-document reference
  format) — deferred by design, same appendix as above.
- **Has no runtime dependencies.** `commonMain`/`commonTest` are Kotlin
  stdlib only, by locked project decision (`doc/PLAN.md`).

## Status

All 11 planned implementation phases are complete and green
(`doc/PROGRESS.md`). The public API described in `api.md` is exercised by
a full test suite on all three targets, cross-checked against a Rust-port
JSON parity spec (`doc/model-spec.md`, `doc/goldens/`), and run against the
full public `.hyp` corpus with zero open failures.

## Getting it

Not yet published to a public repository — the intended distribution path
(per `doc/PLAN.md`'s locked "Distribution" decision, confirmed working
against a real composite build) is a git submodule plus Gradle
`includeBuild`, not a binary dependency:

```kotlin
// settings.gradle.kts, in your consuming project
includeBuild("path/to/hypp") {
    dependencySubstitution {
        // Required: KMP's per-target Maven coordinate (hypp-jvm) doesn't
        // auto-substitute against the included build's project name (hypp).
        substitute(module("de.rholambdapi:hypp-jvm")).using(project(":"))
    }
}
```

```kotlin
// build.gradle.kts, in your consuming project
dependencies {
    implementation("de.rholambdapi:hypp-jvm:0.1.0-SNAPSHOT")
}
```

See `doc/LEARNINGS.md` § "Post-plan follow-up: includeBuild integration
check" for why the `dependencySubstitution` block is necessary and how it
was confirmed.

Alternatively, `./gradlew publishToMavenLocal` inside the hypp repo
publishes `de.rholambdapi:hypp-jvm:0.1.0-SNAPSHOT` (and the `wasmJs`/
`wasmWasi` variants) to `~/.m2`, resolvable via a plain `mavenLocal()`
repository — simpler for local experimentation, not the plan's intended
long-term distribution path.

## Quick start (JVM/Kotlin)

```kotlin
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.OpenOutcome
import java.io.File

val bytes = File("example.hyp").readBytes()
when (val outcome = HypDocument.open(bytes)) {
    is OpenOutcome.Success -> {
        val doc = outcome.document
        for (node in doc.nodes) {
            println("== ${node.name} ==")
            for (line in node.lines) println(line.text)
        }
    }
    is OpenOutcome.Failure -> println("not a .HYP file: ${outcome.reason}")
}
```

## Quick start (JavaScript, via the `wasmJs` façade)

The `wasmJs` target additionally exports a flat, handle-based
`@JsExport` façade (`HyppJs.kt`) — see `concepts.md` § "The JS façade" for
why it's shaped this way, and `api.md` § "JS façade" for the full function
list.

```javascript
import { hyppOpen, hyppNodeLineCount, hyppLineSpanCount, hyppSpanText } from "./hypp.mjs";

const handle = hyppOpen(base64EncodedHypFile);
const lineCount = hyppNodeLineCount(handle, /* nodeIndex */ 0);
for (let l = 0; l < lineCount; l++) {
    const spanCount = hyppLineSpanCount(handle, 0, l);
    let text = "";
    for (let s = 0; s < spanCount; s++) text += hyppSpanText(handle, 0, l, s);
    console.log(text);
}
```

## For agentic/AI consumers

If you're an AI agent working against this library rather than a human
reading it:

- **`api.md` is a complete, flat reference** — every public type and
  member in `commonMain`, plus the JS façade, in one file. Prefer it over
  grepping `src/commonMain` when you need an exact signature or field; it's
  kept in sync with the source (see its own header for the source files it
  was derived from and when).
- **The model has no partial/nullable "not yet parsed" states.** Illegal
  states are unrepresentable by construction — a `HypDocument` you hold is fully
  parsed; there is no "call `.load()` first" step, no lazy fields that can
  throw, and diagnostics are data (`doc.diagnostics: List<Diagnostic>`),
  never exceptions, for anything short of "this isn't a `.HYP` file at
  all" (`OpenOutcome.Failure`).
- **References between nodes are `NodeIndex` values, not object
  pointers**, and a `NodeIndex` may point at nothing resolvable (a
  dangling reference in the source file). Always go through
  `HypDocument.entry()`/`.node()`/`.image()`, which return nullable, rather
  than assuming an index resolves.
- **Don't re-derive the format from `doc/PLAN.md`/`doc/format-notes.md` to
  answer usage questions** — those documents record *why* the parser is
  built the way it is (spec ambiguities, corpus evidence), not *how to call
  it*. `concepts.md` and `api.md` are the consumer-facing subset.
