package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.HypColor
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end colour check for the only multi-plane image in the vendored corpus:
 * `st-guide_orig_en.hyp`'s 528x153 4-plane "ST-Guide" banner (image 77). Its four bitplane pen
 * values are Atari ST hardware palette registers, not GEM VDI colour indices — reading them as
 * VDI indices rendered the round "documentation" stamp dark red instead of dark green and the
 * "fairware from holger weets" subtitle dark magenta instead of black, the two symptoms reported
 * against `hypview`'s rendering. `javax.imageio.ImageIO` is the independent oracle (same pattern
 * as [StoredPngEncoderTest]) — assert on what a real PNG reader sees, not on our own buffers.
 */
class StGuideBannerColorTest {
    private val banner = Corpus.open("st-guide_orig_en").images.single { it.planeCount > 1 }

    private fun decode(png: ByteArray): Map<HypColor, List<Pair<Int, Int>>> {
        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(png)), "not a valid PNG")
        assertEquals(banner.width, decoded.width)
        assertEquals(banner.height, decoded.height)
        val byColor = mutableMapOf<HypColor, MutableList<Pair<Int, Int>>>()
        for (y in 0 until decoded.height) for (x in 0 until decoded.width) {
            val argb = decoded.getRGB(x, y)
            val color = HypColor.entries.singleOrNull {
                it.red == (argb ushr 16) and 0xFF && it.green == (argb ushr 8) and 0xFF && it.blue == argb and 0xFF
            }
            byColor.getOrPut(assertNotNull(color, "off-palette colour at ($x,$y)")) { mutableListOf() } += x to y
        }
        return byColor
    }

    @Test
    fun bannerIsBlueOnWhiteWithAGreenStampAndABlackSubtitle() {
        for (encoder in listOf(StoredPngEncoder, ImageIoPngEncoder)) {
            val byColor = decode(encoder.encode(banner))

            assertEquals(
                mapOf(
                    HypColor.WHITE to 56188,   // pen 0  — background
                    HypColor.BLUE to 16613,    // pen 4  — the "ST-Guide" wordmark
                    HypColor.DARK_GREEN to 6177, // pen 10 — the round stamp (was DARK_RED)
                    HypColor.BLACK to 1806,    // pen 15 — the subtitle (was DARK_MAGENTA)
                ),
                byColor.mapValues { it.value.size },
                "${encoder::class.simpleName}: unexpected banner palette",
            )

            // The two fixed colours must also land where those elements are, not merely be present:
            // the stamp on the right-hand third, the subtitle on a thin band under the wordmark.
            val stamp = assertNotNull(byColor[HypColor.DARK_GREEN])
            assertTrue(stamp.all { (x, _) -> x >= banner.width / 2 }, "stamp pixels outside the right half")
            assertEquals(3 to 149, stamp.minOf { it.second } to stamp.maxOf { it.second })

            val subtitle = assertNotNull(byColor[HypColor.BLACK])
            assertEquals(115 to 137, subtitle.minOf { it.second } to subtitle.maxOf { it.second })
        }
    }
}
