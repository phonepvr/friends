package com.phonepvr.friends.data.incall

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
     * Turns proximity screen-off on or off. Idempotent. When turned off the
     * screen stays dark until the phone moves away from the face
     * (RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY), so a call that ends while the phone
     * is still at the ear doesn't light up the app underneath against the cheek.
     */
    // No timeout on purpose: a call can last hours, and the lock is always
    // released when the call ends or the in-call service goes away.
    @SuppressLint("WakelockTimeout")
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        val lock = wakeLock ?: return
        if (enabled) {
            if (!lock.isHeld) lock.acquire()
        } else if (lock.isHeld) {
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    private companion object {
        const val TAG = "Bondwidth:InCallProximity"
    }
}
