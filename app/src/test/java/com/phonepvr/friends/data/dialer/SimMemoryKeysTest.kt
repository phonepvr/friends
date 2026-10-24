package com.phonepvr.friends.data.dialer

import android.content.ComponentName
import android.telecom.PhoneAccountHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimMemoryKeysTest {

    @Test
    fun theSameNumberWrittenDifferentlyMatches() {
        val key = SimMemoryKeys.numberKey("+91 98765 43210")
        assertEquals(key, SimMemoryKeys.numberKey("098765-43210"))
        assertEquals(key, SimMemoryKeys.numberKey("9876543210"))
    }

    @Test
    fun tooShortToBeANumber() {
        assertNull(SimMemoryKeys.numberKey("12"))
        assertNull(SimMemoryKeys.numberKey(""))
    }

    @Test
    fun aSimSurvivesBeingWrittenDown() {
        val sim = PhoneAccountHandle(
            ComponentName("com.android.phone", "com.android.services.telephony.TelephonyConnectionService"),
            "8991000000000000002",
        )
        assertEquals(sim, SimMemoryKeys.decode(SimMemoryKeys.encode(sim)))
        assertNull(SimMemoryKeys.decode("garbage"))
    }
}
