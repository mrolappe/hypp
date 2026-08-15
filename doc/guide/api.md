# hypp — API reference

A flat, complete reference of hypp's public surface: every public type and
member in `src/commonMain/kotlin/de/rholambdapi/hypp/` plus the `wasmJs`
`@JsExport` façade. Derived from the source as of commit `49396df` (2026-08-15,
phase 11 + post-plan follow-ups); if this drifts from the actual source,
the source wins — this file summarizes, it doesn't replace reading
`src/commonMain` for something exotic enough not to be covered here.

For the conceptual model (why the types are shaped this way), read
[`concepts.md`](concepts.md) first. All types below are in package
`de.rholambdapi.hypp` unless noted.

## Opening a document

### `HypDocument.open(bytes: ByteArray): OpenOutcome`

The one entry point. Never throws for malformed `.HYP` content — see
`concepts.md` § "The parse never fails past the container check".

```kotlin
sealed interface OpenOutcome {
    data class Success(val document: HypDocument) : OpenOutcome
    data class Failure(val reason: OpenFailure) : OpenOutcome
}

sealed interface OpenFailure {
    data object InvalidMagic : OpenFailure   // bytes.size < 4, or the first 4 bytes aren't "HDOC"
}
```

## `HypDocument`

```kotlin
class HypDocument(
    val header: Header,
    val extendedHeaders: List<ExtendedHeader>,
    val entries: List<IndexEntry>,
    val charset: HypCharset,
    val nodes: List<Node>,
    val images: List<ImageNode>,
    val diagnostics: List<Diagnostic>,
)
```

| Member | Returns | Notes |
|---|---|---|
| `entry(index: NodeIndex)` | `IndexEntry?` | Any entry type, including navigation-only ones (external ref, quit, …). `null` if `index` is out of range. |
| `node(index: NodeIndex)` | `Node?` | Non-null only when `entry(index)` is type `INTERNAL` or `POPUP`. |
| `image(index: NodeIndex)` | `ImageNode?` | Non-null only when `entry(index)` is type `IMAGE`. |
| `defaultNode` | `NodeIndex?` (lazy val) | The node named by extended header id 2 (`@default`), or `null` if that header is absent or names an entry that doesn't exist. |
| `tableOfContents()` | `TocEntry` | Builds the tree from every entry's `toc` field, rooted at index 0. Safe against cycles — see `concepts.md`. |

## `IndexEntry`

One row of the file's index table (the type-255 EOF sentinel, if present
on the wire, is excluded from `HypDocument.entries` entirely).

```kotlin
data class IndexEntry(
    val len: Int,
    val type: Int,
    val seek: Int,
    val compDiff: Int,
    val next: Int,
    val prev: Int,
    val toc: Int,
    val name: String,
    val compressedLength: Int,   // derived from the following entry's seek offset; no wire length field
) {
    val isImage: Boolean
    val hasData: Boolean          // true only for INTERNAL/POPUP/IMAGE — the types with a compressed object
    val uncompressedLength: Int   // for IMAGE entries, `next` holds high bits of this rather than being a nav link
}
```

`type` constants (`IndexEntry.Companion`):

| Constant | Value | Meaning |
|---|---|---|
| `TYPE_INTERNAL` | 0 | Internal text page — has a `Node` |
| `TYPE_POPUP` | 1 | Popup text page — has a `Node` |
| `TYPE_EXTERNAL_REF` | 2 | External reference (navigation only) |
| `TYPE_IMAGE` | 3 | Image page — has an `ImageNode` |
| `TYPE_SYSTEM` | 4 | System action |
| `TYPE_REXX_SCRIPT` | 5 | REXX script |
| `TYPE_REXX_COMMAND` | 6 | REXX command |
| `TYPE_QUIT` | 7 | Quit/close dummy |
| `TYPE_CLOSE` | 8 | Never observed in the wild (702-file sweep) |
| `TYPE_EOF` | 255 | Filtered out before entries reach `HypDocument.entries` |

## `Node` / `NodeKind`

```kotlin
enum class NodeKind { TEXT, POPUP }

class Node(
    val index: NodeIndex,
    val name: String,
    val kind: NodeKind,
    val windowTitle: String?,
    val graphics: List<Graphic>,
    val crossReferences: List<CrossReference>,
    val dataBlocks: List<DataBlock>,
    val objectTable: List<ObjectTableEntry>,
    val lines: List<Line>,
)
```

## `Line` / `Span`

```kotlin
data class Line(val spans: List<Span>) {
    val text: String  // spans' text concatenated, styling/links dropped
}

data class Span(val text: String, val style: TextStyle, val link: Link? = null)
```

## `TextStyle`

```kotlin
@JvmInline
value class TextStyle(val bits: Int) {
    val attributes: Int        // raw wire attribute bit-vector (bits 0-5)
    val isBold: Boolean
    val isLight: Boolean
    val isItalic: Boolean
    val isUnderlined: Boolean
    val isOutlined: Boolean
    val isShadowed: Boolean
    val foreground: HypColor   // bits 8-11
    val background: HypColor   // bits 12-15

    companion object {
        val Normal: TextStyle  // no attributes, black foreground, default (white) background
    }
}
```

Every attribute-change escape replaces the whole set at once — see
`concepts.md` § "Styling is absolute, not incremental".

## `Link` / `LinkKind`

```kotlin
enum class LinkKind { LINK, ALINK }

data class Link(
    val kind: LinkKind,
    val target: NodeIndex,     // indexes HypDocument.entries — resolve via entry()/node()/image()
    val lineNumber: Int?,      // present only for the LINE-variant escapes
    val label: String,         // always equal to its Span's text
)
```

## `Graphic` (sealed)

```kotlin
sealed interface Graphic {
    val x: Int
    val y: Int
    val width: Int
    val height: Int
    val centered: Boolean   // x == 0

    class Image(
        val imageIndex: NodeIndex,   // resolve via HypDocument.image()
        override val x: Int, override val y: Int,
        override val width: Int, override val height: Int,  // present on wire but ignored by the format
        val ditherMask: ByteArray?,  // not corpus-evidenced; see doc/format-notes.md
    ) : Graphic

    data class Line(
        override val x: Int, override val y: Int,
        override val width: Int, override val height: Int,
        val arrowAtStart: Boolean, val arrowAtEnd: Boolean, val lineStyle: Int,
    ) : Graphic

    data class Box(
        override val x: Int, override val y: Int,
        override val width: Int, override val height: Int,
        val fillPattern: Int,
    ) : Graphic

    data class RoundedBox(
        override val x: Int, override val y: Int,
        override val width: Int, override val height: Int,
        val fillPattern: Int,
    ) : Graphic
}
```

`x`/`y`/`width`/`height` are in character cells.

## `CrossReference` / `DataBlock` / `ObjectTableEntry`

```kotlin
data class CrossReference(val target: NodeIndex, val popupText: String)

/** Opaque prologue data block (escapes 0x28-0x2e); 0x2f is instead attached to a following Graphic.Image's ditherMask. */
data class DataBlock(val type: Int, val data: ByteArray)

/** @tree/@endtree entry — not corpus-evidenced, implemented from the prose spec only. */
data class ObjectTableEntry(val lineNumber: Int, val tree: Int, val obj: Int, val pageIndex: Int)
```

## `ImageNode` / `Palette` / `HypColor`

```kotlin
class ImageNode(
    val index: NodeIndex,
    val name: String,
    val width: Int,
    val height: Int,
    val planeCount: Int,
    val planePresent: Int,
    val planeFilled: Int,
) {
    val pixels: ByteArray             // one palette index per pixel, row-major; lazy + memoized
    fun toRgba(palette: Palette = Palette.AtariSt): ByteArray   // width*height*4 bytes, RGBA8888
}

class Palette(colors: List<HypColor>) {
    fun colorAt(index: Int): HypColor   // HypColor.BLACK if index is out of range
    companion object { val AtariSt: Palette }
}

enum class HypColor(val red: Int, val green: Int, val blue: Int) {
    WHITE, BLACK, RED, GREEN, BLUE, CYAN, YELLOW, MAGENTA,
    LIGHT_GRAY, DARK_GRAY, DARK_RED, DARK_GREEN, DARK_BLUE, DARK_CYAN, DARK_YELLOW, DARK_MAGENTA;
    companion object { fun byIndex(index: Int): HypColor? }   // ordinal == wire palette index
}
```

RGB values follow the standard Atari ST/GEM default-palette convention;
not independently corpus-verified (see `doc/format-notes.md`).

## `HypCharset`

```kotlin
sealed interface HypCharset {
    fun decode(bytes: ByteArray): String

    data object AtariSt : HypCharset   // the format's own default
    data object Latin1 : HypCharset    // ISO-8859-1
    data object Utf8 : HypCharset

    companion object {
        val Default: HypCharset  // = AtariSt
        fun byName(name: String): HypCharset?  // matches @charset header strings, case-insensitive; null if unsupported
    }
}
```

`byName` aliases (case-insensitive): `ATARI`/`ATARIST`/`TOS` → `AtariSt`;
`ISO-8859-1`/`ISO-IR-100`/`ISO8859-1`/`ISO_8859-1`/`LATIN1`/`L1`/
`CSISOLATIN1` → `Latin1`; `UTF-8`/`UTF8` → `Utf8`.

## `Header`

```kotlin
data class Header(
    val itableSize: Int,
    val itableCount: Int,
    val compilerVersion: Int,
    val compilerOs: Int,
)
```

## `ExtendedHeader` (sealed)

```kotlin
sealed interface ExtendedHeader {
    val id: Int

    data class Unknown(override val id: Int, val data: ByteArray) : ExtendedHeader   // every id is captured, nothing lost

    data class Charset(val name: String) : ExtendedHeader { companion object { const val ID = 30 } }  // @charset
    data class Default(val name: String) : ExtendedHeader { companion object { const val ID = 2 } }   // @default
}
```

## `NodeIndex`

```kotlin
@JvmInline
value class NodeIndex(val value: Int)   // require(value >= 0); a 0-based index into entries/nodes
```

## `TocEntry`

```kotlin
data class TocEntry(val index: NodeIndex, val children: List<TocEntry>)
```

Returned by `HypDocument.tableOfContents()` — see `concepts.md`.

## `Diagnostic` (sealed)

Everything the parser noticed that isn't fatal — see `concepts.md` § "The
parse never fails past the container check".

| Variant | Fields | Trigger |
|---|---|---|
| `UnsupportedCharset` | `name: String` | `@charset` header names a charset outside v1's supported set (`HypCharset.byName` returned `null`). Falls back to `HypCharset.Default`. |
| `DecompressionFailed` | `index: NodeIndex` | An internal/popup/image entry's compressed object failed to decompress. The entry is omitted from `nodes`/`images` entirely. |
| `NodeDataOverrun` | `index: NodeIndex` | A prologue record ran past the end of the node's (decompressed) data. Prologue parsing for that node stopped at that point; the text region may be partial or absent. |
| `CrossReferenceLimitExceeded` | `index: NodeIndex`, `count: Int` | A node carried more than the spec's documented maximum of 12 cross-reference blocks. All of them are still kept in `crossReferences`. |
| `UnknownEscape` | `index: NodeIndex`, `code: Int` | A text-region `ESC` was followed by a type byte the format doesn't define. Skipped; the surrounding text is unaffected. |
| `UnterminatedLine` | `index: NodeIndex` | A node's data ended mid-line, without the terminating NUL. The partial line is kept in `lines`. |
| `DanglingNodeReference` | `index: NodeIndex`, `target: Int` | A link, cross-reference or image placement named an index not present in the document's own index table. The reference is dropped; any accompanying text/label is kept. |

## JS façade (`wasmJs` only, package `de.rholambdapi.hypp.js`)

See `concepts.md` § "The JS façade" for why this is shaped differently
from the rest of the API (handle-based, flat, sentinel returns instead of
`null`/exceptions). Source: `src/wasmJsMain/kotlin/de/rholambdapi/hypp/js/HyppJs.kt`.

| Function | Signature | Notes |
|---|---|---|
| `hyppOpen` | `(base64: String) -> Int` | Decodes + opens; returns a document handle, or `-1` on `OpenFailure`. |
| `hyppEntryCount` | `(handle: Int) -> Int` | |
| `hyppEntryType` | `(handle: Int, index: Int) -> Int` | `IndexEntry.type`; see the `TYPE_*` table above. |
| `hyppEntryName` | `(handle: Int, index: Int) -> String` | |
| `hyppNodeExists` | `(handle: Int, index: Int) -> Boolean` | |
| `hyppNodeKind` | `(handle: Int, index: Int) -> Int` | `0` TEXT, `1` POPUP, `-1` no such node. |
| `hyppNodeWindowTitle` | `(handle: Int, index: Int) -> String` | `""` if absent. |
| `hyppNodeLineCount` | `(handle: Int, index: Int) -> Int` | |
| `hyppLineSpanCount` | `(handle: Int, index: Int, lineNo: Int) -> Int` | |
| `hyppSpanText` | `(handle: Int, index: Int, lineNo: Int, spanNo: Int) -> String` | |
| `hyppSpanStyleBits` | `(handle: Int, index: Int, lineNo: Int, spanNo: Int) -> Int` | Raw `TextStyle.bits` — decode client-side per the bit layout documented above. |
| `hyppSpanLinkKind` | `(handle: Int, index: Int, lineNo: Int, spanNo: Int) -> Int` | `-1` no link, `0` LINK, `1` ALINK. |
| `hyppSpanLinkTarget` | `(handle: Int, index: Int, lineNo: Int, spanNo: Int) -> Int` | `-1` no link. |
| `hyppSpanLinkLineNumber` | `(handle: Int, index: Int, lineNo: Int, spanNo: Int) -> Int` | `-1` no link or no line number. |
| `hyppGraphicCount` | `(handle: Int, index: Int) -> Int` | |
| `hyppGraphicKind` | `(handle: Int, index: Int, graphicNo: Int) -> Int` | `0` Image, `1` Line, `2` Box, `3` RoundedBox, `-1` none. |
| `hyppGraphicX` / `Y` / `Width` / `Height` | `(handle: Int, index: Int, graphicNo: Int) -> Int` | |
| `hyppGraphicImageIndex` | `(handle: Int, index: Int, graphicNo: Int) -> Int` | Only for `Image`-kind graphics; `-1` otherwise. |
| `hyppGraphicLineFlags` | `(handle: Int, index: Int, graphicNo: Int) -> Int` | Only for `Line`-kind graphics: bit0 arrowAtStart, bit1 arrowAtEnd, remaining bits lineStyle. |
| `hyppGraphicFillPattern` | `(handle: Int, index: Int, graphicNo: Int) -> Int` | Only for `Box`/`RoundedBox`-kind graphics; `-1` otherwise. |
| `hyppDiagnosticCount` | `(handle: Int) -> Int` | |
| `hyppDiagnosticKind` | `(handle: Int, diagnosticNo: Int) -> Int` | `0` UnsupportedCharset, `1` DecompressionFailed, `2` NodeDataOverrun, `3` CrossReferenceLimitExceeded, `4` UnknownEscape, `5` UnterminatedLine, `6` DanglingNodeReference. |
| `hyppDiagnosticNodeIndex` | `(handle: Int, diagnosticNo: Int) -> Int` | `-1` for `UnsupportedCharset` (the one variant with no node index). |
| `hyppDiagnosticExtra` | `(handle: Int, diagnosticNo: Int) -> Int` | The variant's secondary numeric field (`count`/`code`/`target`); `-1` where none exists. |
| `hyppDiagnosticText` | `(handle: Int, diagnosticNo: Int) -> String` | `UnsupportedCharset.name`; `""` for any other kind. |

There is no `hyppClose`/handle-release function — opened documents live in
an in-memory map for the lifetime of the JS module instance.
