package de.rholambdapi.hypp

import de.rholambdapi.hypp.internal.decodeName

enum class NodeKind { TEXT, POPUP }

/**
 * A fully parsed internal ([NodeKind.TEXT]) or popup ([NodeKind.POPUP]) node's prologue
 * (data-region items a-e: graphics, cross-references, further data blocks, window title,
 * object table). [textBytes] is the still-undecoded remainder (item f) — line/span parsing
 * lands in a later phase.
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
    val textBytes: ByteArray,
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

private const val MAX_CROSS_REFERENCES = 12

/** Decodes a format base-255 value: two bytes, low digit first, each biased by +1 to avoid NUL. */
private fun decodeBase255(lo: Int, hi: Int): Int = (hi - 1) * 255 + (lo - 1)

internal fun parseNode(
    index: NodeIndex,
    name: String,
    kind: NodeKind,
    data: ByteArray,
    diagnostics: MutableList<Diagnostic>,
): Node {
    var pos = 0
    var windowTitle: String? = null
    val graphics = ArrayList<Graphic>()
    val crossReferences = ArrayList<CrossReference>()
    val dataBlocks = ArrayList<DataBlock>()
    val objectTable = ArrayList<ObjectTableEntry>()
    var pendingDitherMask: ByteArray? = null

    fun u8(at: Int) = data[at].toInt() and 0xFF

    prologue@ while (pos + 1 < data.size && u8(pos) == ESC) {
        val type = u8(pos + 1)
        when {
            type == ESC_WINDOW_TITLE -> {
                var end = pos + 2
                while (end < data.size && data[end].toInt() != 0) end++
                if (end >= data.size) {
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    break@prologue
                }
                windowTitle = data.copyOfRange(pos + 2, end).decodeName()
                pos = end + 1
            }

            type in ESC_DATA_FIRST..ESC_DATA_LAST -> {
                if (pos + 3 > data.size) {
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    break@prologue
                }
                val length = u8(pos + 2)
                if (length < 3 || pos + length > data.size) {
                    diagnostics += Diagnostic.NodeDataOverrun(index)
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
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    break@prologue
                }
                val length = u8(pos + 2)
                if (length < 5 || pos + length > data.size) {
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    break@prologue
                }
                val target = NodeIndex(decodeBase255(u8(pos + 3), u8(pos + 4)))
                val text = data.copyOfRange(pos + 5, pos + length).decodeName()
                crossReferences += CrossReference(target, text)
                pos += length
            }

            type == ESC_OBJECT_TABLE -> {
                if (pos + 10 > data.size) {
                    diagnostics += Diagnostic.NodeDataOverrun(index)
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
                    diagnostics += Diagnostic.NodeDataOverrun(index)
                    break@prologue
                }
                val imageIndex = NodeIndex(decodeBase255(u8(pos + 2), u8(pos + 3)))
                val x = u8(pos + 4)
                val y = decodeBase255(u8(pos + 5), u8(pos + 6))
                val width = u8(pos + 7)
                val height = u8(pos + 8)
                graphics += Graphic.Image(imageIndex, x, y, width, height, pendingDitherMask)
                pendingDitherMask = null
                pos += 9
            }

            type in ESC_LINE..ESC_ROUNDED_BOX -> {
                if (pos + 8 > data.size) {
                    diagnostics += Diagnostic.NodeDataOverrun(index)
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
        textBytes = data.copyOfRange(pos, data.size),
    ).also {
        if (crossReferences.size > MAX_CROSS_REFERENCES) {
            diagnostics += Diagnostic.CrossReferenceLimitExceeded(index, crossReferences.size)
        }
    }
}
