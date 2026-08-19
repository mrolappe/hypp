package de.rholambdapi.hypp.cli

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the built fat jar as a real, separate JVM process end to end (`dump <fixture> --format
 * html`) — proves the `Main-Class` manifest entry, the merged classpath, and the whole
 * ArgParser -> Io -> HypDocument.open -> Commands -> stdout pipeline actually work outside the
 * test JVM, not just when driven directly as Kotlin. The `hypp-cli` Gradle build wires the
 * `jvmTest` task to depend on `fatJar` (see `build.gradle.kts`), so the jar this test runs is
 * always freshly built, not stale.
 */
class FatJarSmokeTest {
    @Test
    fun runningTheFatJarDumpsHtmlToStdout() {
        val jar = File("build/libs/hypp-cli-all.jar")
        assertTrue(jar.exists(), "fat jar not found at ${jar.absolutePath} — did the fatJar task run?")

        val fixture = File("src/commonTest/resources/corpus/textattr.hyp")
        assertTrue(fixture.exists(), "corpus fixture not found at ${fixture.absolutePath}")

        val process = ProcessBuilder(
            "java", "-jar", jar.absolutePath, "dump", fixture.absolutePath, "--format", "html",
        ).redirectErrorStream(false).start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertEquals(0, exitCode, "process failed, stderr:\n$stderr")
        assertTrue(stdout.contains("<!doctype html>"), "expected HTML-looking stdout, got:\n$stdout")
        assertTrue(stdout.isNotBlank())
    }
}
