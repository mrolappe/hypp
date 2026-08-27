package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.IndexEntry
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Link
import de.rholambdapi.hypp.LinkKind
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle

/**
 * A synthetic document holding one of each target a link can point at that has no section of its
 * own — a popup node, an external ref and a system action — plus an ordinary node to link to.
 * The corpus has no file whose entry names carry dialect metacharacters, so [refName]/[popupText]
 * are parameters: that is how each renderer's escaping is exercised against its own `escape`.
 */
object StubTargetFixture {
    const val POPUP_LABEL = "Pop"
    const val REF_LABEL = "RefLink"
    const val ACTION_LABEL = "Quit"

    fun document(
        refName: String = "reflink.hyp/Main",
        popupText: String = "popup body",
        actionName: String = "stool.Tos",
    ): HypDocument {
        val entries = listOf(
            entry("Home", IndexEntry.TYPE_INTERNAL),
            entry("Pop", IndexEntry.TYPE_POPUP),
            entry(refName, IndexEntry.TYPE_EXTERNAL_REF),
            entry(actionName, IndexEntry.TYPE_QUIT),
            entry("Other", IndexEntry.TYPE_INTERNAL),
        )
        val home = node(
            0, "Home", NodeKind.TEXT,
            listOf(
                Line(listOf(linkSpan(1, POPUP_LABEL))),
                Line(listOf(linkSpan(2, REF_LABEL))),
                Line(listOf(linkSpan(3, ACTION_LABEL))),
                Line(listOf(linkSpan(4, "Other"))),
            ),
        )
        val popup = node(1, "Pop", NodeKind.POPUP, listOf(Line(listOf(Span(popupText, TextStyle.Normal)))))
        val other = node(4, "Other", NodeKind.TEXT, listOf(Line(listOf(Span("tail", TextStyle.Normal)))))
        return HypDocument(
            header = Header(itableSize = 0, itableCount = entries.size, compilerVersion = 3, compilerOs = 2),
            extendedHeaders = emptyList(),
            entries = entries,
            charset = HypCharset.Default,
            nodes = listOf(home, popup, other),
            images = emptyList(),
            diagnostics = emptyList(),
        )
    }

    private fun entry(name: String, type: Int) = IndexEntry(
        len = 0, type = type, seek = 0, compDiff = 0,
        next = 0, prev = 0, toc = 0, name = name, compressedLength = 0,
    )

    private fun node(index: Int, name: String, kind: NodeKind, lines: List<Line>) = Node(
        index = NodeIndex(index), name = name, kind = kind, windowTitle = null,
        graphics = emptyList(), crossReferences = emptyList(), dataBlocks = emptyList(),
        objectTable = emptyList(), lines = lines,
    )

    private fun linkSpan(target: Int, label: String) =
        Span(label, TextStyle.Normal, Link(LinkKind.LINK, NodeIndex(target), null, label))
}
