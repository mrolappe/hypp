package de.rholambdapi.hypp.cli.render

import kotlin.test.Test
import kotlin.test.assertTrue

class OrgRendererTest {
    @Test
    fun rendersTextattrWithOrgTokens() {
        val document = Corpus.open("textattr")
        val output = OrgRenderer.render(document)

        // Verify heading format (level 2 = **)
        assertTrue(output.contains("** Main"), "Should contain level 2 heading")

        // Verify Org-mode tokens for bold (*), italic (/), and underline (_)
        assertTrue(output.contains("*"), "Should contain bold markers")
        assertTrue(output.contains("/"), "Should contain italic markers")
        assertTrue(output.contains("_"), "Should contain underline markers")
    }

    @Test
    fun rendersLinkattrWithLinks() {
        val document = Corpus.open("linkattr")
        val output = OrgRenderer.render(document)

        // Verify Org syntax [[#target][label]]
        assertTrue(output.contains("[[#"), "Should contain link opening with fragment")
        assertTrue(output.contains("]["), "Should contain link separator")
        assertTrue(output.contains("]]"), "Should contain link closing")
    }
}
