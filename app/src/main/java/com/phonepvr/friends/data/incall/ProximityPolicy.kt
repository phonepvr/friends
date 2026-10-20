package com.phonepvr.friends.data.incall

/**
 * What the in-call proximity sensor should do right now (issue #45). Pure so it
 * can be unit-tested; [ProximityScreenLock] applies the result.
 *
 * Follows the stock Android dialer (AOSP InCallUI ProximitySensor):
 * - While a call is off-hook (dialling, connecting, active, on hold) and its audio
 *   comes out of the earpiece, the phone is at the user's ear: the screen goes dark
 *   whenever the sensor is covered, so a cheek can't press anything. This includes
 *   the in-call keypad; the stock dialer only exempts the keypad when the phone
 *   lies flat, and a cheek pressing menu digits is worse than typing with the
 *   sensor uncovered.
 * - Speaker, Bluetooth, a wired headset or a video call mean the user is looking at
 *   the screen, so it lights up at once.
 * - When the call is ringing, ends, or there is none, the sensor is let go, but a
 *   screen that is dark against the face stays dark until the phone moves away.
 *   That stops a call that ends at the ear from lighting up the app underneath
 *   against the cheek.
 * - Users can switch the feature off for a faulty sensor (Settings → Phone app).
 */
object ProximityPolicy {

    enum class Action {
        /** Screen turns off whenever the sensor is covered. */
        HOLD,

        /** Stop, but keep the screen dark until nothing is near the sensor. */
        RELEASE_WHEN_CLEAR,

        /** Stop and light the screen now. */
        RELEASE_NOW,
    }

    fun decide(call: CallSnapshot?, route: CallAudioRoute, enabled: Boolean): Action {
        if (!enabled) return Action.RELEASE_NOW
        val screenOnImmediately = route != CallAudioRoute.EARPIECE || call?.isVideo == true
        return when {
            screenOnImmediately -> Action.RELEASE_NOW
            call != null && call.isOffHook() -> Action.HOLD
            else -> Action.RELEASE_WHEN_CLEAR
        }
    }

    private fun CallSnapshot.isOffHook(): Boolean = when (state) {
        CallSimpleState.DIALING,
        CallSimpleState.CONNECTING,
        CallSimpleState.ACTIVE,
        CallSimpleState.HOLDING,
        -> true
        // An outgoing call is briefly NEW before it starts dialling; the phone may
        // already be on its way to the ear.
        CallSimpleState.NEW -> direction == CallDirection.OUTGOING
        // Ringing: the user has to see Accept / Reject.
        CallSimpleState.RINGING,
        CallSimpleState.DISCONNECTED,
        -> false
    }
}
