package com.phonepvr.friends

import android.content.Intent
import android.net.Uri
import android.os.Looper
import com.phonepvr.friends.testing.PermissionEnforcingContentResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * Starts the real app the way a fresh install does: nothing granted yet. With
 * [PermissionEnforcingContentResolver] touching the contacts or call log without
 * permission throws, as it does on a device, so a startup path that forgets to
 * check fails here instead of closing the app on the user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [PermissionEnforcingContentResolver::class])
class FreshInstallStartupTest {

    private val app: FriendsApplication
        get() = RuntimeEnvironment.getApplication() as FriendsApplication

    /** Crashes on background threads (coroutines on IO/Default) end up here. */
    private val backgroundCrashes = Collections.synchronizedList(mutableListOf<Throwable>())
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun recordBackgroundCrashes() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            if (!isRobolectricWorkManagerGap(e)) backgroundCrashes.add(e)
        }
    }

    /**
     * On a device androidx.startup's InitializationProvider (in the merged manifest)
     * initializes WorkManager before Application.onCreate. Robolectric doesn't start
     * manifest content providers, so the app's work scheduling at startup fails here
     * only. Everything else on a background thread still counts as a crash.
     */
    private fun isRobolectricWorkManagerGap(e: Throwable): Boolean =
        e is IllegalStateException &&
            e.message.orEmpty().startsWith("WorkManager is not initialized properly")

    @After
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    fun firstLaunch_showsOnboardingWithoutCrashing() {
        // FriendsApplication.onCreate has already run by now: Robolectric starts the
        // application before the test, so a throw there fails this test.
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle()
        assertFalse(activity.isFinishing)
        assertNoBackgroundCrashes()
    }

    @Test
    fun afterOnboarding_mainScreenOpensWithoutPermissions() {
        runBlocking { app.settingsRepository.setHasSeenOnboarding(true) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle()
        assertFalse(activity.isFinishing)
        assertNoBackgroundCrashes()
    }

    @Test
    fun callHistoryDeepLink_opensWithoutPermissions() {
        // What a missed-call notification opens.
        openWithoutPermissions(
            Intent(app, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CALL_HISTORY, "+15551234567"),
        )
    }

    @Test
    fun dialIntent_opensDialpadWithoutPermissions() {
        openWithoutPermissions(Intent(Intent.ACTION_DIAL, Uri.parse("tel:5551234567")))
    }

    @Test
    fun backupDeepLink_opensWithoutPermissions() {
        // What the backup-reminder notification opens.
        openWithoutPermissions(
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_BACKUP, true),
        )
    }

    private fun openWithoutPermissions(intent: Intent) {
        runBlocking { app.settingsRepository.setHasSeenOnboarding(true) }
        intent.setClass(app, MainActivity::class.java)
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        settle()
        assertFalse(activity.isFinishing)
        assertNoBackgroundCrashes()
    }

    /** Lets coroutines on IO/Default finish and their results reach the main thread. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertNoBackgroundCrashes() {
        assertTrue(
            "Uncaught exceptions on background threads: " +
                backgroundCrashes.joinToString { it.stackTraceToString() },
            backgroundCrashes.isEmpty(),
        )
    }
}
