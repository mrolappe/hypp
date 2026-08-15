package de.rholambdapi.hypp

sealed interface OpenOutcome {
    data class Success(val document: HypDocument) : OpenOutcome
    data class Failure(val reason: OpenFailure) : OpenOutcome
}

sealed interface OpenFailure {
    data object InvalidMagic : OpenFailure
}
