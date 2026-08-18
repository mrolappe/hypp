package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

interface Renderer {
    fun render(document: HypDocument): String
}

data class RenderedFile(val path: String, val bytes: ByteArray)

interface ArchiveRenderer {
    fun render(document: HypDocument): List<RenderedFile>
}

/** Filled in Phase 15 with the real renderers (HTML/Markdown/etc.). */
val renderers: Map<String, Renderer> = emptyMap()
