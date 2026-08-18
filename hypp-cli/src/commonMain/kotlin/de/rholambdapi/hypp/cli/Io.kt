package de.rholambdapi.hypp.cli

expect fun readBytes(path: String): ByteArray

expect fun writeBytes(path: String, bytes: ByteArray)
