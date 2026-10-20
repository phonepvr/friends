package com.phonepvr.friends.data.reachout

import android.content.Context
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** With Android's real phone-number rules and a SIM country, as on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatNumberPlatformTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun simIn(iso: String) {
        shadowOf(context.getSystemService(TelephonyManager::class.java)).setSimCountryIso(iso)
    }

    @Test
    fun indianLocalNumber_getsPlus91() {
        simIn("in")
        assertEquals("919876543210", ReachOutLauncher.chatNumber(context, "098765 43210"))
        assertEquals("919876543210", ReachOutLauncher.chatNumber(context, "98765 43210"))
    }

    @Test
    fun ukLocalNumber_getsPlus44() {
        simIn("gb")
        assertEquals("442079460018", ReachOutLauncher.chatNumber(context, "020 7946 0018"))
    }

    @Test
    fun internationalNumber_isUnchangedWhateverTheSim() {
        simIn("in")
        assertEquals("442079460018", ReachOutLauncher.chatNumber(context, "+44 20 7946 0018"))
    }

    @Test
    fun numberAlreadyCarryingItsCountryCode_isKept() {
        simIn("in")
        assertEquals("919876543210", ReachOutLauncher.chatNumber(context, "919876543210"))
    }
}
