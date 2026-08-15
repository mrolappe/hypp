# Vendored micro-corpus

Test fixtures for hypp's parser, sourced from Thorsten Otto's hypview test
corpus. hypview itself is GPL-2, but these `.hyp` files are third-party test
*data*, not GPL'd source — vendored here for reproducible, offline tests.

| file | origin | size |
|---|---|---|
| `empty.hyp` | `https://tho-otto.m68k.eu/hyp/tests/empty.hyp` | 76 B |
| `textattr.hyp` | `https://tho-otto.m68k.eu/hyp/tests/textattr.hyp` | 229 B |
| `hcp_orig_en.hyp` | `https://tho-otto.m68k.eu/hyp/hcp_orig_en.hyp` (fetched via local cache at `~/studio/kmp-hyp-ag-view/doc/hcp_orig_en.hyp`, same content) | 57785 B |

These files are checked in for provenance and for by-hand hex inspection.
Tests do not read them from disk (multiplatform resource loading across
`jvm`/`wasmJs`/`wasmWasi` is unsolved and out of scope here) — instead their
bytes are embedded as base64 in
`src/commonTest/kotlin/de/rholambdapi/hypp/TestCorpus.kt`, generated from
these exact files.
