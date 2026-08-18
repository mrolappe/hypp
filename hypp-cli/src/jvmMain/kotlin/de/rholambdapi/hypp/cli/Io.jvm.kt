package de.rholambdapi.hypp.cli

import java.io.File

actual fun readBytes(path: String): ByteArray = File(path).readBytes()

actual fun writeBytes(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}
