package de.rholambdapi.hypp.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgParserTest {
    // Happy path tests

    @Test
    fun dumpWithoutOptionalFlags() {
        val cmd = parseArgs(arrayOf("dump", "test.hyp"))
        assertEquals(Command.Dump("test.hyp", "html", null), cmd)
    }

    @Test
    fun dumpWithFormat() {
        val cmd = parseArgs(arrayOf("dump", "test.hyp", "--format", "markdown"))
        assertEquals(Command.Dump("test.hyp", "markdown", null), cmd)
    }

    @Test
    fun dumpWithOut() {
        val cmd = parseArgs(arrayOf("dump", "test.hyp", "--out", "result.md"))
        assertEquals(Command.Dump("test.hyp", "html", "result.md"), cmd)
    }

    @Test
    fun dumpWithBothFlags() {
        val cmd = parseArgs(arrayOf("dump", "test.hyp", "--format", "asciidoc", "--out", "result.adoc"))
        assertEquals(Command.Dump("test.hyp", "asciidoc", "result.adoc"), cmd)
    }

    @Test
    fun dumpWithFlagsInReverseOrder() {
        val cmd = parseArgs(arrayOf("dump", "test.hyp", "--out", "result.adoc", "--format", "org"))
        assertEquals(Command.Dump("test.hyp", "org", "result.adoc"), cmd)
    }

    @Test
    fun dumpDefaultsFormatToHtml() {
        val cmd = parseArgs(arrayOf("dump", "file.hyp"))
        assertEquals("html", (cmd as Command.Dump).format)
    }

    @Test
    fun dumpWithReflowFlag() {
        val cmd = parseArgs(arrayOf("dump", "test.hyp", "--reflow"))
        assertEquals(Command.Dump("test.hyp", "html", null, reflow = true), cmd)
    }

    @Test
    fun dumpWithoutReflowFlagDefaultsToFalse() {
        val cmd = parseArgs(arrayOf("dump", "test.hyp"))
        assertEquals(false, (cmd as Command.Dump).reflow)
    }

    @Test
    fun dumpWithAllFormats() {
        val formats = listOf("html", "markdown", "asciidoc", "org", "ansi", "epub")
        for (format in formats) {
            val cmd = parseArgs(arrayOf("dump", "test.hyp", "--format", format))
            assertEquals(format, (cmd as Command.Dump).format)
        }
    }

    @Test
    fun validateWithoutStrictFlag() {
        val cmd = parseArgs(arrayOf("validate", "test.hyp"))
        assertEquals(Command.Validate("test.hyp", false), cmd)
    }

    @Test
    fun validateWithStrictFlag() {
        val cmd = parseArgs(arrayOf("validate", "test.hyp", "--strict"))
        assertEquals(Command.Validate("test.hyp", true), cmd)
    }

    @Test
    fun validateDefaultsStrictToFalse() {
        val cmd = parseArgs(arrayOf("validate", "file.hyp"))
        assertEquals(false, (cmd as Command.Validate).strict)
    }

    @Test
    fun inspectWithoutFlags() {
        val cmd = parseArgs(arrayOf("inspect", "test.hyp"))
        assertEquals(Command.Inspect("test.hyp"), cmd)
    }

    @Test
    fun extractImagesWithoutOut() {
        val cmd = parseArgs(arrayOf("extract-images", "test.hyp"))
        assertEquals(Command.ExtractImages("test.hyp", "."), cmd)
    }

    @Test
    fun extractImagesWithOut() {
        val cmd = parseArgs(arrayOf("extract-images", "test.hyp", "--out", "/tmp/images"))
        assertEquals(Command.ExtractImages("test.hyp", "/tmp/images"), cmd)
    }

    @Test
    fun extractImagesDefaultsOutToDot() {
        val cmd = parseArgs(arrayOf("extract-images", "file.hyp"))
        assertEquals(".", (cmd as Command.ExtractImages).out)
    }

    // Error cases

    @Test
    fun missingCommandThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf())
        }
    }

    @Test
    fun unknownCommandThrows() {
        val ex = assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("foo", "file.hyp"))
        }
        assertEquals(true, ex.message?.contains("Unknown command"))
        assertEquals(true, ex.message?.contains("dump"))
    }

    @Test
    fun dumpMissingFileThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("dump"))
        }
    }

    @Test
    fun validateMissingFileThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("validate"))
        }
    }

    @Test
    fun inspectMissingFileThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("inspect"))
        }
    }

    @Test
    fun extractImagesMissingFileThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("extract-images"))
        }
    }

    @Test
    fun invalidFormatThrows() {
        val ex = assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("dump", "test.hyp", "--format", "pdf"))
        }
        assertEquals(true, ex.message?.contains("invalid format"))
        assertEquals(true, ex.message?.contains("pdf"))
    }

    @Test
    fun formatFlagWithoutValueThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("dump", "test.hyp", "--format"))
        }
    }

    @Test
    fun outFlagWithoutValueThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("dump", "test.hyp", "--out"))
        }
    }

    @Test
    fun extractImagesOutFlagWithoutValueThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("extract-images", "test.hyp", "--out"))
        }
    }

    @Test
    fun unrecognizedFlagForDumpThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("dump", "test.hyp", "--strict"))
        }
    }

    @Test
    fun unrecognizedFlagForValidateThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("validate", "test.hyp", "--format", "html"))
        }
    }

    @Test
    fun unrecognizedFlagForInspectThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("inspect", "test.hyp", "--strict"))
        }
    }

    @Test
    fun unrecognizedFlagForExtractImagesThrows() {
        assertFailsWith<ArgParseException> {
            parseArgs(arrayOf("extract-images", "test.hyp", "--strict"))
        }
    }
}
