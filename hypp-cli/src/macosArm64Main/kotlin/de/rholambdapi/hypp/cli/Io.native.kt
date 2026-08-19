@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.rholambdapi.hypp.cli

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.stderr

// Kotlin/Native's stdlib (this Kotlin version) has no java.io-style file API; `platform.posix`
// interop (fopen/fread/fwrite/fclose) is the plain, no-dependency way to do this, confirmed by
// compiling against it (see doc/progress/phase-19-macos-arm64.md).

actual fun readBytes(path: String): ByteArray {
    val file = fopen(path, "rb") ?: error("cannot open file for reading: $path")
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        fseek(file, 0, SEEK_SET)
        if (size <= 0L) return ByteArray(0)
        val bytes = ByteArray(size.toInt())
        bytes.usePinned { pinned ->
            val read = fread(pinned.addressOf(0), 1uL, size.toULong(), file)
            if (read != size.toULong()) error("short read for $path: expected $size, got $read")
        }
        return bytes
    } finally {
        fclose(file)
    }
}

// java.lang.System (JVM's stdout/stderr) doesn't exist on Kotlin/Native: `print`/`println` write
// to stdout, but stderr needs its own POSIX call — same reasoning as wasmWasi's printErrorLine,
// just via libc's `stderr` FILE* instead of a raw fd_write syscall.
fun printError(message: String) {
    fputs(message, stderr)
}

fun printErrorLine(message: String) {
    fputs(message + "\n", stderr)
}

actual fun writeBytes(path: String, bytes: ByteArray) {
    val file = fopen(path, "wb") ?: error("cannot open file for writing: $path")
    try {
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                val written = fwrite(pinned.addressOf(0), 1uL, bytes.size.toULong(), file)
                if (written != bytes.size.toULong()) error("short write for $path")
            }
        }
    } finally {
        fclose(file)
    }
}
