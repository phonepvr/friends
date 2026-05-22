package com.phonepvr.friends

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Confirms the JVM unit-test tier runs in CI. Real domain tests arrive in Phase 1.
 */
class SanityTest {
    @Test
    fun unitTestTierExecutes() {
        assertEquals(4, 2 + 2)
    }
}
