package de.rholambdapi.hypp.cli.render

import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun everyNodeGetsAnAnchorMatchingItsIndex() {
        val document = Corpus.open("textattr")
        val output = AsciiDocRenderer.render(document)

        for (node in document.nodes) {
            assertTrue(
                output.contains("[#${node.index.value}]"),
                "missing block anchor for node ${node.index.value} (${node.name})",
            )
        }
    }

    // --- Group F: targets with no section of their own ---

    @Test
    fun aPopupBecomesANoteAdmonitionAndNotASectionOfItsOwn() {
        val output = AsciiDocRenderer.render(StubTargetFixture.document())

        assertTrue(output.contains("*Pop*\n\n[NOTE]\n====\npopup body\n====\n"), output)
        // Before Group F the popup was a `== Pop` section reached by a `link:#1[Pop]` fragment.
        assertFalse(output.contains("== Pop"), output)
        assertFalse(output.contains("link:#1["), output)
    }

    @Test
    fun externalRefsAndSystemActionsAreParentheticalsRatherThanDeadFragments() {
        val output = AsciiDocRenderer.render(StubTargetFixture.document())

        assertTrue(output.contains("*RefLink* _(external reference: reflink.hyp/Main)_"), output)
        assertTrue(
            output.contains("*Quit* _(viewer action, not available in this document: stool.Tos)_"),
            output,
        )
        assertFalse(output.contains("link:#2["), output)
        assertFalse(output.contains("link:#3["), output)
        assertTrue(output.contains("link:#4[Other]"), output)
    }

    @Test
    fun aRefNameCarryingAsciiDocMetacharactersIsEscapedTheAsciiDocWay() {
        val output = AsciiDocRenderer.render(StubTargetFixture.document(refName = "*b*_i_.hyp/#c#+d+"))

        assertTrue(
            output.contains("_(external reference: \\*b\\*\\_i\\_.hyp/\\#c\\#\\+d\\+)_"),
            output,
        )
    }

    @Test
    fun aRefNameCannotSmuggleRawHtmlThroughAsciiDocsPassthroughMacro() {
        // `pass:[…]` is AsciiDoc's raw-passthrough macro — the one construct in this dialect that
        // turns document text into live markup in asciidoctor's HTML output.
        val output = AsciiDocRenderer.render(StubTargetFixture.document(refName = "pass:[<script>alert(1)</script>]"))

        assertFalse(output.contains("pass:[<script>"), output)
        assertTrue(output.contains("pass:\\[<script>alert(1)</script>\\]"), output)
    }
}
