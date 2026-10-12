package com.phonepvr.friends.data.incall

/**
 * When the in-call proximity sensor should be allowed to turn the screen off
 * (issue #45). Pure so it can be unit-tested; [ProximityScreenLock] applies it.
 *
 * Mirrors the stock Android dialer: while a call is off-hook and its audio comes
 * out of the earpiece, the user is holding the phone to their ear, so the screen
 * goes dark when the sensor is covered and the cheek can't press anything. The
 * screen stays usable when:
 * - the call is ringing (the user has to see Accept / Reject),
 * - audio is on the speaker, Bluetooth or a wired headset (the phone is in the
 *   hand or on a table, not at the ear),
 * - the in-call keypad is open (the user is typing digits for a menu),
 * - there is no call, or it has ended.
 */
object ProximityPolicy {

    fun shouldUseProximity(
        call: CallSnapshot?,
        route: CallAudioRoute,
        keypadOpen: Boolean,
    ): Boolean {
        if (call == null || keypadOpen || route != CallAudioRoute.EARPIECE) return false
        return when (call.state) {
            CallSimpleState.DIALING,
            CallSimpleState.CONNECTING,
            CallSimpleState.ACTIVE,
            CallSimpleState.HOLDING,
            -> true
            // An outgoing call is briefly NEW before it starts dialling; the phone
            // may already be on its way to the ear.
            CallSimpleState.NEW -> call.direction == CallDirection.OUTGOING
            CallSimpleState.RINGING,
            CallSimpleState.DISCONNECTED,
            -> false
        }
    }
}
