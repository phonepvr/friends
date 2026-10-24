package com.phonepvr.friends.data.blocking

import android.telecom.TelecomManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenCallerTest {

    @Test
    fun aShownNumberIsNotHidden() {
        assertFalse(HiddenCaller.isHidden(TelecomManager.PRESENTATION_ALLOWED, "+919876543210"))
    }

    @Test
    fun privateUnknownAndPayphoneCallersAreHidden() {
        for (p in listOf(
            TelecomManager.PRESENTATION_RESTRICTED,
            TelecomManager.PRESENTATION_UNKNOWN,
            TelecomManager.PRESENTATION_PAYPHONE,
        )) {
            assertTrue(HiddenCaller.isHidden(p, "123"))
        }
    }

    @Test
    fun noNumberAtAllIsHidden() {
        assertTrue(HiddenCaller.isHidden(TelecomManager.PRESENTATION_ALLOWED, ""))
        assertTrue(HiddenCaller.isHidden(TelecomManager.PRESENTATION_ALLOWED, null))
    }
}
