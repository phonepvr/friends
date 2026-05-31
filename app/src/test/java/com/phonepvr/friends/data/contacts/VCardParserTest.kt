package com.phonepvr.friends.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardParserTest {

    @Test fun `parses a single full card`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            N:Lovelace;Ada;Augusta;;
            TEL;TYPE=CELL:+44 7700 900000
            TEL;TYPE=HOME:+44 1234 567890
            EMAIL;TYPE=INTERNET:ada@analyticalengine.com
            ORG:Analytical Engine Co
            NOTE:Notes about Ada
            BDAY:1815-12-10
            END:VCARD
        """.trimIndent()
        val cards = VCardParser.parse(text)
        assertEquals(1, cards.size)
        val card = cards.single()
        assertEquals("Ada Lovelace", card.displayName)
        assertEquals(listOf("+44 7700 900000", "+44 1234 567890"), card.phones)
        assertEquals(listOf("ada@analyticalengine.com"), card.emails)
        assertEquals("Analytical Engine Co", card.organization)
        assertEquals("Notes about Ada", card.notes)
        assertEquals(ContactDate(month = 12, day = 10, year = 1815), card.birthday)
    }

    @Test fun `parses multiple cards in one stream`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:One
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Two
            TEL:555-0000
            END:VCARD
        """.trimIndent()
        val cards = VCardParser.parse(text)
        assertEquals(listOf("One", "Two"), cards.map { it.displayName })
        assertEquals(listOf("555-0000"), cards[1].phones)
    }

    @Test fun `falls back to structured N when FN is missing`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            N:Curie;Marie;;;
            END:VCARD
        """.trimIndent()
        assertEquals("Marie Curie", VCardParser.parse(text).single().displayName)
    }

    @Test fun `skips cards with no usable name`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            TEL:555-1234
            END:VCARD
        """.trimIndent()
        assertTrue(VCardParser.parse(text).isEmpty())
    }

    @Test fun `unescapes commas, semicolons, backslashes and \\n`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Acme\, Inc.
            NOTE:Line one\nLine two\; still on the same value
            END:VCARD
        """.trimIndent()
        val card = VCardParser.parse(text).single()
        assertEquals("Acme, Inc.", card.displayName)
        assertEquals("Line one\nLine two; still on the same value", card.notes)
    }

    @Test fun `accepts year-less birthdays`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Anon
            BDAY:--04-22
            END:VCARD
        """.trimIndent()
        val card = VCardParser.parse(text).single()
        assertEquals(4, card.birthday?.month)
        assertEquals(22, card.birthday?.day)
        assertNull(card.birthday?.year)
    }

    @Test fun `unfolds long lines split across CRLF + space`() {
        // vCard's line-folding rule: a CRLF followed by a space/tab is a
        // continuation of the previous line. We do the same with bare LF
        // because exporters are inconsistent.
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Long\r\n Name\r\nEND:VCARD"
        assertEquals("LongName", VCardParser.parse(text).single().displayName)
    }

    @Test fun `strips group prefixes on property names`() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Grouped
            item1.TEL:555-0001
            item2.EMAIL:grouped@example.com
            END:VCARD
        """.trimIndent()
        val card = VCardParser.parse(text).single()
        assertEquals(listOf("555-0001"), card.phones)
        assertEquals(listOf("grouped@example.com"), card.emails)
    }
}
