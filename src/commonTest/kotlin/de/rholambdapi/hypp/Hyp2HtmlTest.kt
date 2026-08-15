package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The standing phase-7 integration test: the [hyp2html] renderer driven over whole real
 * documents through the public API only, on all three targets.
 */
class Hyp2HtmlTest {
    private fun open(bytes: ByteArray): HypDocument {
        val outcome = HypDocument.open(bytes)
        assertIs<OpenOutcome.Success>(outcome)
        return outcome.document
    }

    @Test
    fun embedsAPlacedImageAsADataUri() {
        val html = hyp2html(open(TestCorpus.image))
        assertTrue(html.contains("<img width=\"216\" height=\"177\" src=\"data:image/bmp;base64,"), html.take(500))
    }

    @Test
    fun rendersColouredSpansAsInlineStyles() {
        val html = hyp2html(open(TestCorpus.colors))
        assertTrue(html.contains("color:rgb(255,0,0)"), html)
    }

    @Test
    fun rendersEveryNodeOfBothRealDocumentsWithoutCrashing() {
        for (bytes in listOf(TestCorpus.hcpOrigEn, TestCorpus.stGuideOrigEn)) {
            val html = hyp2html(open(bytes))
            assertTrue(html.startsWith("<!doctype html>"))
            assertTrue(html.endsWith("</body></html>\n"))
        }
    }
}
