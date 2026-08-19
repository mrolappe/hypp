package de.rholambdapi.hypp.cli.render

import kotlin.test.Test
import kotlin.test.assertTrue

class AsciiDocRendererTest {
    @Test
    fun rendersTextattrWithAsciiDocTokens() {
        val document = Corpus.open("textattr")
        val output = AsciiDocRenderer.render(document)

        // Verify heading format (level 2 = ==)
        assertTrue(output.contains("== Main"), "Should contain level 2 heading")

        // Verify AsciiDoc tokens for bold (*), italic (_), and underline ([.underline]#)
        assertTrue(output.contains("*"), "Should contain bold markers")
        assertTrue(output.contains("_"), "Should contain italic markers")
        assertTrue(output.contains("[.underline]#"), "Should contain underline open")
        assertTrue(output.contains("#"), "Should contain underline close")
    }

    @Test
    fun rendersLinkattrWithLinks() {
        val document = Corpus.open("linkattr")
        val output = AsciiDocRenderer.render(document)

        // Verify AsciiDoc link syntax link:#target[label]
        assertTrue(output.contains("link:#"), "Should contain link: prefix with fragment")
        assertTrue(output.contains("["), "Should contain link label brackets")
    }
}
