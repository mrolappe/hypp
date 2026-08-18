package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument

/** Trivial renderer proving the registry/interfaces round-trip end to end; real renderers land in Phase 15. */
object IdentityRenderer : Renderer {
    override fun render(document: HypDocument): String = document.nodes.size.toString()
}
