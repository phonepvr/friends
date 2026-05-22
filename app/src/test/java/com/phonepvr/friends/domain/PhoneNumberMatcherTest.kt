package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.phone.PhoneNumberMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberMatcherTest {

    @Test
    fun normalize_stripsFormatting() {
        assertEquals("15551234567", PhoneNumberMatcher.normalize("+1 (555) 123-4567"))
    }

    @Test
    fun matchKey_returnsLastSevenDigits() {
        assertEquals("1234567", PhoneNumberMatcher.matchKey("+1 555-123-4567"))
    }

    @Test
    fun matchKey_isNullForTooFewDigits() {
        assertNull(PhoneNumberMatcher.matchKey("12345"))
        assertNull(PhoneNumberMatcher.matchKey(""))
    }

    @Test
    fun matches_sameNumberDifferentFormatting() {
        assertTrue(PhoneNumberMatcher.matches("(555) 123-4567", "5551234567"))
    }

    @Test
    fun matches_sameLocalNumberDifferentCountryCode() {
        assertTrue(PhoneNumberMatcher.matches("+1 555 123 4567", "555-123-4567"))
    }

    @Test
    fun matches_differentNumbers() {
        assertFalse(PhoneNumberMatcher.matches("5551234567", "5559999999"))
    }

    @Test
    fun matches_withheldNumberNeverMatches() {
        assertFalse(PhoneNumberMatcher.matches("", "5551234567"))
    }

    @Test
    fun callDedupKey_ignoresFormatting() {
        val a = PhoneNumberMatcher.callDedupKey("555-123-4567", 1_000L)
        val b = PhoneNumberMatcher.callDedupKey("(555) 123 4567", 1_000L)
        assertEquals(a, b)
    }

    @Test
    fun callDedupKey_differsByTimestamp() {
        val a = PhoneNumberMatcher.callDedupKey("5551234567", 1_000L)
        val b = PhoneNumberMatcher.callDedupKey("5551234567", 2_000L)
        assertFalse(a == b)
    }
}
