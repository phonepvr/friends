package com.phonepvr.friends.ui.incall

import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallDirection
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.data.incall.ProximityPolicy
import com.phonepvr.friends.data.incall.ProximityScreenLock
import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityTestLineTest {

    private fun status(supported: Boolean = true, held: Boolean = false, error: String? = null) =
        ProximityScreenLock.Status(supported = supported, held = held, lastAction = null, error = error)

    private fun call(
        state: CallSimpleState = CallSimpleState.ACTIVE,
        isVideo: Boolean = false,
        direction: CallDirection = CallDirection.OUTGOING,
    ) =
        CallSnapshot(
            callId = "1",
            state = state,
            direction = direction,
            number = "5550100",
            callerDisplayName = null,
            connectTimeMillis = null,
            isVideo = isVideo,
        )

    private fun line(
        status: ProximityScreenLock.Status = status(),
        call: CallSnapshot? = call(),
        route: CallAudioRoute = CallAudioRoute.EARPIECE,
        enabled: Boolean = true,
    ) = proximityTestLine(status, call, route, enabled)

    @Test
    fun armed() {
        assertEquals("Test build · screen off at ear: ON", line(status = status(held = true)))
    }

    @Test
    fun noSensorAndFailuresAreSaidPlainly() {
        assertEquals(
            "Test build · this phone reports no proximity sensor",
            line(status = status(supported = false)),
        )
        assertEquals(
            "Test build · screen off at ear: FAILED (SecurityException)",
            line(status = status(error = "SecurityException")),
        )
    }

    @Test
    fun namesWhyTheScreenStaysLit() {
        assertEquals("Test build · screen off at ear: off in Settings", line(enabled = false))
        assertEquals("Test build · screen off at ear: off on speaker", line(route = CallAudioRoute.SPEAKER))
        assertEquals("Test build · screen off at ear: off on Bluetooth", line(route = CallAudioRoute.BLUETOOTH))
        assertEquals(
            "Test build · screen off at ear: off with a headset",
            line(route = CallAudioRoute.WIRED_HEADSET),
        )
        assertEquals("Test build · screen off at ear: off for video calls", line(call = call(isVideo = true)))
        assertEquals(
            "Test build · screen off at ear: starts once answered",
            line(call = call(state = CallSimpleState.RINGING, direction = CallDirection.INCOMING)),
        )
        assertEquals(
            "Test build · screen off at ear: starts once answered",
            line(call = call(state = CallSimpleState.NEW, direction = CallDirection.INCOMING)),
        )
        assertEquals("Test build · call ended", line(call = call(state = CallSimpleState.DISCONNECTED)))
        assertEquals("Test build · call ended", line(call = null))
    }

    @Test
    fun aCallThatShouldBeArmedButIsNot_isFlagged() {
        assertEquals("Test build · screen off at ear: NOT armed", line(status = status(held = false)))
    }

    @Test
    fun saysNotArmedExactlyWhenThePolicyWantsTheSensorButTheLockIsNotHeld() {
        for (route in CallAudioRoute.entries) {
            for (enabled in listOf(true, false)) {
                for (isVideo in listOf(false, true)) {
                    for (direction in CallDirection.entries) {
                        for (state in CallSimpleState.entries) {
                            val c = call(state = state, isVideo = isVideo, direction = direction)
                            val wanted =
                                ProximityPolicy.decide(c, route, enabled) == ProximityPolicy.Action.HOLD
                            val text = line(call = c, route = route, enabled = enabled)
                            assertEquals(
                                "$state $direction $route video=$isVideo enabled=$enabled: $text",
                                wanted,
                                text.endsWith("NOT armed"),
                            )
                        }
                    }
                }
            }
        }
    }
}
