package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.ImageNode

/** Swappable per platform (decision 10, `doc/PLAN-12-19.md`) without touching any renderer. */
interface ImageEncoder {
    fun encode(image: ImageNode): ByteArray
}
