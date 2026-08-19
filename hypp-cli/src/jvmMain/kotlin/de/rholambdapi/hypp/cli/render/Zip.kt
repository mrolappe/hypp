package de.rholambdapi.hypp.cli.render

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** EPUB's ZIP packaging (decision 6, `doc/PLAN-12-19.md`) — JVM-only, `java.util.zip` is enough. */
fun zip(files: List<RenderedFile>): ByteArray {
    val buffer = ByteArrayOutputStream()
    ZipOutputStream(buffer).use { out ->
        for (file in files) {
            out.putNextEntry(ZipEntry(file.path))
            out.write(file.bytes)
            out.closeEntry()
        }
    }
    return buffer.toByteArray()
}
