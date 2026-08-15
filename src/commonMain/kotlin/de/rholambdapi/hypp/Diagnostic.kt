package de.rholambdapi.hypp

/**
 * Something the parser noticed that isn't fatal to opening the document. A
 * total parse never fails on account of a diagnostic — see [OpenOutcome].
 */
sealed interface Diagnostic {
    /** An `@charset` name (extended header id 30) not among v1's supported set. */
    data class UnsupportedCharset(val name: String) : Diagnostic
}
