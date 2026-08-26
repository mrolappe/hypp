package de.rholambdapi.hypp

/**
 * Report-only raw-byte scan of every `ESC 0x33/0x34/0x35` (line/box/rbox) graphic record in the
 * vendored `st-guide_orig_en.hyp` fixture, run via `./gradlew lineGraphicScan`. Same shape as
 * [main]'s `ESC 0xa4` scan in `CorpusSweep.kt` — network-free and never part of `build`/`check`,
 * kept as ongoing evidence for the width/x-length encoding documented in `doc/format-notes.md`.
 */
fun main() {
    val bytes = TestCorpus.stGuideOrigEn
    val container = parseContainer(bytes) ?: error("could not parse container")

    val widthByteCounts = sortedMapOf<Int, Int>()
    for (e in container.entries) {
        if (e.type != IndexEntry.TYPE_INTERNAL && e.type != IndexEntry.TYPE_POPUP) continue
        val data = decompressEntry(bytes, e) ?: continue
        fun u8(at: Int) = data[at].toInt() and 0xFF

        // Walk the prologue exactly as parseNode does, so record starts are real, not scavenged.
        var pos = 0
        val rows = mutableListOf<String>()
        prologue@ while (pos + 1 < data.size && u8(pos) == ESC_SCAN) {
            when (val type = u8(pos + 1)) {
                0x23 -> {
                    var end = pos + 2
                    while (end < data.size && data[end].toInt() != 0) end++
                    if (end >= data.size) break@prologue
                    pos = end + 1
                }

                in 0x28..0x30 -> {
                    if (pos + 3 > data.size) break@prologue
                    val length = u8(pos + 2)
                    if (length < 3 || pos + length > data.size) break@prologue
                    pos += length
                }

                0x31 -> { if (pos + 10 > data.size) break@prologue; pos += 10 }
                0x32 -> { if (pos + 9 > data.size) break@prologue; pos += 9 }

                in 0x33..0x35 -> {
                    if (pos + 8 > data.size) break@prologue
                    val body = (2..7).map { u8(pos + it) }
                    val kind = when (type) { 0x33 -> "line"; 0x34 -> "box "; else -> "rbox" }
                    val x = body[0]
                    val y = (body[2] - 1) * 255 + (body[1] - 1)
                    val w = body[3]
                    val h = body[4]
                    if (type == 0x33) widthByteCounts.merge(w, 1, Int::plus)
                    rows += "  $kind raw=[${body.joinToString(" ") { it.toString().padStart(3) }}]" +
                        " x=$x y=$y wByte=$w h=$h flags=${body[5]}" +
                        "  →  signed(w-128)=${w - 128} twosComplement=${w.toByte().toInt()}" +
                        " base255(w,h)=${(h - 1) * 255 + (w - 1)}"
                    pos += 8
                }

                else -> break@prologue
            }
        }
        if (rows.isNotEmpty()) {
            println("node \"${e.name}\" (type=${e.type}):")
            rows.forEach(::println)
        }
    }

    println()
    println("=== line width-byte histogram (byte -> count, and byte-128) ===")
    widthByteCounts.forEach { (b, n) -> println("  byte=$b (byte-128=${b - 128}): $n") }
}

private const val ESC_SCAN = 0x1b
