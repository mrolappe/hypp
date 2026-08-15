# Phase 4 — Charsets

**State: green.**

## Completed

- `HypCharset.kt` — sealed type with three v1 members:
  - `AtariSt` — ASCII pass-through 0x00-0x7F, a 128-entry table for 0x80-0xFF
    sourced from the public Wikipedia "Atari ST character set" article
    (independent of hypview's `cp_atarist.h`, per the clean-room decision).
  - `Latin1` — identity mapping, byte value == code point.
  - `Utf8` — delegates to stdlib `ByteArray.decodeToString()`.
  - `HypCharset.byName(name)` resolves a `@charset` descriptor string
    case-insensitively against the alias set from the current UDO manual's
    charset descriptor table (`man.udo-open-source.org`), returning `null`
    for anything outside v1's three.
- `ExtendedHeader.Charset(name: String)` — id 30, parsed as a NUL-terminated
  C-string via the existing `decodeName()`.
- `Diagnostic.kt` — new, minimal: just `UnsupportedCharset(name)`. Not the
  full sealed hierarchy from `doc/PLAN.md`'s domain-model sketch (that
  hierarchy's `Diagnostic.location` needs `NodeIndex`, which doesn't exist
  until node parsing lands) — see Decisions.
- `HypDocument` gained `charset: HypCharset` and `diagnostics: List<Diagnostic>`.
  Resolution in `open()`: no charset header → `HypCharset.Default` (`AtariSt`);
  header present and recognized → that charset; header present and
  unrecognized → `Default` plus an `UnsupportedCharset` diagnostic.
  `OpenOutcome` is still `Success` in the unrecognized case — a bad charset
  name is not a parse failure.

## Decisions

- **Extended header id 30 is a charset *name string*, not `hyp.h`'s numeric
  `HYP_CHARSET` enum.** The plan was written expecting an id; hex-inspecting
  the corpus (`textattr.hyp`, `empty.hyp`) showed id 30's 8-byte payload is
  the literal C-string `"atarist\0"`. `hyp.h`'s enum is hypview's *internal*
  representation after it parses this string — irrelevant to the file
  format's encoding, and out of bounds anyway (clean-room). See
  `doc/format-notes.md`.
- **Charset name aliases sourced from the current UDO manual**, not from
  hypview. `header.ui`/`hcp_orig_en.hyp`/`st-guide_orig_en.hyp` all use only
  `"atarist"`; the corpus has no example of a Latin-1 or UTF-8 document, so
  the exact alias spellings for those two came from
  `man.udo-open-source.org/en/spec_converting_8bit_characters.htm` (UDO is
  the compiler that writes these files; its own current manual is the
  authoritative source for the descriptor syntax, and it predates and is
  independent of hypview).
- **No `Diagnostic.location` yet.** Deferred until `NodeIndex` exists
  (phase 5+), rather than inventing a placeholder shape now. Revisit when the
  next diagnostic variant is added.

## Tests added

All green on `jvm`, `wasmJs`, `wasmWasi` (23 tests total in the suite now):

- `HypCharsetTest` (6) — `AtariSt` decodes known accented letters (ä ö ü ß Ä
  Ö Ü) and passes ASCII through; the same byte (`0xE4`) decodes to `Σ` under
  `AtariSt` and `ä` under `Latin1` (the charset-selection case, not just a
  table smoke test); `Utf8` decodes a real multi-byte sequence; `byName`
  resolves aliases case-insensitively and returns `null` for an unsupported
  name (`koi8-r`).
- `ContainerTest` gained three cases: `empty.hyp`'s `"atarist"` header
  resolves to `AtariSt` with no diagnostics; `hcp_orig_en.hyp` (no charset
  header) defaults to `AtariSt`; a byte-mutated copy of `empty.hyp` with the
  id-30 payload replaced in place (`"bogus\0\0\0"`, same 8-byte length so no
  other offset moves) falls back to `AtariSt` and records exactly one
  `UnsupportedCharset("bogus")` diagnostic, with `OpenOutcome.Success`.

## Remaining

- Charset selection isn't wired to any text decoding yet — nothing decodes
  node bytes to `String` until phase 6 (`Line`/`Span`). Phase 6 should call
  `document.charset.decode(...)` on each text object's bytes.
- `ExtendedHeader.Language` (id 31) is not implemented — no corpus file uses
  it, and it wasn't needed for this phase's red test. Add when a document
  requiring it turns up (wild sweep, phase 11, or an actual consumer need).
