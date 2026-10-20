package com.phonepvr.friends.data.incall

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the platform's proximity wake lock during a call so the screen turns
 * off while the phone is against the user's face (issue #45). Without it the
 * in-call screen stays lit (FLAG_KEEP_SCREEN_ON) and a cheek can press buttons,
 * which is how a reporter's frequent contact got blocked.
 *
 * Only the screen is affected: a proximity wake lock does not keep the CPU
 * awake. A no-op on devices without a proximity sensor.
 */
@Singleton
class ProximityScreenLock @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val wakeLock: PowerManager.WakeLock? =
        context.getSystemService(PowerManager::class.java)
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)
            ?.apply { setReferenceCounted(false) }

    /** False when the device has no usable proximity sensor. */
    val isSupported: Boolean get() = wakeLock != null

    val isHeld: Boolean get() = wakeLock?.isHeld == true

    /**
     * What the lock is doing right now. Test builds show it on the call screen, so
     * a test on a real phone tells for certain whether the sensor was armed.
     */
    data class Status(
        val supported: Boolean,
        val held: Boolean,
        val lastAction: ProximityPolicy.Action?,
        /** Set when the platform refused the last change (e.g. an OEM build throwing). */
        val error: String? = null,
    )

    private val _status = MutableStateFlow(Status(supported = isSupported, held = false, lastAction = null))
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Applies a [ProximityPolicy] decision. Idempotent. */
    // No timeout on purpose: a call can last hours (a timed lock would silently stop
    // working mid-call), and the lock is always let go when the call ends or the
    // in-call service goes away.
    @SuppressLint("WakelockTimeout")
    @Synchronized
    fun update(action: ProximityPolicy.Action) {
        val lock = wakeLock
        var error: String? = null
        if (lock != null) {
            try {
                when (action) {
                    ProximityPolicy.Action.HOLD -> if (!lock.isHeld) lock.acquire()
                    ProximityPolicy.Action.RELEASE_WHEN_CLEAR ->
                        if (lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                    ProximityPolicy.Action.RELEASE_NOW -> if (lock.isHeld) lock.release()
                }
            } catch (e: RuntimeException) {
                // Runs from the in-call service: a failure here must never take the
                // call screen down with it. Recorded so test builds can show it.
                error = e.javaClass.simpleName
            }
        }
        _status.value = Status(
            supported = lock != null,
            held = lock?.isHeld == true,
            lastAction = action,
            error = error,
        )
    }

    private companion object {
        const val TAG = "Bondwidth:InCallProximity"
    }
}
