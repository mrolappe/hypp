package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.cli.reflow
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `st-guide_orig_en.hyp`'s "Introduction" node is the real fixture that surfaced the bug: a
 * literal byte `0x03` embedded in its text broke XHTML well-formedness, and a strict XML reader
 * (e.g. Apple Books/Preview) renders only up to that point — which looks like the document got
 * truncated. A JVM `DocumentBuilder` is the same class of strict parser those readers use, so a
 * document that fails to parse here would fail there too.
 */
class EpubRendererWellFormednessTest {
    private fun parse(xhtml: ByteArray) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xhtml))
    }

    @Test
    fun everyNodeOfTheRealCorpusFixtureIsWellFormedXhtml() {
        val document = Corpus.open("st-guide_orig_en")

        for (file in EpubRenderer().render(document)) {
            if (!file.path.endsWith(".xhtml")) continue
            try {
                parse(file.bytes)
            } catch (e: Exception) {
                throw AssertionError("${file.path} is not well-formed XML: ${e.message}", e)
            }
        }
    }

    @Test
    fun realCorpusLineAndBoxGraphicsRenderAsWellFormedInlineSvg() {
        val document = Corpus.open("st-guide_orig_en")
        val introduction = document.nodes.single { it.name == "Introduction" }

        val xhtml = EpubRenderer().render(document)
            .single { it.path == "OEBPS/node-${introduction.index.value}.xhtml" }

        parse(xhtml.bytes)
        val text = xhtml.bytes.decodeToString()
        assertTrue(text.contains("<line"), text)
        assertTrue(text.contains("<rect"), text)
    }

    @Test
    fun stillWellFormedAfterReflow() {
        val document = reflow(Corpus.open("st-guide_orig_en"))

        for (file in EpubRenderer().render(document)) {
            if (!file.path.endsWith(".xhtml")) continue
            try {
                parse(file.bytes)
            } catch (e: Exception) {
                throw AssertionError("${file.path} is not well-formed XML: ${e.message}", e)
            }
        }
    }
}
