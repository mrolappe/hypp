package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.Diagnostic
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.IndexEntry
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.ResolvedTarget
import de.rholambdapi.hypp.TocEntry
import de.rholambdapi.hypp.resolve
import de.rholambdapi.hypp.cli.render.ArchiveRenderer
import de.rholambdapi.hypp.cli.render.ImageEncoder
import de.rholambdapi.hypp.cli.render.RenderedFile
import de.rholambdapi.hypp.cli.render.Renderer

/** One file this command wants written by the caller (`Main.kt` does the actual I/O via `Io.kt`). */
data class OutputFile(val path: String, val bytes: ByteArray)

/**
 * A command's outcome: [exitCode] for the process, [stdout]/[stderr] text for the caller to print,
 * and [files] for the caller to write. Kept commonMain-testable — no direct file I/O happens here.
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val files: List<OutputFile> = emptyList(),
)

private const val EXIT_OK = 0
private const val EXIT_FAILURE = 1
private const val EXIT_USAGE = 2

/**
 * Renders [document] as [format]. Text formats print to stdout unless [out] names a file; the
 * `epub` archive format is always written to [out] (there's no sensible way to print a zip to a
 * terminal) — passing `--format epub` without `--out` is a usage error, not a crash. [zip] is
 * injected because [de.rholambdapi.hypp.cli.render.zip] is jvmMain-only (`java.util.zip`) while
 * this function is commonMain. When [reflowParagraphs] is set, [reflow] runs once up front so
 * every format (text or archive) sees the same joined-paragraph text.
 */
fun dump(
    document: HypDocument,
    format: String,
    out: String?,
    renderers: Map<String, Renderer>,
    archiveRenderers: Map<String, ArchiveRenderer>,
    zip: (List<RenderedFile>) -> ByteArray,
    reflowParagraphs: Boolean = false,
): CommandResult {
    val doc = if (reflowParagraphs) reflow(document) else document

    archiveRenderers[format]?.let { archiveRenderer ->
        if (out == null) {
            return CommandResult(exitCode = EXIT_USAGE, stderr = "dump --format $format requires --out\n")
        }
        val bytes = zip(archiveRenderer.render(doc))
        return CommandResult(exitCode = EXIT_OK, files = listOf(OutputFile(out, bytes)))
    }

    val renderer = renderers[format]
        ?: return CommandResult(exitCode = EXIT_USAGE, stderr = "unknown format: $format\n")
    val rendered = renderer.render(doc)
    return if (out != null) {
        CommandResult(exitCode = EXIT_OK, files = listOf(OutputFile(out, rendered.encodeToByteArray())))
    } else {
        CommandResult(exitCode = EXIT_OK, stdout = rendered)
    }
}

private fun Diagnostic.isHard(): Boolean = when (this) {
    is Diagnostic.UnsupportedCharset, is Diagnostic.DecompressionFailed, is Diagnostic.NodeDataOverrun -> true
    is Diagnostic.CrossReferenceLimitExceeded, is Diagnostic.UnknownEscape,
    is Diagnostic.UnterminatedLine, is Diagnostic.DanglingNodeReference -> false
}

private fun Diagnostic.describe(): String {
    val severity = if (isHard()) "hard" else "info"
    val detail = when (this) {
        is Diagnostic.UnsupportedCharset -> "unsupported charset: $name"
        is Diagnostic.DecompressionFailed -> "decompression failed at node ${index.value}"
        is Diagnostic.NodeDataOverrun -> "node data overrun at node ${index.value}"
        is Diagnostic.CrossReferenceLimitExceeded ->
            "cross-reference limit exceeded at node ${index.value} (count=$count)"
        is Diagnostic.UnknownEscape -> "unknown escape code $code at node ${index.value}"
        is Diagnostic.UnterminatedLine -> "unterminated line at node ${index.value}"
        is Diagnostic.DanglingNodeReference -> "dangling reference to $target at node ${index.value}"
    }
    return "[$severity] $detail"
}

/**
 * Diagnostics are CLI-local policy, not pushed into `commonMain`'s neutral model (decision 15,
 * `doc/PLAN-12-19.md`): [Diagnostic.UnsupportedCharset], [Diagnostic.DecompressionFailed] and
 * [Diagnostic.NodeDataOverrun] represent lost/corrupted content and are "hard" — anything else is
 * informational. Without `--strict`, only a hard diagnostic fails the command; with `--strict`,
 * any diagnostic does.
 */
fun validate(document: HypDocument, strict: Boolean): CommandResult {
    val diagnostics = document.diagnostics
    val stdout = if (diagnostics.isEmpty()) "no diagnostics\n" else diagnostics.joinToString("\n", postfix = "\n") { it.describe() }
    val hasHard = diagnostics.any { it.isHard() }
    val exitCode = when {
        strict && diagnostics.isNotEmpty() -> EXIT_FAILURE
        !strict && hasHard -> EXIT_FAILURE
        else -> EXIT_OK
    }
    return CommandResult(exitCode = exitCode, stdout = stdout)
}

private fun typeName(type: Int): String = when (type) {
    IndexEntry.TYPE_INTERNAL -> "INTERNAL"
    IndexEntry.TYPE_POPUP -> "POPUP"
    IndexEntry.TYPE_EXTERNAL_REF -> "EXTERNAL_REF"
    IndexEntry.TYPE_IMAGE -> "IMAGE"
    IndexEntry.TYPE_SYSTEM -> "SYSTEM"
    IndexEntry.TYPE_REXX_SCRIPT -> "REXX_SCRIPT"
    IndexEntry.TYPE_REXX_COMMAND -> "REXX_COMMAND"
    IndexEntry.TYPE_QUIT -> "QUIT"
    IndexEntry.TYPE_CLOSE -> "CLOSE"
    IndexEntry.TYPE_EOF -> "EOF"
    else -> "UNKNOWN($type)"
}

private fun ResolvedTarget.kindName(): String = when (this) {
    is ResolvedTarget.ToNode -> "ToNode"
    is ResolvedTarget.ToImage -> "ToImage"
    is ResolvedTarget.ToExternalRef -> "ToExternalRef"
    is ResolvedTarget.ToSystemAction -> "ToSystemAction"
    ResolvedTarget.Missing -> "Missing"
}

private fun StringBuilder.appendToc(document: HypDocument, entry: TocEntry, depth: Int) {
    val name = document.entry(entry.index)?.name ?: "?"
    append("  ".repeat(depth)).append("[").append(entry.index.value).append("] ").appendLine(name)
    for (child in entry.children) appendToc(document, child, depth + 1)
}

/**
 * Summarizes [document]: header, extended headers, table of contents, entry counts by type,
 * image count, and a [ResolvedTarget] breakdown over every entry (Phase 13's model) — no `.REF`
 * file is loaded here, so external refs necessarily stay unresolved; this just demonstrates the
 * shape, not full cross-document resolution.
 */
fun inspect(document: HypDocument): CommandResult {
    val sb = StringBuilder()
    sb.appendLine("Header:")
    sb.append("  itableSize=").appendLine(document.header.itableSize)
    sb.append("  itableCount=").appendLine(document.header.itableCount)
    sb.append("  compilerVersion=").appendLine(document.header.compilerVersion)
    sb.append("  compilerOs=").appendLine(document.header.compilerOs)
    sb.appendLine()

    sb.append("Extended headers (").append(document.extendedHeaders.size).appendLine("):")
    for (header in document.extendedHeaders) sb.append("  ").appendLine(header)
    sb.appendLine()

    sb.appendLine("Table of contents:")
    sb.appendToc(document, document.tableOfContents(), 1)
    sb.appendLine()

    sb.appendLine("Entries by type:")
    val byType = document.entries.groupingBy { typeName(it.type) }.eachCount()
    for ((type, count) in byType.entries.sortedByDescending { it.value }) {
        sb.append("  ").append(type).append(": ").appendLine(count)
    }
    sb.appendLine()

    sb.append("Images: ").appendLine(document.images.size)
    sb.appendLine()

    val resolved = document.entries.indices.map { document.resolve(NodeIndex(it)) }
    sb.appendLine("Link resolution:")
    val byKind = resolved.groupingBy { it.kindName() }.eachCount()
    for ((kind, count) in byKind.entries.sortedByDescending { it.value }) {
        sb.append("  ").append(kind).append(": ").appendLine(count)
    }
    val externalRefs = resolved.filterIsInstance<ResolvedTarget.ToExternalRef>().map { it.ref }
    if (externalRefs.isNotEmpty()) {
        sb.appendLine("  Unresolved external refs (no .REF file loaded):")
        for (ref in externalRefs) {
            sb.append("    ").append(ref.fileName ?: "?").append("/").appendLine(ref.nodeName)
        }
    }

    return CommandResult(exitCode = EXIT_OK, stdout = sb.toString())
}

/**
 * The final path segment of a document-derived image [name], or a synthetic `image-<index>` name
 * if that segment is empty, `.` or `..`. [name] comes from an attacker-controlled `.hyp` file (the
 * index-entry name) and is about to be joined with a caller-supplied output directory to build a
 * filesystem write path — this is the path-traversal / absolute-path-escape guard for that sink.
 * The output directory itself is trusted operator input and is deliberately left untouched.
 */
internal fun sanitizeImageFileName(name: String, index: NodeIndex): String {
    val lastSegment = name.split('/', '\\').lastOrNull { it.isNotEmpty() }
    return if (lastSegment == null || lastSegment == "." || lastSegment == "..") "image-${index.value}" else lastSegment
}

private fun joinPath(directory: String, fileName: String): String =
    if (directory.endsWith("/") || directory.endsWith("\\")) directory + fileName else "$directory/$fileName"

/** Encodes every image in [document] via [imageEncoder] and writes it as `<sanitized-name>.png` under [out]. */
fun extractImages(document: HypDocument, out: String, imageEncoder: ImageEncoder): CommandResult {
    val files = document.images.map { image ->
        val fileName = sanitizeImageFileName(image.name, image.index) + ".png"
        OutputFile(joinPath(out, fileName), imageEncoder.encode(image))
    }
    return CommandResult(exitCode = EXIT_OK, stdout = "extracted ${files.size} image(s) to $out\n", files = files)
}
