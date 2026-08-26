package de.rholambdapi.hypp

/**
 * Report-only scan of the multi-plane images in the vendored `st-guide_orig_en.hyp` fixture, run
 * via `./gradlew imagePlaneScan`. Same shape as [LineGraphicScan]'s raw-byte scan — network-free
 * and never part of `build`/`check`, kept as ongoing evidence for the bitplane pixel-value
 * encoding documented in `doc/format-notes.md`.
 */
fun main() {
    val outcome = HypDocument.open(TestCorpus.stGuideOrigEn)
    val document = (outcome as OpenOutcome.Success).document

    for (image in document.images) {
        if (image.planeCount == 1) continue
        println("image ${image.index.value} \"${image.name}\" ${image.width}x${image.height}" +
            " planeCount=${image.planeCount} planePresent=${image.planePresent} planeFilled=${image.planeFilled}")

        val pens = image.pixels.map { it.toInt() and 0xFF }
        val counts = pens.groupingBy { it }.eachCount().toSortedMap()
        println("  raw pen histogram (pen -> count, current VDI-as-pen reading, ST-pen→VDI reading):")
        counts.forEach { (pen, n) ->
            val asIs = HypColor.byIndex(pen)?.name ?: "?"
            val remapped = HypColor.byIndex(stPenToVdi16[pen])?.name ?: "?"
            val pct = "%.2f".format(100.0 * n / pens.size)
            println("    pen=${pen.toString().padStart(2)} (${pen.toString(2).padStart(4, '0')})" +
                " count=${n.toString().padStart(7)} ($pct%)  as-is=$asIs  st→vdi=$remapped")
        }

        for (pen in counts.keys) {
            var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if (pens[y * image.width + x] == pen) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
            println("  pen=$pen bbox x=$minX..$maxX y=$minY..$maxY")
        }

        // Downsampled art: one char per block, the block's most common pen.
        val block = 6
        val glyphs = "0123456789ABCDEF"
        println("  art (1 char = ${block}x$block px, glyph = dominant pen):")
        for (by in 0 until image.height step block) {
            val row = StringBuilder("    ")
            for (bx in 0 until image.width step block) {
                val tally = IntArray(16)
                for (y in by until minOf(by + block, image.height))
                    for (x in bx until minOf(bx + block, image.width)) tally[pens[y * image.width + x]]++
                val dominant = tally.indices.maxBy { tally[it] }
                row.append(if (dominant == 0) '.' else glyphs[dominant])
            }
            println(row)
        }
        println()
        val rgba = image.toRgba()
        val bi = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val i = (y * image.width + x) * 4
            bi.setRGB(x, y, ((rgba[i].toInt() and 0xFF) shl 16) or ((rgba[i + 1].toInt() and 0xFF) shl 8) or (rgba[i + 2].toInt() and 0xFF))
        }
        val out = java.io.File("build/imagePlaneScan/image-${image.index.value}.png")
        out.parentFile.mkdirs()
        javax.imageio.ImageIO.write(bi, "png", out)
        println("  wrote $out")
    }
}

/**
 * Atari ST/GEM 16-colour VDI-index ↔ hardware-palette-register mapping, inverted: hardware pen
 * (what a bitplane pixel value actually is) → VDI colour index (what [HypColor]'s ordinal is).
 */
private val stPenToVdi16 = intArrayOf(0, 2, 3, 6, 4, 7, 5, 8, 9, 10, 11, 14, 12, 15, 13, 1)
