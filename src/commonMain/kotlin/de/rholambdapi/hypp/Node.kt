package de.rholambdapi.hypp

import de.rholambdapi.hypp.internal.decodeName

enum class NodeKind { TEXT, POPUP }

/**
 * A fully parsed internal ([NodeKind.TEXT]) or popup ([NodeKind.POPUP]) node: the prologue
 * (data-region items a-e: graphics, cross-references, further data blocks, window title,
 * object table) followed by [lines], the decoded text region (item f).
 */
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

/**
 * Prologue escape types, in the order the prose spec (`hypfmt.ui`) enumerates them as a-e.
 * A real file does *not* emit them in this order — `hcp_orig_en.hyp`'s first node has its
 * window title before its graphics — so this parses them as a set of independently optional,
 * self-identifying records rather than a fixed sequence; see `doc/format-notes.md`.
 */
private const val ESC = 0x1b
private const val ESC_WINDOW_TITLE = 0x23
private const val ESC_DATA_FIRST = 0x28
private const val ESC_DATA_LAST = 0x2f
private const val ESC_DITHERMASK = 0x2f
private const val ESC_CROSS_REFERENCE = 0x30
private const val ESC_OBJECT_TABLE = 0x31
private const val ESC_IMAGE = 0x32
private const val ESC_LINE = 0x33
private const val ESC_BOX = 0x34
private const val ESC_ROUNDED_BOX = 0x35

/**
 * Text-region escape types (item f). Disjoint from the prologue's range, which is what makes the
 * prologue's "stop at the first unrecognized escape" rule unambiguous.
 */
private const val ESC_LINK = 0x24
private const val ESC_LINK_LINE = 0x25
private const val ESC_ALINK = 0x26
private const val ESC_ALINK_LINE = 0x27
private const val ESC_TEXTATTR_FIRST = 0x64
private const val ESC_TEXTATTR_LAST = 0xa3
private const val ESC_NO_EFFECT = 0xa4
private const val ESC_FG_COLOR = 0xa5
private const val ESC_BG_COLOR = 0xa6

/** A link's label-length byte is biased by 32; exactly 32 means "use the target's own name". */
private const val LABEL_LENGTH_BIAS = 32

private const val MAX_CROSS_REFERENCES = 12

/** Decodes a format base-255 value: two bytes, low digit first, each biased by +1 to avoid NUL. */
private fun decodeBase255(lo: Int, hi: Int): Int = (hi - 1) * 255 + (lo - 1)

internal fun parseNode(
    index: NodeIndex,
    name: String,
    kind: NodeKind,
    data: ByteArray,
    diagnostics: MutableList<Diagnostic>,
    charset: HypCharset = HypCharset.Default,
    entryNames: List<String> = emptyList(),
): Node {
    var pos = 0
    var windowTitle: String? = null
    val graphics = ArrayList<Graphic>()
    val crossReferences = ArrayList<CrossReference>()
    val dataBlocks = ArrayList<DataBlock>()
    val objectTable = ArrayList<ObjectTableEntry>()
    var pendingDitherMask: ByteArray? = null

    fun u8(at: Int) = data[at].toInt() and 0xFF

    /** A base-255 field can only decode negative on malformed data — [NodeIndex] rejects that. */
    fun nodeIndexAt(at: Int): NodeIndex? {
        val value = decodeBase255(u8(at), u8(at + 1))
        if (value < 0) {
            diagnostics += Diagnostic.DanglingNodeReference(index, value)
            return null
        }
        return NodeIndex(value)
    }

    prologue@ while (pos + 1 < data.size && u8(pos) == ESC) {
        val type = u8(pos + 1)
        when {
            type == ESC_WINDOW_TITLE -> {
                var end = pos + 2
                while (end < data.size && data[end].toInt() != 0) end++
                if (end >= data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                windowTitle = data.copyOfRange(pos + 2, end).decodeName()
                pos = end + 1
            }

            type in ESC_DATA_FIRST..ESC_DATA_LAST -> {
                if (pos + 3 > data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                val length = u8(pos + 2)
                if (length < 3 || pos + length > data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                val payload = data.copyOfRange(pos + 3, pos + length)
                if (type == ESC_DITHERMASK) {
                    // An earlier dithermask that no image ever claimed is not lost — it surfaces
                    // as an ordinary data block once superseded by this one.
                    pendingDitherMask?.let { dataBlocks += DataBlock(ESC_DITHERMASK, it) }
                    pendingDitherMask = payload
                } else {
                    dataBlocks += DataBlock(type, payload)
                }
                pos += length
            }

            type == ESC_CROSS_REFERENCE -> {
                if (pos + 5 > data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                val length = u8(pos + 2)
                if (length < 5 || pos + length > data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                val target = nodeIndexAt(pos + 3)
                val text = data.copyOfRange(pos + 5, pos + length).decodeName()
                if (target != null) crossReferences += CrossReference(target, text)
                pos += length
            }

            type == ESC_OBJECT_TABLE -> {
                if (pos + 10 > data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                objectTable += ObjectTableEntry(
                    lineNumber = decodeBase255(u8(pos + 2), u8(pos + 3)),
                    tree = decodeBase255(u8(pos + 4), u8(pos + 5)),
                    obj = decodeBase255(u8(pos + 6), u8(pos + 7)),
                    pageIndex = decodeBase255(u8(pos + 8), u8(pos + 9)),
                )
                pos += 10
            }

            type == ESC_IMAGE -> {
                if (pos + 9 > data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                val imageIndex = nodeIndexAt(pos + 2)
                val x = u8(pos + 4)
                val y = decodeBase255(u8(pos + 5), u8(pos + 6))
                val width = u8(pos + 7)
                val height = u8(pos + 8)
                if (imageIndex != null) {
                    graphics += Graphic.Image(imageIndex, x, y, width, height, pendingDitherMask)
                }
                pendingDitherMask = null
                pos += 9
            }

            type in ESC_LINE..ESC_ROUNDED_BOX -> {
                if (pos + 8 > data.size) {
                    // The record is truncated, so the text region's start is unknowable too:
                    // stop here rather than reinterpreting prologue bytes as text.
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    pos = data.size
                    break@prologue
                }
                val x = u8(pos + 2)
                val y = decodeBase255(u8(pos + 3), u8(pos + 4))
                val width = u8(pos + 5)
                val height = u8(pos + 6)
                val flags = u8(pos + 7)
                graphics += when (type) {
                    ESC_LINE -> Graphic.Line(
                        x, y, width, height,
                        arrowAtStart = flags and 1 != 0, arrowAtEnd = flags and 2 != 0, lineStyle = flags shr 2,
                    )
                    ESC_BOX -> Graphic.Box(x, y, width, height, fillPattern = flags)
                    else -> Graphic.RoundedBox(x, y, width, height, fillPattern = flags)
                }
                pos += 8
            }

            else -> break@prologue
        }
    }
    pendingDitherMask?.let { dataBlocks += DataBlock(ESC_DITHERMASK, it) }

    return Node(
        index = index,
        name = name,
        kind = kind,
        windowTitle = windowTitle,
        graphics = graphics,
        crossReferences = crossReferences,
        dataBlocks = dataBlocks,
        objectTable = objectTable,
        lines = parseLines(index, data, pos, charset, entryNames, diagnostics),
    ).also {
        if (crossReferences.size > MAX_CROSS_REFERENCES) {
            diagnostics += Diagnostic.CrossReferenceLimitExceeded(index, crossReferences.size)
        }
    }
}

/**
 * Parses node data item f — NUL-terminated lines of text carrying `ESC`-headed attribute, colour
 * and link escapes — into [Line]s of styled [Span]s.
 *
 * Line splitting is escape-aware rather than a plain split on NUL: a colour escape's parameter is
 * a raw palette index, and index 0 (white) is a literal `0x00` byte in the stream. See
 * `doc/format-notes.md`.
 */
private fun parseLines(
    index: NodeIndex,
    data: ByteArray,
    from: Int,
    charset: HypCharset,
    entryNames: List<String>,
    diagnostics: MutableList<Diagnostic>,
): List<Line> {
    val lines = ArrayList<Line>()
    var spans = ArrayList<Span>()
    val pending = StringBuilder()
    var style = TextStyle.Normal
    var pos = from
    var runStart = from
    var truncated = false

    fun u8(at: Int) = data[at].toInt() and 0xFF

    /** Decodes the plain-text run ending at [at] into [pending] — one charset decode per run. */
    fun endRun(at: Int) {
        if (at > runStart) pending.append(charset.decode(data.copyOfRange(runStart, at)))
    }

    fun flushSpan(at: Int) {
        endRun(at)
        if (pending.isNotEmpty()) {
            spans.add(Span(pending.toString(), style))
            pending.clear()
        }
    }

    text@ while (pos < data.size) {
        if (u8(pos) != ESC) {
            if (data[pos].toInt() == 0) {
                flushSpan(pos)
                lines += Line(spans)
                spans = ArrayList()
                pos++
                runStart = pos
            } else {
                pos++
            }
            continue
        }
        if (pos + 1 >= data.size) {
            truncated = true
            break@text
        }
        when (val type = u8(pos + 1)) {
            ESC -> {
                // ESC ESC is a literal ESC character, mid-run: the run continues after it.
                endRun(pos)
                pending.append(Char(ESC))
                pos += 2
                runStart = pos
            }

            ESC_LINK, ESC_LINK_LINE, ESC_ALINK, ESC_ALINK_LINE -> {
                flushSpan(pos)
                val hasLineNumber = type == ESC_LINK_LINE || type == ESC_ALINK_LINE
                var p = pos + 2
                if (p + (if (hasLineNumber) 5 else 3) > data.size) {
                    truncated = true
                    break@text
                }
                val lineNumber = if (hasLineNumber) decodeBase255(u8(p), u8(p + 1)).also { p += 2 } else null
                val target = decodeBase255(u8(p), u8(p + 1)); p += 2
                val rawLength = u8(p); p++
                val useTargetName = rawLength <= LABEL_LENGTH_BIAS
                val labelLength = if (useTargetName) 0 else rawLength - LABEL_LENGTH_BIAS
                if (p + labelLength > data.size) {
                    truncated = true
                    break@text
                }
                val resolved = entryNames.getOrNull(target)
                if (resolved == null) diagnostics += Diagnostic.DanglingNodeReference(index, target)
                val label = if (useTargetName) resolved.orEmpty()
                else charset.decode(data.copyOfRange(p, p + labelLength))
                p += labelLength
                val link = if (resolved == null) null
                else Link(
                    kind = if (type == ESC_LINK || type == ESC_LINK_LINE) LinkKind.LINK else LinkKind.ALINK,
                    target = NodeIndex(target),
                    lineNumber = lineNumber,
                    label = label,
                )
                if (label.isNotEmpty() || link != null) spans.add(Span(label, style, link))
                pos = p
                runStart = pos
            }

            in ESC_TEXTATTR_FIRST..ESC_TEXTATTR_LAST -> {
                flushSpan(pos)
                style = style.withAttributes(type - ESC_TEXTATTR_FIRST)
                pos += 2
                runStart = pos
            }

            // Documented as having no visual effect. Not an unknown escape, and not a diagnostic.
            ESC_NO_EFFECT -> {
                endRun(pos)
                pos += 2
                runStart = pos
            }

            ESC_FG_COLOR, ESC_BG_COLOR -> {
                if (pos + 2 >= data.size) {
                    truncated = true
                    break@text
                }
                val color = HypColor.byIndex(u8(pos + 2))
                if (color == null) {
                    diagnostics += Diagnostic.UnknownEscape(index, type)
                } else {
                    flushSpan(pos)
                    style = if (type == ESC_FG_COLOR) style.withForeground(color) else style.withBackground(color)
                }
                pos += 3
                runStart = pos
            }

            else -> {
                diagnostics += Diagnostic.UnknownEscape(index, type)
                endRun(pos)
                pos += 2
                runStart = pos
            }
        }
    }

    flushSpan(pos.coerceAtMost(data.size))
    if (spans.isNotEmpty() || truncated) {
        lines += Line(spans)
        diagnostics += Diagnostic.UnterminatedLine(index)
    }
    return lines
}
