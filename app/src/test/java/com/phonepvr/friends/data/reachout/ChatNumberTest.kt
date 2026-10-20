package com.phonepvr.friends.data.reachout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatNumberTest {

    /** Stand-in for PhoneNumberUtils: knows one Indian and one UK local number. */
    private val fakeE164: (String, String) -> String? = { number, iso ->
        when {
            iso == "IN" && number.filter(Char::isDigit) == "09876543210" -> "+919876543210"
            iso == "GB" && number.filter(Char::isDigit) == "02079460018" -> "+442079460018"
            else -> null
        }
    }

    private fun intl(raw: String, iso: String?) = ChatNumber.internationalDigits(raw, iso, fakeE164)

    @Test
    fun internationalNumbersKeepTheirCountryCode() {
        assertEquals("442079460018", intl("+44 20 7946 0018", "IN"))
        assertEquals("442079460018", intl("0044 20 7946 0018", "IN"))
        assertEquals("15551234567", intl("+1 (555) 123-4567", null))
    }

    @Test
    fun localNumbersGetTheSimCountryCode() {
        assertEquals("919876543210", intl("098765 43210", "IN"))
        assertEquals("919876543210", intl("098765 43210", "in"))
        assertEquals("442079460018", intl("020 7946 0018", "GB"))
    }

    @Test
    fun numbersTheFormatterCantPlaceAreKeptAsTyped() {
        // Already written with the country code but no '+'.
        assertEquals("919876543210", intl("91 98765 43210", "IN"))
        // No country known.
        assertEquals("09876543210", intl("098765 43210", null))
        assertEquals("09876543210", intl("098765 43210", "XYZ"))
    }

    @Test
    fun nothingDialable_isNull() {
        assertNull(intl("", "IN"))
        assertNull(intl("call me", "IN"))
    }
}
