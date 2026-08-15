@file:OptIn(ExperimentalJsExport::class)

package de.rholambdapi.hypp.js

import de.rholambdapi.hypp.Diagnostic
import de.rholambdapi.hypp.Graphic
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.LinkKind
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.OpenOutcome
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * `wasmJs`-only flattening of [HypDocument] into the primitive/String-only shape Kotlin/Wasm's
 * `@JsExport` supports: no arrays and no exported classes in this Kotlin version (2.4.10) — see
 * `doc/LEARNINGS.md` phase 9. Every accessor below takes a document [handle] from [hyppOpen] plus
 * plain integer indices, the flat/C-style array idiom that constraint forces: a `*Count` function
 * bounds a loop, per-element getters fill it in. Out-of-range indices return a sentinel (`-1` for
 * Int/Boolean-as-absent, `""` for String) instead of throwing.
 */
private val documents = HashMap<Int, HypDocument>()
private var nextHandle = 0

/** Decodes and opens a base64-encoded `.HYP` file, returning a handle for every other function, or -1 on failure. */
@OptIn(ExperimentalEncodingApi::class)
@JsExport
fun hyppOpen(base64: String): Int {
    val outcome = HypDocument.open(Base64.decode(base64))
    val document = (outcome as? OpenOutcome.Success)?.document ?: return -1
    val handle = nextHandle++
    documents[handle] = document
    return handle
}

private fun doc(handle: Int) = documents[handle]
private fun node(handle: Int, index: Int) = if (index < 0) null else doc(handle)?.node(NodeIndex(index))
private fun lineAt(handle: Int, index: Int, lineNo: Int) = node(handle, index)?.lines?.getOrNull(lineNo)
private fun spanAt(handle: Int, index: Int, lineNo: Int, spanNo: Int) = lineAt(handle, index, lineNo)?.spans?.getOrNull(spanNo)
private fun graphicAt(handle: Int, index: Int, graphicNo: Int) = node(handle, index)?.graphics?.getOrNull(graphicNo)

@JsExport
fun hyppEntryCount(handle: Int): Int = doc(handle)?.entries?.size ?: -1

@JsExport
fun hyppEntryType(handle: Int, index: Int): Int = doc(handle)?.entries?.getOrNull(index)?.type ?: -1

@JsExport
fun hyppEntryName(handle: Int, index: Int): String = doc(handle)?.entries?.getOrNull(index)?.name ?: ""

@JsExport
fun hyppNodeExists(handle: Int, index: Int): Boolean = node(handle, index) != null

/** 0 [NodeKind.TEXT], 1 [NodeKind.POPUP], -1 no such node. */
@JsExport
fun hyppNodeKind(handle: Int, index: Int): Int = when (node(handle, index)?.kind) {
    NodeKind.TEXT -> 0
    NodeKind.POPUP -> 1
    null -> -1
}

@JsExport
fun hyppNodeWindowTitle(handle: Int, index: Int): String = node(handle, index)?.windowTitle ?: ""

@JsExport
fun hyppNodeLineCount(handle: Int, index: Int): Int = node(handle, index)?.lines?.size ?: -1

@JsExport
fun hyppLineSpanCount(handle: Int, index: Int, lineNo: Int): Int = lineAt(handle, index, lineNo)?.spans?.size ?: -1

@JsExport
fun hyppSpanText(handle: Int, index: Int, lineNo: Int, spanNo: Int): String = spanAt(handle, index, lineNo, spanNo)?.text ?: ""

/** [de.rholambdapi.hypp.TextStyle.bits] — bits 0-5 attribute flags, 8-11 foreground index, 12-15 background index. */
@JsExport
fun hyppSpanStyleBits(handle: Int, index: Int, lineNo: Int, spanNo: Int): Int = spanAt(handle, index, lineNo, spanNo)?.style?.bits ?: -1

/** -1 no link, 0 [LinkKind.LINK], 1 [LinkKind.ALINK]. The link's label is always its span's own text. */
@JsExport
fun hyppSpanLinkKind(handle: Int, index: Int, lineNo: Int, spanNo: Int): Int =
    when (spanAt(handle, index, lineNo, spanNo)?.link?.kind) {
        LinkKind.LINK -> 0
        LinkKind.ALINK -> 1
        null -> -1
    }

@JsExport
fun hyppSpanLinkTarget(handle: Int, index: Int, lineNo: Int, spanNo: Int): Int =
    spanAt(handle, index, lineNo, spanNo)?.link?.target?.value ?: -1

@JsExport
fun hyppSpanLinkLineNumber(handle: Int, index: Int, lineNo: Int, spanNo: Int): Int =
    spanAt(handle, index, lineNo, spanNo)?.link?.lineNumber ?: -1

@JsExport
fun hyppGraphicCount(handle: Int, index: Int): Int = node(handle, index)?.graphics?.size ?: -1

/** 0 [Graphic.Image], 1 [Graphic.Line], 2 [Graphic.Box], 3 [Graphic.RoundedBox], -1 no such graphic. */
@JsExport
fun hyppGraphicKind(handle: Int, index: Int, graphicNo: Int): Int = when (graphicAt(handle, index, graphicNo)) {
    is Graphic.Image -> 0
    is Graphic.Line -> 1
    is Graphic.Box -> 2
    is Graphic.RoundedBox -> 3
    null -> -1
}

@JsExport
fun hyppGraphicX(handle: Int, index: Int, graphicNo: Int): Int = graphicAt(handle, index, graphicNo)?.x ?: -1

@JsExport
fun hyppGraphicY(handle: Int, index: Int, graphicNo: Int): Int = graphicAt(handle, index, graphicNo)?.y ?: -1

@JsExport
fun hyppGraphicWidth(handle: Int, index: Int, graphicNo: Int): Int = graphicAt(handle, index, graphicNo)?.width ?: -1

@JsExport
fun hyppGraphicHeight(handle: Int, index: Int, graphicNo: Int): Int = graphicAt(handle, index, graphicNo)?.height ?: -1

/** [Graphic.Image.imageIndex]; -1 for any other graphic kind. */
@JsExport
fun hyppGraphicImageIndex(handle: Int, index: Int, graphicNo: Int): Int =
    (graphicAt(handle, index, graphicNo) as? Graphic.Image)?.imageIndex?.value ?: -1

/** [Graphic.Line]'s flags repacked as bit0 arrowAtStart, bit1 arrowAtEnd, remaining bits lineStyle; -1 otherwise. */
@JsExport
fun hyppGraphicLineFlags(handle: Int, index: Int, graphicNo: Int): Int =
    (graphicAt(handle, index, graphicNo) as? Graphic.Line)?.let {
        (if (it.arrowAtStart) 1 else 0) or (if (it.arrowAtEnd) 2 else 0) or (it.lineStyle shl 2)
    } ?: -1

/** [Graphic.Box.fillPattern] / [Graphic.RoundedBox.fillPattern]; -1 for any other graphic kind. */
@JsExport
fun hyppGraphicFillPattern(handle: Int, index: Int, graphicNo: Int): Int = when (val g = graphicAt(handle, index, graphicNo)) {
    is Graphic.Box -> g.fillPattern
    is Graphic.RoundedBox -> g.fillPattern
    else -> -1
}

@JsExport
fun hyppDiagnosticCount(handle: Int): Int = doc(handle)?.diagnostics?.size ?: -1

/**
 * Tag order: 0 [Diagnostic.UnsupportedCharset], 1 [Diagnostic.DecompressionFailed],
 * 2 [Diagnostic.NodeDataOverrun], 3 [Diagnostic.CrossReferenceLimitExceeded],
 * 4 [Diagnostic.UnknownEscape], 5 [Diagnostic.UnterminatedLine], 6 [Diagnostic.DanglingNodeReference].
 */
@JsExport
fun hyppDiagnosticKind(handle: Int, diagnosticNo: Int): Int = when (doc(handle)?.diagnostics?.getOrNull(diagnosticNo)) {
    is Diagnostic.UnsupportedCharset -> 0
    is Diagnostic.DecompressionFailed -> 1
    is Diagnostic.NodeDataOverrun -> 2
    is Diagnostic.CrossReferenceLimitExceeded -> 3
    is Diagnostic.UnknownEscape -> 4
    is Diagnostic.UnterminatedLine -> 5
    is Diagnostic.DanglingNodeReference -> 6
    null -> -1
}

/** -1 for [Diagnostic.UnsupportedCharset], the one variant with no node index. */
@JsExport
fun hyppDiagnosticNodeIndex(handle: Int, diagnosticNo: Int): Int = when (val d = doc(handle)?.diagnostics?.getOrNull(diagnosticNo)) {
    is Diagnostic.UnsupportedCharset -> -1
    is Diagnostic.DecompressionFailed -> d.index.value
    is Diagnostic.NodeDataOverrun -> d.index.value
    is Diagnostic.CrossReferenceLimitExceeded -> d.index.value
    is Diagnostic.UnknownEscape -> d.index.value
    is Diagnostic.UnterminatedLine -> d.index.value
    is Diagnostic.DanglingNodeReference -> d.index.value
    null -> -1
}

/** The diagnostic's secondary numeric field (count/code/target); -1 where it has none. */
@JsExport
fun hyppDiagnosticExtra(handle: Int, diagnosticNo: Int): Int = when (val d = doc(handle)?.diagnostics?.getOrNull(diagnosticNo)) {
    is Diagnostic.CrossReferenceLimitExceeded -> d.count
    is Diagnostic.UnknownEscape -> d.code
    is Diagnostic.DanglingNodeReference -> d.target
    else -> -1
}

/** [Diagnostic.UnsupportedCharset.name]; "" for any other diagnostic kind. */
@JsExport
fun hyppDiagnosticText(handle: Int, diagnosticNo: Int): String =
    (doc(handle)?.diagnostics?.getOrNull(diagnosticNo) as? Diagnostic.UnsupportedCharset)?.name ?: ""
