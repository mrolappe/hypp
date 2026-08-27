package de.rholambdapi.hypp.cli.render

import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun everyNodeGetsACustomIdMatchingItsIndex() {
        val document = Corpus.open("textattr")
        val output = OrgRenderer.render(document)

        for (node in document.nodes) {
            assertTrue(
                output.contains(":CUSTOM_ID: ${node.index.value}"),
                "missing CUSTOM_ID for node ${node.index.value} (${node.name})",
            )
        }
    }

    // --- Group F: targets with no section of their own ---

    @Test
    fun aPopupBecomesAQuoteBlockAndNotASectionOfItsOwn() {
        val output = OrgRenderer.render(StubTargetFixture.document())

        assertTrue(output.contains("*Pop*\n\n#+BEGIN_QUOTE\npopup body\n#+END_QUOTE\n"), output)
        // Before Group F the popup was a `** Pop` section reached by a `[[#1][Pop]]` fuzzy link.
        assertFalse(output.contains("** Pop"), output)
        assertFalse(output.contains("[[#1]"), output)
    }

    @Test
    fun externalRefsAndSystemActionsAreParentheticalsRatherThanDeadFuzzyLinks() {
        val output = OrgRenderer.render(StubTargetFixture.document())

        assertTrue(output.contains("*RefLink* /(external reference: reflink.hyp/Main)/"), output)
        assertTrue(
            output.contains("*Quit* /(viewer action, not available in this document: stool.Tos)/"),
            output,
        )
        assertFalse(output.contains("[[#2]"), output)
        assertFalse(output.contains("[[#3]"), output)
        assertTrue(output.contains("[[#4][Other]]"), output)
    }

    @Test
    fun aRefNameCarryingOrgEmphasisMarkersIsEscapedTheOrgWay() {
        // Org's convention, not HTML's: a backslash before a marker that could *open* emphasis.
        // The closing `*` is left alone deliberately — with nothing opened it renders literally.
        val output = OrgRenderer.render(StubTargetFixture.document(refName = "*evil*.hyp/Main"))

        assertTrue(output.contains("/(external reference: \\*evil*.hyp/Main)/"), output)
        assertFalse(output.contains("reference: *evil*"), output)
    }
}
