package de.rholambdapi.hypp

/**
 * One cross-reference data block (prologue item b, escape `0x30`) — up to 12 per node per the
 * prose spec. [popupText] is the NUL-terminated string shown for the reference.
 */
data class CrossReference(val target: NodeIndex, val popupText: String)
