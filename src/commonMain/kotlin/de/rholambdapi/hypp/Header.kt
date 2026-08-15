package de.rholambdapi.hypp

/** The 12-byte `HDOC` file header. */
data class Header(
    val itableSize: Int,
    val itableCount: Int,
    val compilerVersion: Int,
    val compilerOs: Int,
)
