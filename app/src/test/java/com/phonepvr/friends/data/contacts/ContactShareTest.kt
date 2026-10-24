package com.phonepvr.friends.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactShareTest {

    private val asha = ContactDetails(
        lookupKey = "lk",
        displayName = "Asha Rao",
        phoneNumbers = listOf("+91 98765 43210", "080 1234 5678"),
        phoneEntries = listOf(
            ContactPhone(dataId = 1, number = "+91 98765 43210", isPrimary = true, type = PhoneType.MOBILE),
            ContactPhone(dataId = 2, number = "080 1234 5678", isPrimary = false, type = PhoneType.WORK),
        ),
        emails = listOf("asha@example.org"),
        organization = "Rao, Sons & Co; Ltd",
        birthday = null,
        anniversary = null,
    )

    @Test
    fun cardUsesStandardLineBreaksAndLabelsEachNumber() {
        val card = VCardBuilder.build(asha)
        assertTrue(card.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(card.endsWith("END:VCARD\r\n"))
        assertTrue("\r\nTEL;TYPE=CELL,PREF:+91 98765 43210\r\n" in card)
        assertTrue("\r\nTEL;TYPE=WORK:080 1234 5678\r\n" in card)
        assertTrue("\r\nORG:Rao\\, Sons & Co\\; Ltd\r\n" in card)
    }

    @Test
    fun aContactsAppCanReadTheCardBack() {
        val parsed = VCardParser.parse(VCardBuilder.build(asha.copy(organization = "Rao, Sons & Co"))).single()
        assertEquals("Asha Rao", parsed.displayName)
        assertEquals(listOf("+91 98765 43210", "080 1234 5678"), parsed.phones)
        assertEquals(listOf("asha@example.org"), parsed.emails)
        assertEquals("Rao, Sons & Co", parsed.organization)
    }

    @Test
    fun cardFileIsNamedAfterThePersonInAnyScript() {
        assertEquals("Asha Rao.vcf", VCardBuilder.fileName("Asha Rao"))
        assertEquals("आशा राव.vcf", VCardBuilder.fileName("आशा राव"))
        assertEquals("contact.vcf", VCardBuilder.fileName("  /?*  "))
    }

    @Test
    fun textHasTheNameThenLabelledNumbersThenEmail() {
        val text = ContactShareText.build(asha) { phone ->
            if (phone.type == PhoneType.WORK) "Work" else "Mobile"
        }
        assertEquals(
            "Asha Rao\nMobile: +91 98765 43210\nWork: 080 1234 5678\nEmail: asha@example.org",
            text,
        )
    }
}
