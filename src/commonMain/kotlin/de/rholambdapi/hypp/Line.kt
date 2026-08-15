package de.rholambdapi.hypp

/** One NUL-terminated line of a node's text region, as a flat run of styled [Span]s. */
data class Line(val spans: List<Span>) {
    /** The line's text with all styling and links dropped. */
    val text: String get() = spans.joinToString("") { it.text }
}

/**
 * A run of text sharing one [TextStyle]. A span carrying a [link] holds that link's whole label
 * and nothing else, since a link never straddles a style change.
 */
data class Span(val text: String, val style: TextStyle, val link: Link? = null)
