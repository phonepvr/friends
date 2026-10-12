package com.phonepvr.friends.data.incall

import android.content.Context
import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProximityScreenLockTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun deviceWithProximitySensor(supported: Boolean) {
        val pm = context.getSystemService(PowerManager::class.java)
        shadowOf(pm).setIsWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, supported)
    }

    @Test
    fun holdsAndReleasesTheProximityWakeLock() {
        deviceWithProximitySensor(true)
        val lock = ProximityScreenLock(context)
        assertTrue(lock.isSupported)
        assertFalse(lock.isHeld)

        lock.setEnabled(true)
        assertTrue(lock.isHeld)
        val wakeLock = shadowOf(ShadowPowerManager.getLatestWakeLock())
        assertEquals("Bondwidth:InCallProximity", wakeLock.tag)
        assertFalse(wakeLock.isReferenceCounted)

        lock.setEnabled(false)
        assertFalse(lock.isHeld)
    }

    @Test
    fun repeatedUpdates_doNotStackAcquires() {
        deviceWithProximitySensor(true)
        val lock = ProximityScreenLock(context)
        lock.setEnabled(true)
        lock.setEnabled(true)
        lock.setEnabled(true)
        // Not reference counted: a single release turns it off however often it was enabled.
        lock.setEnabled(false)
        assertFalse(lock.isHeld)
        lock.setEnabled(false)
        assertFalse(lock.isHeld)
    }

    @Test
    fun deviceWithoutProximitySensor_isANoOp() {
        deviceWithProximitySensor(false)
        val lock = ProximityScreenLock(context)
        assertFalse(lock.isSupported)
        lock.setEnabled(true)
        assertFalse(lock.isHeld)
        assertNull(ShadowPowerManager.getLatestWakeLock())
    }
}
