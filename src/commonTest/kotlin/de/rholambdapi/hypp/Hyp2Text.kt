package de.rholambdapi.hypp

/**
 * The phase-6 integration consumer: a plain-text renderer that reaches a document only through
 * the public API — `HypDocument` -> `nodes` -> `lines` -> `spans` -> `links`. Its job is to walk
 * every part of the model end to end, so an awkward or incomplete API shows up here first.
 */
fun hyp2text(document: HypDocument): String = buildString {
    for (node in document.nodes) {
        append(if (node.kind == NodeKind.POPUP) "== popup " else "== node ").append(node.name)
        node.windowTitle?.let { append(" [").append(it).append(']') }
        appendLine()
        for (graphic in node.graphics) appendLine("  <${describe(graphic)}>")
        for (xref in node.crossReferences) appendLine("  <xref -> ${xref.target.value}: ${xref.popupText}>")
        for (line in node.lines) appendLine(render(line))
        appendLine()
    }
}

private fun describe(graphic: Graphic): String {
    val where = "at ${if (graphic.centered) "centre" else graphic.x.toString()},${graphic.y} " +
        "${graphic.width}x${graphic.height}"
    return when (graphic) {
        is Graphic.Image -> "image ${graphic.imageIndex.value} $where"
        is Graphic.Line -> "line $where style=${graphic.lineStyle}" +
            (if (graphic.arrowAtStart) " arrow-start" else "") + (if (graphic.arrowAtEnd) " arrow-end" else "")
        is Graphic.Box -> "box $where fill=${graphic.fillPattern}"
        is Graphic.RoundedBox -> "rbox $where fill=${graphic.fillPattern}"
    }
}

private fun render(line: Line): String = buildString {
    for (span in line.spans) {
        val link = span.link
        if (link != null) {
            append(if (link.kind == LinkKind.ALINK) "@[" else "[")
            append(span.text).append(" -> ").append(link.target.value)
            link.lineNumber?.let { append(':').append(it) }
            append(']')
        } else {
            val marks = markers(span.style)
            if (marks.isEmpty()) append(span.text) else append('<').append(marks).append('>').append(span.text).append("</>")
        }
    }
}

/** Style markers, so styled runs are visible in a snapshot diff without needing colour. */
private fun markers(style: TextStyle): String {
    val parts = ArrayList<String>(3)
    if (style.isBold) parts += "b"
    if (style.isLight) parts += "l"
    if (style.isItalic) parts += "i"
    if (style.isUnderlined) parts += "u"
    if (style.isOutlined) parts += "o"
    if (style.isShadowed) parts += "s"
    if (style.foreground != TextStyle.Normal.foreground) parts += "fg=${style.foreground}"
    if (style.background != TextStyle.Normal.background) parts += "bg=${style.background}"
    return parts.joinToString(",")
}
