package com.phonepvr.friends.ui.permissions

import android.Manifest
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissingPermissionsTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun freshInstall_everythingIsMissing() {
        assertEquals(onboardingRuntimePermissions(), missingRuntimePermissions(app))
    }

    @Test
    fun afterBecomingThePhoneApp_nothingIsLeftToAsk() {
        // The dialer role grants the phone, contacts and notifications sets; every
        // permission setup asks for is in one of them, so no extra pop-up follows.
        shadowOf(app).grantPermissions(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        assertTrue(missingRuntimePermissions(app).isEmpty())
    }

    @Test
    fun onlyWhatIsStillMissingIsAskedFor() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        val missing = missingRuntimePermissions(app)
        assertFalse(Manifest.permission.READ_CONTACTS in missing)
        assertTrue(Manifest.permission.READ_CALL_LOG in missing)
    }

    @Test
    fun unusedPhoneNumbersPermissionIsNotRequested() {
        assertFalse(Manifest.permission.READ_PHONE_NUMBERS in onboardingRuntimePermissions())
    }
}
