package de.rholambdapi.hypp

import kotlin.test.Test
import kotlin.test.assertEquals

class SkeletonTest {
    @Test
    fun compilesAndRunsOnEveryTarget() {
        assertEquals("0.1.0-SNAPSHOT", HYPP_VERSION)
    }
}
