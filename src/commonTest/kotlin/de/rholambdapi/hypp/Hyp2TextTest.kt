package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The standing phase-6 integration test: the [hyp2text] renderer driven over whole real
 * documents through the public API only, on all three targets.
 */
class Hyp2TextTest {
    private fun open(bytes: ByteArray): HypDocument {
        val outcome = HypDocument.open(bytes)
        assertIs<OpenOutcome.Success>(outcome)
        return outcome.document
    }

    @Test
    fun rendersTheMainNodeOfARealDocument() {
        val rendered = hyp2text(open(TestCorpus.hcpOrigEn))
        val main = rendered.split("\n== ").first()

        assertTrue(main.startsWith("== node Main [Documentation for HCP]\n"), main.take(80))
        // One centred image placeholder and nine decorative box-drawing lines (phase 5), then
        // the three cross-references, then the text region.
        assertEquals(10, main.lines().count { it.startsWith("  <image") || it.startsWith("  <line") })
        assertEquals(
            listOf(
                "  <xref -> 123:  ST-Guide Documentation>",
                "  <xref -> 121:  STool Documentation>",
                "  <xref -> 122:  RefLink Documentation>",
            ),
            main.lines().filter { it.startsWith("  <xref") },
        )

        assertEquals(
            listOf(
                "     [Calling HCP -> 4]",
                "     [Options overview -> 5]",
                "     [Commands in a hypertext -> 39]",
                "",
                "     [Tasks and properties of the compiler -> 31]",
                "     [Writing a hypertext -> 2]",
                "     [Technical -> 32]",
                "     [File-types -> 3]",
                "",
                "     [Legal -> 120]",
                "     [Credits -> 119]",
                "",
            ),
            // The block's own trailing blank line is the renderer's node separator, not content.
            main.substringAfter("  <xref -> 122:  RefLink Documentation>\n").removeSuffix("\n").lines(),
        )
    }

    @Test
    fun rendersEveryNodeOfBothRealDocumentsWithoutLosingContent() {
        for ((bytes, expectedNodes) in listOf(TestCorpus.hcpOrigEn to 104, TestCorpus.stGuideOrigEn to 63)) {
            val document = open(bytes)
            assertEquals(expectedNodes, document.nodes.size)

            val rendered = hyp2text(document)
            assertEquals(expectedNodes, rendered.lines().count { it.startsWith("== node ") || it.startsWith("== popup ") })
            // Every node's every line reaches the output; the renderer adds one header line and
            // one blank separator line per node.
            assertEquals(
                document.nodes.sumOf { n -> n.lines.size + n.graphics.size + n.crossReferences.size + 2 },
                rendered.lines().size - 1,
            )
            assertTrue(rendered.isNotEmpty())
        }
    }

    @Test
    fun rendersStyledAndColouredRunsWithTheirMarkers() {
        val textattr = hyp2text(open(TestCorpus.textattr))
        assertTrue(textattr.contains("Dies ist <u>unterstrichener</> Text."), textattr)
        assertTrue(textattr.contains("Dies ist <b>fetter</> Text."), textattr)
        val colors = hyp2text(open(TestCorpus.colors))
        assertTrue(colors.contains("hello <fg=RED>red world</>"), colors)
        assertTrue(colors.contains("hello <fg=WHITE,bg=BLACK>white world</>"), colors)
    }
}
