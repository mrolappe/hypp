package de.rholambdapi.hypp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Phase 10 (parity artefacts, `doc/PLAN.md`): the whole micro-corpus, serialized through
 * [HypDocument.toCanonicalJson] and compared byte-for-byte against a checked-in golden under
 * `doc/goldens/` — the source of truth a future Rust port's own goldens are checked against. JVM-only
 * because it needs real filesystem access to the golden files; the writer itself (`CanonicalJson.kt`)
 * is `commonTest` and runs identically on every target.
 */
class ParityGoldenTest {
    private fun open(bytes: ByteArray): HypDocument {
        val outcome = HypDocument.open(bytes)
        assertIs<OpenOutcome.Success>(outcome)
        return outcome.document
    }

    private fun checkGolden(name: String, bytes: ByteArray) {
        val json = open(bytes).toCanonicalJson()
        assertEquals(json, open(bytes).toCanonicalJson(), "canonical JSON must be stable across repeated opens")
        val goldenFile = File("doc/goldens/$name.json")
        check(goldenFile.exists()) { "missing golden: ${goldenFile.path}" }
        assertEquals(goldenFile.readText(), json, "golden mismatch for $name")
    }

    @Test fun empty() = checkGolden("empty", TestCorpus.empty)
    @Test fun textattr() = checkGolden("textattr", TestCorpus.textattr)
    @Test fun colors() = checkGolden("colors", TestCorpus.colors)
    @Test fun linkattr() = checkGolden("linkattr", TestCorpus.linkattr)
    @Test fun image() = checkGolden("image", TestCorpus.image)
    @Test fun limage() = checkGolden("limage", TestCorpus.limage)
    @Test fun limage2() = checkGolden("limage2", TestCorpus.limage2)
    @Test fun lines() = checkGolden("lines", TestCorpus.lines)
    @Test fun hcpOrigEn() = checkGolden("hcp_orig_en", TestCorpus.hcpOrigEn)
    @Test fun stGuideOrigEn() = checkGolden("st_guide_orig_en", TestCorpus.stGuideOrigEn)
}
