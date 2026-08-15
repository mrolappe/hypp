# CLI consumer app — design space

Brainstorm only, requested 2026-08-15, after the plan's 11 phases and the
two post-plan follow-ups (includeBuild integration check, unknown-escape
file survey — see `doc/LEARNINGS.md`). **No decisions made here** — this
lays out options for a future session to choose from, same spirit as
`doc/PLAN.md`'s own "Accommodating the deferred work" appendix.

## Why a CLI at all

hypp today is a library with no consumer outside its own test suite. Every
existing renderer (`hyp2text`, `hyp2html`, `toCanonicalJson`) is
`commonTest`-scoped — proven correct via golden tests, but nothing a user
can run against an arbitrary `.hyp` file today without writing Kotlin. A CLI
would be the first real end-user-facing artefact, and (per the corpus-sweep
follow-up) there's now a small, named set of real documents
(`chips_x.hyp`, `chips50d.hyp`, `206stb12.hyp`, plus the whole 702-file
corpus) to exercise it against immediately.

## Target platform

| Option | Pros | Cons |
|---|---|---|
| **JVM fat jar** (`shadowJar` or Gradle's `application` distribution) | Zero new KMP targets — `jvm()` already exists and is exactly what `HypDocument` runs on in tests today. Fastest to stand up. | Requires a JVM on the user's machine; slower cold-start than a native binary. |
| **GraalVM native-image from the JVM target** | Real native binary, no JVM required at runtime; still built from the existing `jvm()` target. | New build toolchain (GraalVM SDK) and platform-specific binary builds (no cross-compilation without CI matrix). |
| **`wasmWasi` (already a KMP target)** | Already built and tested every round (`wasmWasiTest` green since phase 1) — a CLI here reuses an existing target rather than adding one. Runs under `wasmtime`/`node --experimental-wasi`. | WASI CLI ergonomics (arg parsing, stdout, file reads) are less battle-tested than JVM; distributing a `.wasm` file to end users is a less familiar shape than a jar or native binary. |
| **New Kotlin/Native target** (`linuxX64`/`macosX64`/`mingwX64`) | True native binary, no new runtime-selection story (unlike GraalVM). | A genuinely new KMP target — more build matrix, more CI surface, and `commonMain` would need to stay free of anything JVM/WASI-specific (it already is, so this is low-risk, just more targets to build/test on every phase). |

None of these are mutually exclusive long-term (KMP supports multiple
binary targets from one `commonMain`), but the first CLI iteration should
pick exactly one — probably the JVM fat jar, since it needs no new target
and the whole test suite already runs on `jvm`.

## Command surface

Sketched, not committed:

- `hypp dump <file>` — render to stdout. `--format text|html|json` selects
  `hyp2text`/`hyp2html`/`toCanonicalJson` (see "Reuse" below); default
  `text`.
- `hypp validate <file>` — open the document, print `diagnostics`, exit
  non-zero if any diagnostic is present (or a `--strict` flag distinguishing
  "hard" diagnostics like `DecompressionFailed` from informational ones like
  `UnknownEscape`). Useful standalone given the corpus sweep already showed
  diagnostics are rare but real (76 `DecompressionFailed`, 1
  `UnsupportedCharset` across 702 files).
- `hypp inspect <file>` — structural summary without full content: header
  fields, extended headers, table of contents (`tableOfContents()`), entry
  count/types, image count. The "what's in this file" command, distinct
  from `dump`'s "render the content" command.
- `hypp extract-images <file> [--out dir]` — write each `ImageNode` out as a
  real image file (`hyp2html`'s existing `bmp()` encoder is a candidate
  starting point, or a proper PNG encoder if fidelity/size matters more than
  reusing what's already written).
- Multi-file / corpus mode: accepting a directory or glob and running
  `validate` or `inspect` over every file, echoing the shape of
  `CorpusSweep.kt`'s loop but without the network-fetch part — this could
  even *replace* `CorpusSweep.kt`'s core loop if the CLI grows first, per
  the "reuse what's a few files over" rung.

## Output formats

- **Plain text** — `hyp2text`'s existing format, or a CLI-native variant
  (it currently prints internal debug-ish markers like `<b,i>text</>` for
  style, useful for tests but maybe not for an end-user-facing tool — worth
  a fresh look rather than assuming the test renderer is the right UX).
- **HTML** — `hyp2html`, already does images-as-`data:`-URI, styled spans,
  links as `<a href="#target">`. Closest to being genuinely useful today.
- **JSON** — `toCanonicalJson()` (phase 10) already exists and is a full,
  stable structural dump — arguably the *least* work of the three formats
  to expose via CLI, since it needs no new rendering logic at all, only a
  file-write wrapper.
- Exit codes / machine-readable diagnostics for `validate`, so it composes
  in scripts (CI checking a batch of `.hyp` files before publishing them,
  for instance).

## Reusing `Hyp2Text.kt` / `Hyp2Html.kt`

Both currently live in `src/commonTest/kotlin/...` — deliberately, per
phase 6/7 ("the phase-N integration consumer"), because their job was to
exercise the public API as a test, not to be a published artefact. A CLI
needs them (or equivalents) reachable from a non-test source set. Options:

1. **Promote to `commonMain`** as a small `de.rholambdapi.hypp.render`
   package, published alongside the core model. Simplest, but changes the
   library's public surface — everything in `commonMain` today is the core
   object model plus `open()`; adding renderers means hypp stops being
   "just a parser" and starts bundling opinionated output formats consumers
   may not all want.
2. **New Gradle subproject** (e.g. `hypp-cli`, multi-module build) that
   depends on published `hypp` and re-implements or copies the renderer
   logic into its own `main`. Keeps the core library's surface untouched,
   costs a small amount of duplication (or an `implementation` dependency
   on `hypp`'s own `commonTest`, which Gradle doesn't expose to other
   modules by default — test source sets aren't published).
3. **Leave `Hyp2Text`/`Hyp2Html` as test-only** and let the CLI own a
   completely independent (possibly simpler, possibly more capable)
   rendering layer from day one, treating the test versions purely as what
   they were built for — integration-test scaffolding, not a reusable
   component.

Option 2 (separate module, own rendering code, `hypp` as a normal published
dependency) is closest to how a real external consumer would use the
library and doesn't touch the finished 11-phase core at all; option 1 is
less duplication but is a scope decision about what hypp *is* that the
plan's locked decisions never made. Worth raising explicitly rather than
defaulting.

## Argument parsing / dependencies

The plan's "no dependencies" decision (`doc/PLAN.md`) applies to the
library's `commonMain`/`commonTest` — a separate CLI module isn't bound by
it automatically, but reusing the same discipline (hand-rolled `args`
parsing for a handful of subcommands, no `kotlinx-cli`/`clikt`) is
plausible for a command surface this small. Worth an explicit call once the
command surface above is narrowed down, not assumed either way.

## Testing

The existing golden-file machinery (`doc/goldens/*.json`, `ParityGoldenTest.kt`,
phase 10) is a natural fit for CLI output testing too — run the built CLI
against a fixture and diff stdout against a checked-in golden, the same
pattern already proven for `toCanonicalJson()`. `CorpusSweep.kt`'s
now-named unknown-escape files (`chips_x.hyp`, `chips50d.hyp`,
`206stb12.hyp`) and the `hyp2gdos.hyp` `0xa4` file are ready-made
real-world exercise cases beyond the vendored micro-corpus.

## Suggested next step (not a decision)

If/when this gets picked up: start with the JVM fat-jar target, `dump`
(text + json, since json needs zero new rendering code) and `validate`
only, as a new `hypp-cli` Gradle subproject depending on published `hypp`.
That's the smallest slice that's genuinely useful and exercises the
composite-build/publish path the includeBuild follow-up already validated.
