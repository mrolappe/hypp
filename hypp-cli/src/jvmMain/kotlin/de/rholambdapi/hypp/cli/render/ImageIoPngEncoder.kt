package de.rholambdapi.hypp.cli.render

import de.rholambdapi.hypp.ImageNode
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * JVM-only [ImageEncoder] using `javax.imageio.ImageIO` for real deflate compression, versus
 * [StoredPngEncoder]'s uncompressed stored blocks (which exist so `commonMain` renderers work on
 * every target). This is the JVM composition root's (`Main.kt`) default.
 */
object ImageIoPngEncoder : ImageEncoder {
    override fun encode(image: ImageNode): ByteArray {
        val rgba = image.toRgba()
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val i = (y * image.width + x) * 4
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                val a = rgba[i + 3].toInt() and 0xFF
                buffered.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(buffered, "png", out)
        return out.toByteArray()
    }
}
