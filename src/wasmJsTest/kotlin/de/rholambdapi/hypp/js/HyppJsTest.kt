package de.rholambdapi.hypp.js

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the exported functions in `HyppJs.kt` as plain Kotlin calls, proving the flattening
 * itself is correct on the `wasmJs` target. [FacadeJsTest] (an external Node script + Gradle
 * task) is the complementary proof that the *export* is actually reachable from real JavaScript —
 * see its comment for why both are needed.
 */
@OptIn(ExperimentalEncodingApi::class)
class HyppJsTest {
    // textattr.hyp — same bytes as TestCorpus.textattr (commonTest can't be shared into wasmJsTest
    // without extra Gradle wiring; duplicated here as it's the smallest fixture, see phase 1's
    // resource-loading learning).
    private val textattr =
        "SERPQwAAACIAAgMCFAAAAABuAK4AAAAAAABNYWluAAAO/wAAAOUAAAAAAAAAAAAeAAhhdGFyaXN0AAAEABYtZDEyICtnIC1pIC1zIC10NCAregAAAAgABktlaW5zAAAKAAIAAAALAAJLAAAAAAAAclJyrdUUeA2g2U8AWYsFh03/ZtHfqF1yMh4z6EioihmYAAWpuUWJbQTdH1Ooct47Rt2eDyogrg8tPqRQRGU1u2EWh1mgbzhFjpV4ZaR6lxC1DG1Dbl7kCI7zTG78mcR36+Tnd7L1z5qL6ubaD/7jvaaHEKH9PA=="

    // linkattr.hyp — same bytes as TestCorpus.linkattr.
    private val linkattr =
        "SERPQwAAAPQACwMCFAAAAAFAADoAAQAAAABNYWluAAAWAAAAAcMAAAACAAAAAFBhZ2UgMQAAFgAAAAHUAAAAAgABAABQYWdlIDIAABQBAAAB5QAAAAMAAgAAcG9wdXAAHAIAAAH2AAAAAAAAAABoY3AuaHlwL01haW4AABoEAAAB9gAAAAAAAAAAZWNobyBoZWxsbwAAIAYAAAH2AAAAAAAAAABlY2hvIHJleHggY29tbWFuZAAgBQAAAfYAAAAAAAAAAGVjaG8gcmV4eCBzY3JpcHQAAA4IAAAB9gAAAAAAAAAADgcAAAH2AAAAAAAAAAAO/wAAAfYAAAAAAAAAAAAeAAhhdGFyaXN0AAAEABYtZDEyICtnIC1pIC1zIC10NCAregAAAAgABktlaW5zAAAKAAIAAAALAAJLAAAAAAAAgVNzjShR7FVW6UfobUhOALCm0CLRKumNE+OP/JsQHjkRoouAECuxH6GSDOrYFJHJ2BseNbPLpvytoLrSGMNRbgpdn/2+GfNDbwXc570108A6d6rLBal97Dt1TxCj6mLcChKadXIF9XpFwyuKI5EdqZaG5gqrMpD7x0hkIPJrJEhzAERpZXMgaXN0IFNlaXRlIDEARGllcyBpc3QgU2VpdGUgMgBUaGlzIGlzIGEgcG9wdXAuAA=="

    @Test
    fun openReturnsAFreshHandlePerCallAndMinusOneOnFailure() {
        val h1 = hyppOpen(textattr)
        val h2 = hyppOpen(textattr)
        assertTrue(h1 >= 0 && h2 >= 0 && h1 != h2)
        assertEquals(-1, hyppOpen(Base64.encode(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun flattensTextattrHypSpanBySpan() {
        val h = hyppOpen(textattr)
        assertEquals(1, hyppEntryCount(h), "entries excludes the EOF sentinel")
        assertEquals(0, hyppEntryType(h, 0))
        assertEquals("Main", hyppEntryName(h, 0))
        assertTrue(hyppNodeExists(h, 0))
        assertEquals(0, hyppNodeKind(h, 0))
        assertEquals(9, hyppNodeLineCount(h, 0))

        assertEquals(1, hyppLineSpanCount(h, 0, 0))
        assertEquals("Dies ist normaler Text.", hyppSpanText(h, 0, 0, 0))
        assertEquals(-1, hyppSpanLinkKind(h, 0, 0, 0))
        val normalBits = hyppSpanStyleBits(h, 0, 0, 0)
        assertEquals(0, normalBits and 0x3F, "no attributes")
        assertEquals(1, (normalBits shr 8) and 0xF, "default foreground is BLACK (ordinal 1)")
        assertEquals(0, (normalBits shr 12) and 0xF, "default background is WHITE (ordinal 0)")

        // Line 1: "Dies ist " / "heller" (light, escape 0x66 -> vector 2) / " Text."
        assertEquals(3, hyppLineSpanCount(h, 0, 1))
        assertEquals("heller", hyppSpanText(h, 0, 1, 1))
        assertEquals(2, hyppSpanStyleBits(h, 0, 1, 1) and 0x3F, "light attribute bit")

        assertEquals(-1, hyppNodeLineCount(h, 99))
        assertEquals(0, hyppDiagnosticCount(h))
    }

    @Test
    fun flattensLinkattrHypLinksWithTargetAndKind() {
        val h = hyppOpen(linkattr)
        assertEquals(0, hyppDiagnosticCount(h))

        assertEquals(2, hyppLineSpanCount(h, 0, 0))
        assertEquals("Link to ", hyppSpanText(h, 0, 0, 0))
        assertEquals(-1, hyppSpanLinkKind(h, 0, 0, 0))

        assertEquals("internal Page", hyppSpanText(h, 0, 0, 1))
        assertEquals(0, hyppSpanLinkKind(h, 0, 0, 1), "LinkKind.LINK")
        assertEquals(1, hyppSpanLinkTarget(h, 0, 0, 1))
        assertEquals(-1, hyppSpanLinkLineNumber(h, 0, 0, 1))

        // Entry 9 ("Exit") is a type-7 quit dummy: no node, but its link still flattens fine.
        assertEquals(7, hyppEntryType(h, 9))
        assertTrue(!hyppNodeExists(h, 9))
    }

    @Test
    fun graphicAndDiagnosticAccessorsReturnSentinelsWhenAbsent() {
        val h = hyppOpen(textattr)
        assertEquals(0, hyppGraphicCount(h, 0))
        assertEquals(-1, hyppGraphicKind(h, 0, 0))
        assertEquals(-1, hyppDiagnosticKind(h, 0))
        assertEquals(-1, hyppDiagnosticNodeIndex(h, 0))
        assertEquals("", hyppDiagnosticText(h, 0))
    }
}
