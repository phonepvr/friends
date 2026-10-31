package com.phonepvr.friends.data.dialer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialCodesTest {

    @Test
    fun `ussd and mmi codes are codes`() {
        assertTrue(DialCodes.isCode("*100#"))
        assertTrue(DialCodes.isCode("*#06#"))
        assertTrue(DialCodes.isCode("#31#0123456"))
        assertTrue(DialCodes.isCode("*#*#4636#*#*"))
    }

    @Test
    fun `plain numbers are not codes`() {
        assertFalse(DialCodes.isCode("100"))
        assertFalse(DialCodes.isCode("+49 911 347717"))
        assertFalse(DialCodes.isCode(""))
    }

    @Test
    fun `service code digits are extracted`() {
        assertEquals("4636", DialCodes.serviceCode("*#*#4636#*#*"))
        assertEquals("6484", DialCodes.serviceCode(" *#*#6484#*#* "))
    }

    @Test
    fun `ussd and partial service codes are not service codes`() {
        assertNull(DialCodes.serviceCode("*100#"))
        assertNull(DialCodes.serviceCode("*#06#"))
        assertNull(DialCodes.serviceCode("*#*#4636#*"))
        assertNull(DialCodes.serviceCode("*#*##*#*"))
    }
}
