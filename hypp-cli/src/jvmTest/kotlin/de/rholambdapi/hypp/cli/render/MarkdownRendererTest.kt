package de.rholambdapi.hypp.cli.render

import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownRendererTest {
    @Test
    fun rendersTextattrWithMarkdownTokens() {
        val document = Corpus.open("textattr")
        val output = MarkdownRenderer.render(document)

        // Verify heading format (level 2 = ##)
        assertTrue(output.contains("## Main"), "Should contain level 2 heading")

        // Verify CommonMark tokens for bold (**), italic (*), and underline (<u>)
        assertTrue(output.contains("**"), "Should contain bold markers")
        assertTrue(output.contains("*"), "Should contain italic markers")
        assertTrue(output.contains("<u>"), "Should contain underline open tag")
        assertTrue(output.contains("</u>"), "Should contain underline close tag")
    }

    @Test
    fun rendersLinkattrWithLinks() {
        val document = Corpus.open("linkattr")
        val output = MarkdownRenderer.render(document)

        // Verify CommonMark link syntax [label](#target)
        assertTrue(output.contains("["), "Should contain link opening bracket")
        assertTrue(output.contains("](#"), "Should contain link format with fragment")
    }

    @Test
    fun everyNodeGetsAnAnchorMatchingItsIndex() {
        val document = Corpus.open("textattr")
        val output = MarkdownRenderer.render(document)

        for (node in document.nodes) {
            assertTrue(
                output.contains("<a id=\"${node.index.value}\"></a>"),
                "missing anchor for node ${node.index.value} (${node.name})",
            )
        }
    }
}
