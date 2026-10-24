package com.phonepvr.friends.ui.dialer

import android.content.ComponentName
import android.telecom.PhoneAccountHandle
import com.phonepvr.friends.data.dialer.CallPlacer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimCallLauncherTest {

    private val component = ComponentName("com.android.phone", "Telephony")
    private val sim1 = PhoneAccountHandle(component, "1")
    private val sim2 = PhoneAccountHandle(component, "2")
    private val twoSims = listOf(CallPlacer.SimAccount(sim1, "SIM 1"), CallPlacer.SimAccount(sim2, "SIM 2"))

    private val placed = mutableListOf<Pair<String, PhoneAccountHandle?>>()
    private var pending: SimCallLauncher.PendingCall? = null
    private val remembered = mutableMapOf<String, PhoneAccountHandle>()

    private fun launcher(accounts: List<CallPlacer.SimAccount>, askEveryTime: Boolean) = SimCallLauncher(
        accountsProvider = { accounts },
        needsChoice = { askEveryTime && accounts.size >= 2 },
        call = { n, a -> placed += n to a },
        setPending = { pending = it },
        rememberedSim = { n, available -> remembered[n]?.takeIf { it in available } },
        rememberSim = { n, h -> remembered[n] = h },
    )

    @Test
    fun rememberedSimIsUsedWithoutAsking() {
        remembered["555"] = sim2
        launcher(twoSims, askEveryTime = true).launch("555")
        assertEquals(listOf("555" to sim2), placed)
        assertNull(pending)
    }

    @Test
    fun rememberedSimWinsOverThePhonesDefault() {
        remembered["555"] = sim2
        launcher(twoSims, askEveryTime = false).launch("555")
        assertEquals(listOf("555" to sim2), placed)
    }

    @Test
    fun pickingWithRememberStoresTheChoice() {
        val l = launcher(twoSims, askEveryTime = true)
        l.launch("555")
        assertEquals("555", pending?.number)
        l.pick("555", sim1, rememberForNumber = true)
        assertEquals(sim1, remembered["555"])
        assertEquals(listOf("555" to sim1), placed)
        l.launch("555")
        assertEquals(listOf("555" to sim1, "555" to sim1), placed)
    }

    @Test
    fun pickingWithoutRememberAsksAgainNextTime() {
        val l = launcher(twoSims, askEveryTime = true)
        l.launch("555")
        l.pick("555", sim1, rememberForNumber = false)
        pending = null
        l.launch("555")
        assertEquals("555", pending?.number)
    }

    @Test
    fun aRememberedSimNoLongerInThePhoneIsIgnored() {
        remembered["555"] = PhoneAccountHandle(component, "removed")
        launcher(twoSims, askEveryTime = true).launch("555")
        assertEquals("555", pending?.number)
    }

    @Test
    fun singleSimPhonesNeverAsk() {
        remembered["555"] = sim2
        launcher(listOf(CallPlacer.SimAccount(sim1, "SIM 1")), askEveryTime = true).launch("555")
        assertEquals(listOf("555" to null), placed)
    }
}
