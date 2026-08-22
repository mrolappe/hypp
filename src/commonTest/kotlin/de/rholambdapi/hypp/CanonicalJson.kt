package de.rholambdapi.hypp

/**
 * A minimal, deterministic JSON value model and pretty-printer — hand-rolled rather than pulling
 * in a serialization library, since the only requirement is a stable, human-diffable byte-for-byte
 * rendering (field order is declaration order, never a hash-map iteration order).
 *
 * This is the parity artefact's writer (phase 10, `doc/PLAN.md`): [HypDocument.toCanonicalJson]'s
 * field names and variant tags are the schema a Rust port's goldens are checked against — see
 * `doc/model-spec.md`.
 */
private sealed interface Json {
    data object Null : Json
    data class Bool(val value: Boolean) : Json
    data class Num(val value: Long) : Json
    data class Str(val value: String) : Json
    data class Arr(val items: List<Json>) : Json
    data class Obj(val fields: List<Pair<String, Json>>) : Json
}

private fun obj(vararg fields: Pair<String, Json>): Json = Json.Obj(fields.toList())
private fun arr(items: List<Json>): Json = Json.Arr(items)
private fun num(v: Int): Json = Json.Num(v.toLong())
private fun str(v: String): Json = Json.Str(v)
private fun strOrNull(v: String?): Json = v?.let { Json.Str(it) } ?: Json.Null
private fun bool(v: Boolean): Json = Json.Bool(v)
private fun numOrNull(v: Int?): Json = v?.let { num(it) } ?: Json.Null

private const val HEX = "0123456789abcdef"
private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val v = byte.toInt() and 0xFF
        append(HEX[v shr 4])
        append(HEX[v and 0xF])
    }
}

private fun jsonEscape(s: String): String = buildString {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c.code < 0x20 -> append("\\u" + c.code.toString(16).padStart(4, '0'))
        else -> append(c)
    }
    append('"')
}

private fun pad(indent: Int) = "  ".repeat(indent)

private fun Json.render(indent: Int): String = when (this) {
    Json.Null -> "null"
    is Json.Bool -> value.toString()
    is Json.Num -> value.toString()
    is Json.Str -> jsonEscape(value)
    is Json.Arr -> if (items.isEmpty()) "[]" else buildString {
        append("[\n")
        items.forEachIndexed { i, item ->
            append(pad(indent + 1))
            append(item.render(indent + 1))
            if (i < items.lastIndex) append(",")
            append("\n")
        }
        append(pad(indent))
        append("]")
    }
    is Json.Obj -> if (fields.isEmpty()) "{}" else buildString {
        append("{\n")
        fields.forEachIndexed { i, (name, value) ->
            append(pad(indent + 1))
            append(jsonEscape(name))
            append(": ")
            append(value.render(indent + 1))
            if (i < fields.lastIndex) append(",")
            append("\n")
        }
        append(pad(indent))
        append("}")
    }
}

private fun Header.toJson(): Json = obj(
    "itableSize" to num(itableSize),
    "itableCount" to num(itableCount),
    "compilerVersion" to num(compilerVersion),
    "compilerOs" to num(compilerOs),
)

private fun ExtendedHeader.toJson(): Json = when (this) {
    is ExtendedHeader.Charset -> obj("kind" to str("Charset"), "name" to str(name))
    is ExtendedHeader.Default -> obj("kind" to str("Default"), "name" to str(name))
    is ExtendedHeader.Database -> obj("kind" to str("Database"), "name" to str(name))
    is ExtendedHeader.Author -> obj("kind" to str("Author"), "name" to str(name))
    is ExtendedHeader.Unknown -> obj("kind" to str("Unknown"), "id" to num(id), "data" to str(data.toHex()))
}

private fun HypCharset.toJson(): Json = str(
    when (this) {
        HypCharset.AtariSt -> "AtariSt"
        HypCharset.Latin1 -> "Latin1"
        HypCharset.Utf8 -> "Utf8"
    }
)

private fun IndexEntry.toJson(index: Int): Json = obj(
    "index" to num(index),
    "len" to num(len),
    "type" to num(type),
    "seek" to num(seek),
    "compDiff" to num(compDiff),
    "next" to num(next),
    "prev" to num(prev),
    "toc" to num(toc),
    "name" to str(name),
    "compressedLength" to num(compressedLength),
    "uncompressedLength" to num(uncompressedLength),
)

private fun CrossReference.toJson(): Json = obj("target" to num(target.value), "popupText" to str(popupText))

private fun DataBlock.toJson(): Json = obj("type" to num(type), "data" to str(data.toHex()))

private fun ObjectTableEntry.toJson(): Json = obj(
    "lineNumber" to num(lineNumber), "tree" to num(tree), "obj" to num(obj), "pageIndex" to num(pageIndex),
)

private fun Graphic.toJson(): Json = when (this) {
    is Graphic.Image -> obj(
        "kind" to str("Image"),
        "imageIndex" to num(imageIndex.value),
        "x" to num(x), "y" to num(y), "width" to num(width), "height" to num(height),
        "ditherMask" to (ditherMask?.let { str(it.toHex()) } ?: Json.Null),
    )
    is Graphic.Line -> obj(
        "kind" to str("Line"),
        "x" to num(x), "y" to num(y), "width" to num(width), "height" to num(height),
        "arrowAtStart" to bool(arrowAtStart), "arrowAtEnd" to bool(arrowAtEnd), "lineStyle" to num(lineStyle),
    )
    is Graphic.Box -> obj(
        "kind" to str("Box"),
        "x" to num(x), "y" to num(y), "width" to num(width), "height" to num(height),
        "fillPattern" to num(fillPattern),
    )
    is Graphic.RoundedBox -> obj(
        "kind" to str("RoundedBox"),
        "x" to num(x), "y" to num(y), "width" to num(width), "height" to num(height),
        "fillPattern" to num(fillPattern),
    )
}

private fun Link.toJson(): Json = obj(
    "kind" to str(kind.name),
    "target" to num(target.value),
    "lineNumber" to numOrNull(lineNumber),
    "label" to str(label),
)

private fun Span.toJson(): Json = obj(
    "text" to str(text),
    "styleBits" to num(style.bits),
    "link" to (link?.toJson() ?: Json.Null),
)

private fun Line.toJson(): Json = obj("spans" to arr(spans.map { it.toJson() }))

private fun Node.toJson(): Json = obj(
    "index" to num(index.value),
    "name" to str(name),
    "kind" to str(kind.name),
    "windowTitle" to strOrNull(windowTitle),
    "graphics" to arr(graphics.map { it.toJson() }),
    "crossReferences" to arr(crossReferences.map { it.toJson() }),
    "dataBlocks" to arr(dataBlocks.map { it.toJson() }),
    "objectTable" to arr(objectTable.map { it.toJson() }),
    "lines" to arr(lines.map { it.toJson() }),
)

private fun ImageNode.toJson(): Json = obj(
    "index" to num(index.value),
    "name" to str(name),
    "width" to num(width),
    "height" to num(height),
    "planeCount" to num(planeCount),
    "planePresent" to num(planePresent),
    "planeFilled" to num(planeFilled),
    "pixels" to str(pixels.toHex()),
)

private fun Diagnostic.toJson(): Json = when (this) {
    is Diagnostic.UnsupportedCharset -> obj("kind" to str("UnsupportedCharset"), "name" to str(name))
    is Diagnostic.DecompressionFailed -> obj("kind" to str("DecompressionFailed"), "index" to num(index.value))
    is Diagnostic.NodeDataOverrun -> obj("kind" to str("NodeDataOverrun"), "index" to num(index.value))
    is Diagnostic.CrossReferenceLimitExceeded -> obj(
        "kind" to str("CrossReferenceLimitExceeded"), "index" to num(index.value), "count" to num(count),
    )
    is Diagnostic.UnknownEscape -> obj("kind" to str("UnknownEscape"), "index" to num(index.value), "code" to num(code))
    is Diagnostic.UnterminatedLine -> obj("kind" to str("UnterminatedLine"), "index" to num(index.value))
    is Diagnostic.DanglingNodeReference -> obj(
        "kind" to str("DanglingNodeReference"), "index" to num(index.value), "target" to num(target),
    )
}

/** Canonical, deterministic JSON rendering of a whole document — see `doc/model-spec.md`. */
internal fun HypDocument.toCanonicalJson(): String = obj(
    "header" to header.toJson(),
    "extendedHeaders" to arr(extendedHeaders.map { it.toJson() }),
    "charset" to charset.toJson(),
    "entries" to arr(entries.mapIndexed { i, e -> e.toJson(i) }),
    "nodes" to arr(nodes.map { it.toJson() }),
    "images" to arr(images.map { it.toJson() }),
    "diagnostics" to arr(diagnostics.map { it.toJson() }),
).render(0) + "\n"
