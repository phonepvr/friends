package com.phonepvr.friends.data.dialer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastedNumberTest {

    private fun extract(text: String?) = PastedNumber.extract(text)

    @Test
    fun formattedNumbers_loseTheirFormatting() {
        assertEquals("+15551234567", extract("+1 (555) 123-4567"))
        assertEquals("+919876543210", extract("+91 98765 43210"))
        assertEquals("02079460018", extract("020 7946 0018"))
        assertEquals("5551234567", extract("555.123.4567"))
        assertEquals("0301234567", extract("030/1234567"))
        assertEquals("5551234567", extract("  5551234567\n"))
    }

    @Test
    fun numberInsideText_isFound() {
        assertEquals("5551234567", extract("Call me at 555-123-4567 tomorrow"))
        assertEquals("5551234", extract("Office: 555-1234 ext. 89"))
        assertEquals("+447700900123", extract("WhatsApp me on +44 7700 900123!"))
    }

    @Test
    fun firstOfSeveralNumbers_wins() {
        assertEquals("02012345678", extract("Office 020 1234 5678, mobile 07700 900123"))
        assertEquals("+15551234567", extract("+1 555 123 4567 +44 20 7946 0018"))
    }

    @Test
    fun ussdAndServiceCodes_keepStarAndHash() {
        assertEquals("*123#", extract("*123#"))
        assertEquals("*#06#", extract("Dial *#06# to see your IMEI"))
        assertEquals("*100*2#", extract("*100*2#"))
    }

    @Test
    fun telLinks_areDecoded() {
        assertEquals("+442079460018", extract("tel:+44%2020%207946%200018"))
        assertEquals("*123#", extract("tel:*123%23"))
        assertEquals("+15551234567", extract("TEL:+1-555-123-4567;ext=89"))
    }

    @Test
    fun otherScriptsDigits_becomeAscii() {
        assertEquals("9876543210", extract("९८७६५४३२१०"))
        assertEquals("0123456789", extract("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("+1555", extract("＋１ ５５５"))
        assertEquals("+919876543210", extract("+९१ ९८७६५ ४३२१०"))
    }

    @Test
    fun nonBreakingAndThinSpaces_areSeparators() {
        assertEquals("+33123456789", extract("+33 1 23 45 67 89"))
    }

    @Test
    fun datesAreSkipped() {
        assertEquals("02079460018", extract("Meeting on 12/05/2024, call 020 7946 0018"))
        assertNull(extract("12.05.24"))
    }

    @Test
    fun nothingDialable_returnsNull() {
        assertNull(extract(null))
        assertNull(extract(""))
        assertNull(extract("   "))
        assertNull(extract("hello there"))
        assertNull(extract("12"))
        assertNull(extract("+"))
        assertNull(extract("1".repeat(PastedNumber.MAX_LENGTH + 1)))
    }
}
