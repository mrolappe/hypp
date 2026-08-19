package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

interface Renderer {
    fun render(document: HypDocument): String
}

data class RenderedFile(val path: String, val bytes: ByteArray)

interface ArchiveRenderer {
    fun render(document: HypDocument): List<RenderedFile>
}

/**
 * Factory, not a fixed instance: [imageEncoder] is swappable per platform (decision 10,
 * `doc/PLAN-12-19.md`) — the JVM composition root (`Main.kt`) picks [ImageIoPngEncoder], nothing
 * in `commonMain` or [Commands.kt] needs to know that.
 */
fun defaultRenderers(imageEncoder: ImageEncoder = StoredPngEncoder): Map<String, Renderer> = mapOf(
    "html" to HtmlRenderer(imageEncoder),
    "markdown" to MarkdownRenderer,
    "asciidoc" to AsciiDocRenderer,
    "org" to OrgRenderer,
    "ansi" to AnsiRenderer,
)

fun defaultArchiveRenderers(imageEncoder: ImageEncoder = StoredPngEncoder): Map<String, ArchiveRenderer> = mapOf(
    "epub" to EpubRenderer(imageEncoder),
)
