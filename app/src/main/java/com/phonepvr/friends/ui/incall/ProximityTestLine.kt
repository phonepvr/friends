package com.phonepvr.friends.ui.incall

import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.data.incall.ProximityPolicy
import com.phonepvr.friends.data.incall.ProximityScreenLock

/**
 * The line test builds show at the top of the call screen. It names the build, so
 * a tester can tell this call screen from the regular app's (they look the same,
 * and Android shows the call screen of whichever app is the phone app), and says
 * whether the screen will go dark at the ear right now, and if not, why.
 */
internal fun proximityTestLine(
    status: ProximityScreenLock.Status,
    call: CallSnapshot?,
    route: CallAudioRoute,
    enabled: Boolean,
): String {
    val wanted = ProximityPolicy.decide(call, route, enabled) == ProximityPolicy.Action.HOLD
    val detail = when {
        !status.supported -> "this phone reports no proximity sensor"
        status.error != null -> "screen off at ear: FAILED (${status.error})"
        status.held -> "screen off at ear: ON"
        // The policy wants the sensor on, yet the lock isn't held.
        wanted -> "screen off at ear: NOT armed"
        !enabled -> "screen off at ear: off in Settings"
        route == CallAudioRoute.SPEAKER -> "screen off at ear: off on speaker"
        route == CallAudioRoute.BLUETOOTH -> "screen off at ear: off on Bluetooth"
        route == CallAudioRoute.WIRED_HEADSET -> "screen off at ear: off with a headset"
        call?.isVideo == true -> "screen off at ear: off for video calls"
        call == null || call.state == CallSimpleState.DISCONNECTED -> "call ended"
        // Ringing (or about to): the sensor starts once the call is answered.
        else -> "screen off at ear: starts once answered"
    }
    return "Test build · $detail"
}
