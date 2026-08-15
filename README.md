# hypp

A Kotlin Multiplatform library for reading Atari ST `.HYP` (ST-Guide)
hypertext documents. Clean-room implementation from the format
specification; exposes a rich object model of nodes, styled text spans,
links, and images across `jvm`, `wasmJs`, and `wasmWasi` targets.

Status: all 11 planned phases complete and green. See `doc/PROGRESS.md` for
phase status and `doc/PLAN.md` for the full design and roadmap.

**Using the library?** Start at
[`doc/guide/overview.md`](doc/guide/overview.md) — install/quick-start,
[`doc/guide/concepts.md`](doc/guide/concepts.md) for the domain model, and
[`doc/guide/api.md`](doc/guide/api.md) for the full API reference.

Licensed under Apache-2.0.
