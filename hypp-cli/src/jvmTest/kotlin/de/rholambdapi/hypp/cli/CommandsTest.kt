package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.Diagnostic
import de.rholambdapi.hypp.Header
import de.rholambdapi.hypp.HypCharset
import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.ImageNode
import de.rholambdapi.hypp.Line
import de.rholambdapi.hypp.Node
import de.rholambdapi.hypp.NodeIndex
import de.rholambdapi.hypp.NodeKind
import de.rholambdapi.hypp.Span
import de.rholambdapi.hypp.TextStyle
import de.rholambdapi.hypp.cli.render.Corpus
import de.rholambdapi.hypp.cli.render.StoredPngEncoder
import de.rholambdapi.hypp.cli.render.defaultArchiveRenderers
import de.rholambdapi.hypp.cli.render.defaultRenderers
import de.rholambdapi.hypp.cli.render.zip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandsTest {
    private val renderers = defaultRenderers()
    private val archiveRenderers = defaultArchiveRenderers()

    private fun emptyDocument(diagnostics: List<Diagnostic> = emptyList(), images: List<ImageNode> = emptyList()) = HypDocument(
        header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
        extendedHeaders = emptyList(),
        entries = emptyList(),
        charset = HypCharset.Default,
        nodes = emptyList(),
        images = images,
        diagnostics = diagnostics,
    )

    // --- dump ---

    @Test
    fun dumpRendersToStdoutWhenNoOutGiven() {
        val document = Corpus.open("textattr")
        val result = dump(document, "html", out = null, renderers, archiveRenderers, ::zip)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.startsWith("<!doctype html>"))
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun dumpWritesToOutFileWhenGiven() {
        val document = Corpus.open("textattr")
        val result = dump(document, "html", out = "dump.html", renderers, archiveRenderers, ::zip)

        assertEquals(0, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals(1, result.files.size)
        assertEquals("dump.html", result.files.single().path)
    }

    @Test
    fun dumpEpubWithoutOutIsAUsageErrorNotACrash() {
        val document = Corpus.open("textattr")
        val result = dump(document, "epub", out = null, renderers, archiveRenderers, ::zip)

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.isNotEmpty())
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun dumpEpubWithOutProducesAZip() {
        val document = Corpus.open("textattr")
        val result = dump(document, "epub", out = "book.epub", renderers, archiveRenderers, ::zip)

        assertEquals(0, result.exitCode)
        assertEquals(1, result.files.size)
        val file = result.files.single()
        assertEquals("book.epub", file.path)
        assertTrue(file.bytes.isNotEmpty())
        // local file header signature "PK\x03\x04"
        assertEquals(listOf(0x50, 0x4B, 0x03, 0x04), file.bytes.take(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun dumpReflowsParagraphsBeforeRenderingWhenRequested() {
        val document = HypDocument(
            header = Header(itableSize = 0, itableCount = 0, compilerVersion = 0, compilerOs = 0),
            extendedHeaders = emptyList(),
            entries = emptyList(),
            charset = HypCharset.Default,
            nodes = listOf(
                Node(
                    index = NodeIndex(0),
                    name = "Home",
                    kind = NodeKind.TEXT,
                    windowTitle = null,
                    graphics = emptyList(),
                    crossReferences = emptyList(),
                    dataBlocks = emptyList(),
                    objectTable = emptyList(),
                    lines = listOf(
                        Line(listOf(Span("wrapped", TextStyle.Normal))),
                        Line(listOf(Span("text.", TextStyle.Normal))),
                    ),
                ),
            ),
            images = emptyList(),
            diagnostics = emptyList(),
        )

        val withoutReflow = dump(document, "html", out = null, renderers, archiveRenderers, ::zip)
        val withReflow = dump(document, "html", out = null, renderers, archiveRenderers, ::zip, reflowParagraphs = true)

        // Both rows share one <p> either way (no graphic sits between them) — reflow's own effect
        // is joining their *text* with a space instead of a newline, not changing the <p> count.
        assertEquals(1, Regex("<p>").findAll(withoutReflow.stdout).count())
        assertEquals(1, Regex("<p>").findAll(withReflow.stdout).count())
        assertTrue(withoutReflow.stdout.contains("<p>wrapped\ntext.</p>"))
        assertTrue(withReflow.stdout.contains("<p>wrapped text.</p>"))
    }

    @Test
    fun dumpUnknownFormatIsHandledDefensively() {
        val document = Corpus.open("textattr")
        val result = dump(document, "does-not-exist", out = null, renderers, archiveRenderers, ::zip)

        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.isNotEmpty())
    }

    // --- validate ---

    @Test
    fun validateExitsZeroWithNoDiagnostics() {
        val result = validate(emptyDocument(), strict = false)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun validateExitsZeroForInformationalOnlyWithoutStrict() {
        val document = emptyDocument(diagnostics = listOf(Diagnostic.UnknownEscape(NodeIndex(0), 7)))
        val result = validate(document, strict = false)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("[info]"))
    }

    @Test
    fun validateExitsNonZeroForInformationalOnlyWithStrict() {
        val document = emptyDocument(diagnostics = listOf(Diagnostic.UnknownEscape(NodeIndex(0), 7)))
        val result = validate(document, strict = true)
        assertEquals(1, result.exitCode)
    }

    @Test
    fun validateExitsNonZeroForHardDiagnosticWithoutStrict() {
        val document = emptyDocument(diagnostics = listOf(Diagnostic.NodeDataOverrun(NodeIndex(0))))
        val result = validate(document, strict = false)
        assertEquals(1, result.exitCode)
        assertTrue(result.stdout.contains("[hard]"))
    }

    @Test
    fun validateExitsNonZeroForHardDiagnosticWithStrict() {
        val document = emptyDocument(diagnostics = listOf(Diagnostic.NodeDataOverrun(NodeIndex(0))))
        val result = validate(document, strict = true)
        assertEquals(1, result.exitCode)
    }

    @Test
    fun validateExitsZeroWhenOnlyHardIsAbsentAndInformationalIsPresentWithoutStrict() {
        // Mixed but no hard diagnostic: still fine without --strict.
        val document = emptyDocument(
            diagnostics = listOf(
                Diagnostic.CrossReferenceLimitExceeded(NodeIndex(0), 13),
                Diagnostic.UnterminatedLine(NodeIndex(1)),
            ),
        )
        val result = validate(document, strict = false)
        assertEquals(0, result.exitCode)
    }

    // --- inspect ---

    @Test
    fun inspectSummarizesADocument() {
        val document = Corpus.open("linkattr")
        val result = inspect(document)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Header:"))
        assertTrue(result.stdout.contains("Table of contents:"))
        assertTrue(result.stdout.contains("Entries by type:"))
        assertTrue(result.stdout.contains("Images: ${document.images.size}"))
        assertTrue(result.stdout.contains("Link resolution:"))
    }

    // --- extract-images ---

    private fun image(name: String, index: Int = 0) = ImageNode(
        index = NodeIndex(index), name = name, width = 1, height = 1,
        planeCount = 1, planePresent = 1, planeFilled = 0,
        planeData = byteArrayOf(0x80.toByte(), 0x00),
    )

    @Test
    fun extractImagesWritesOnePngPerImageUnderOut() {
        val document = emptyDocument(images = listOf(image("pic")))
        val result = extractImages(document, out = "out", imageEncoder = StoredPngEncoder)

        assertEquals(0, result.exitCode)
        assertEquals(1, result.files.size)
        assertEquals("out/pic.png", result.files.single().path)
        assertTrue(result.files.single().bytes.isNotEmpty())
    }

    @Test
    fun extractImagesSanitizesRelativeTraversalInDocumentName() {
        val document = emptyDocument(images = listOf(image("../../etc/passwd", index = 5)))
        val result = extractImages(document, out = "out", imageEncoder = StoredPngEncoder)

        val path = result.files.single().path
        assertTrue(path.startsWith("out/"), "expected write confined to out/, was $path")
        assertFalse(path.contains(".."), "sanitized path must not contain a traversal segment: $path")
        assertEquals("out/passwd.png", path)
    }

    @Test
    fun extractImagesSanitizesAbsolutePathInDocumentName() {
        val document = emptyDocument(images = listOf(image("/etc/passwd", index = 5)))
        val result = extractImages(document, out = "out", imageEncoder = StoredPngEncoder)

        val path = result.files.single().path
        assertTrue(path.startsWith("out/"), "expected write confined to out/, was $path")
        assertEquals("out/passwd.png", path)
    }

    @Test
    fun extractImagesFallsBackToASyntheticNameWhenSanitizedNameIsEmptyOrDotDot() {
        val document = emptyDocument(images = listOf(image("../", index = 9)))
        val result = extractImages(document, out = "out", imageEncoder = StoredPngEncoder)

        assertEquals("out/image-9.png", result.files.single().path)
    }
}
