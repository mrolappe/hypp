package de.rholambdapi.hypp

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Phase 11 (wild sweep, `doc/PLAN.md`): an opt-in, network-using tool (`./gradlew corpusSweep`,
 * not part of `build`/`check`) that downloads the full public `.hyp` corpus from
 * `tho-otto.m68k.eu/hypview/`, opens every file through the same public [HypDocument.open] entry
 * point real consumers use, and prints a feature histogram (escape/diagnostic codes, charsets,
 * node types, compiler versions) plus a targeted raw-byte scan for `ESC 0xa4` occurrences — the
 * evidence needed to settle the `0xa4` typewriter vs. `0xa5`/`0xa6` colour overlap left open since
 * phase 6. Downloaded files are cached under the working directory so reruns are network-free.
 *
 * Reuses [parseContainer]/[decompressEntry] (factored out of [HypDocument.open] for exactly this)
 * rather than re-deriving the container format, since the raw-byte scan needs decompressed node
 * bytes that the public model doesn't expose.
 */
private const val LISTING_URL = "https://tho-otto.m68k.eu/hypview/"
private const val BASE_URL = "https://tho-otto.m68k.eu"

fun main(args: Array<String>) {
    val cacheDir = File(args.getOrElse(0) { "build/corpusSweep/cache" })
    cacheDir.mkdirs()
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    val listingFile = File(cacheDir, "_index.html")
    val listingHtml = if (listingFile.exists()) listingFile.readText()
    else (fetchBytes(client, LISTING_URL)?.decodeToString() ?: error("could not fetch $LISTING_URL"))
        .also { listingFile.writeText(it) }

    val urls = Regex("""/hyp/[A-Za-z0-9_.\-]+\.hyp""").findAll(listingHtml).map { it.value }.toSortedSet().toList()
    println("corpus listing: ${urls.size} files")

    var fetchFailures = 0
    var crashes = 0
    var opened = 0
    val openFailures = sortedMapOf<String, Int>()
    val entryTypeCounts = sortedMapOf<Int, Int>()
    val nodeKindCounts = sortedMapOf<String, Int>()
    val compilerVersionCounts = sortedMapOf<Int, Int>()
    val compilerOsCounts = sortedMapOf<Int, Int>()
    val charsetCounts = sortedMapOf<String, Int>()
    val extendedHeaderIdCounts = sortedMapOf<Int, Int>()
    val diagnosticCounts = sortedMapOf<String, Int>()
    val unknownEscapeCodeCounts = sortedMapOf<Int, Int>()

    // Targeted evidence for the 0xa4 ambiguity (doc/format-notes.md): every ESC(0x1b) 0xa4 pair
    // found in any decompressed node/popup/image object, and what byte follows it.
    var esc0xa4Occurrences = 0
    val esc0xa4FollowingByteCounts = sortedMapOf<Int, Int>()
    val esc0xa4Examples = mutableListOf<String>()

    for ((i, relUrl) in urls.withIndex()) {
        val name = relUrl.substringAfterLast('/')
        val localFile = File(cacheDir, name)
        val bytes = if (localFile.exists()) localFile.readBytes() else {
            val body = fetchBytes(client, BASE_URL + relUrl)
            if (body == null) {
                fetchFailures++
                println("fetch failed: $relUrl")
                null
            } else {
                localFile.writeBytes(body)
                body
            }
        } ?: continue

        try {
            when (val outcome = HypDocument.open(bytes)) {
                is OpenOutcome.Failure -> openFailures.merge(outcome.reason::class.simpleName ?: "?", 1, Int::plus)
                is OpenOutcome.Success -> {
                    opened++
                    val doc = outcome.document
                    compilerVersionCounts.merge(doc.header.compilerVersion, 1, Int::plus)
                    compilerOsCounts.merge(doc.header.compilerOs, 1, Int::plus)
                    doc.entries.forEach { entryTypeCounts.merge(it.type, 1, Int::plus) }
                    doc.nodes.forEach { nodeKindCounts.merge(it.kind.name, 1, Int::plus) }
                    val charsetName =
                        doc.extendedHeaders.filterIsInstance<ExtendedHeader.Charset>().firstOrNull()?.name ?: "(default)"
                    charsetCounts.merge(charsetName, 1, Int::plus)
                    doc.extendedHeaders.forEach { extendedHeaderIdCounts.merge(it.id, 1, Int::plus) }
                    doc.diagnostics.forEach { d ->
                        diagnosticCounts.merge(d::class.simpleName ?: "?", 1, Int::plus)
                        if (d is Diagnostic.UnknownEscape) unknownEscapeCodeCounts.merge(d.code, 1, Int::plus)
                    }

                    // Restricted to text/popup entries: image raster data coincidentally contains the
                    // byte pair 0x1b 0xa4 often enough to swamp the signal this scan is looking for.
                    val container = parseContainer(bytes)
                    container?.entries.orEmpty().forEach { e ->
                        if (e.type != IndexEntry.TYPE_INTERNAL && e.type != IndexEntry.TYPE_POPUP) return@forEach
                        val data = decompressEntry(bytes, e) ?: return@forEach
                        var p = 0
                        while (p + 1 < data.size) {
                            if ((data[p].toInt() and 0xFF) == 0x1b && (data[p + 1].toInt() and 0xFF) == 0xa4) {
                                esc0xa4Occurrences++
                                val next = if (p + 2 < data.size) data[p + 2].toInt() and 0xFF else -1
                                esc0xa4FollowingByteCounts.merge(next, 1, Int::plus)
                                if (esc0xa4Examples.size < 40) {
                                    val ctxFrom = (p - 20).coerceAtLeast(0)
                                    val ctxTo = (p + 20).coerceAtMost(data.size)
                                    val context = data.copyOfRange(ctxFrom, ctxTo).joinToString("") { b ->
                                        val v = b.toInt() and 0xFF
                                        if (v in 0x20..0x7e) v.toChar().toString() else "·"
                                    }
                                    esc0xa4Examples += "$name entry=${e.name} offset=$p next=0x${next.toString(16)} context=[$context]"
                                }
                            }
                            p++
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            crashes++
            println("CRASH opening $name: ${t::class.simpleName}: ${t.message}")
        }

        if ((i + 1) % 100 == 0) println("... ${i + 1}/${urls.size}")
    }

    println()
    println("=== hypp phase 11 wild-sweep report ===")
    println("files in listing: ${urls.size}, fetch failures: $fetchFailures, opened: $opened, crashes: $crashes")
    println("open() failures: $openFailures")
    println()
    println("entry type counts (see IndexEntry.TYPE_* for codes): $entryTypeCounts")
    println("node kind counts: $nodeKindCounts")
    println("compiler version counts: $compilerVersionCounts")
    println("compiler os counts: $compilerOsCounts")
    println("charset counts: $charsetCounts")
    println("extended header id counts: $extendedHeaderIdCounts")
    println()
    println("diagnostic counts: $diagnosticCounts")
    println("UnknownEscape code counts (hex would read: ${unknownEscapeCodeCounts.keys.map { "0x${it.toString(16)}" }}): $unknownEscapeCodeCounts")
    println()
    println("ESC 0xa4 occurrences: $esc0xa4Occurrences")
    println("ESC 0xa4 following-byte histogram: $esc0xa4FollowingByteCounts")
    esc0xa4Examples.forEach { println("  example: $it") }
}

private fun fetchBytes(client: HttpClient, url: String): ByteArray? = try {
    val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    if (response.statusCode() == 200) response.body() else null
} catch (e: Exception) {
    null
}
