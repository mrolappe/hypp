package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.OpenOutcome

/** Loads a `.hyp` fixture vendored under `hypp-cli`'s own `commonTest/resources/corpus/`. */
internal object Corpus {
    fun open(name: String): HypDocument {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/corpus/$name.hyp")) {
            "missing corpus fixture: $name"
        }.readBytes()
        val outcome = HypDocument.open(bytes)
        check(outcome is OpenOutcome.Success) { "failed to open $name: $outcome" }
        return outcome.document
    }
}
