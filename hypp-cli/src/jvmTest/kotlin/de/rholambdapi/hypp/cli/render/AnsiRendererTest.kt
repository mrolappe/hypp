package de.rholambdapi.hypp.cli.render

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end snapshot over textattr.hyp — captured once from a real run (its single "Main" node
 * exercises normal/light/bold/underlined x3/italic/outlined/shadowed text) and hand-verified:
 * "fetter" (bold) carries SGR 1, "unterstrichener" (underlined) SGR 4, "kursiver" (italic) SGR 3;
 * light/outlined/shadowed have no ANSI equivalent (decision 9, doc/PLAN-12-19.md) so "heller"
 * (light), "umrandeter" (outlined) and "schattierter" (shadowed) render with the same no-code SGR
 * as plain text.
 */
class AnsiRendererTest {
    private val esc = 27.toChar()
    private fun sgr(code: String) = "$esc[${code}m"
    private val reset = sgr("0")
    private val plain = sgr("")

    @Test
    fun rendersTextattrEndToEnd() {
        val expected = buildString {
            appendLine("Main")
            appendLine("${plain}Dies ist normaler Text.$reset")
            appendLine("${plain}Dies ist $reset${plain}heller$reset${plain} Text.$reset")
            appendLine("${plain}Dies ist $reset${sgr("1")}fetter$reset${plain} Text.$reset")
            appendLine("${plain}Dies ist $reset${sgr("4")}unterstrichener$reset${plain} Text.$reset")
            appendLine("${plain}    $reset${sgr("4")}Unterstrichener$reset${plain} Text am Zeilenanfang.$reset")
            appendLine("${plain}Dies ist $reset${sgr("4")}  auch   unterstrichener  $reset${plain} Text.$reset")
            appendLine("${plain}Dies ist $reset${sgr("3")}kursiver$reset${plain} Text.$reset")
            appendLine("${plain}Dies ist $reset${plain}umrandeter$reset${plain} Text.$reset")
            appendLine("${plain}Dies ist $reset${plain}schattierter$reset${plain} Text.$reset")
        }

        val document = Corpus.open("textattr")
        assertEquals(expected, AnsiRenderer.render(document))
    }
}
