package com.phonepvr.friends.data.blocking

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.telecom.TelecomManager
import com.phonepvr.friends.role.DialerRoleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockAvailabilityTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Stands in for Android's blocked-numbers provider; answers "may this user block numbers?". */
    class FakeBlockedNumberProvider : ContentProvider() {
        var userCanBlock = true

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
            if (method == "can_current_user_block_numbers") {
                Bundle().apply { putBoolean("can_block", userCanBlock) }
            } else {
                null
            }

        override fun onCreate() = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }

    private fun blockedNumbersProvider(userCanBlock: Boolean) {
        Robolectric.setupContentProvider(
            FakeBlockedNumberProvider::class.java,
            BlockedNumberContract.AUTHORITY,
        ).userCanBlock = userCanBlock
    }

    private fun phoneApp(isBondwidth: Boolean) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        shadowOf(telecom).setDefaultDialerPackage(
            if (isBondwidth) context.packageName else "com.android.dialer",
        )
    }

    private fun manager() = BlockedNumberManager(context, DialerRoleManager(context))

    @Test
    fun of_coversEveryCase() {
        assertEquals(BlockAvailability.AVAILABLE, BlockAvailability.of(isDefaultPhoneApp = true, userCanBlockNumbers = true))
        assertEquals(BlockAvailability.NEEDS_PHONE_APP, BlockAvailability.of(isDefaultPhoneApp = false, userCanBlockNumbers = true))
        assertEquals(BlockAvailability.UNAVAILABLE, BlockAvailability.of(isDefaultPhoneApp = true, userCanBlockNumbers = false))
        assertEquals(BlockAvailability.UNAVAILABLE, BlockAvailability.of(isDefaultPhoneApp = false, userCanBlockNumbers = false))
    }

    @Test
    fun anotherAppIsThePhoneApp_blockIsOfferedWithAnExplanationInsteadOfHidden() {
        // What a test build sees next to the regular app: the regular app is still
        // the phone app, so the test build can't write the block list itself.
        blockedNumbersProvider(userCanBlock = true)
        phoneApp(isBondwidth = false)
        assertEquals(BlockAvailability.NEEDS_PHONE_APP, manager().availability())
        assertFalse(manager().canBlock())
    }

    @Test
    fun bondwidthIsThePhoneApp_canBlock() {
        blockedNumbersProvider(userCanBlock = true)
        phoneApp(isBondwidth = true)
        assertEquals(BlockAvailability.AVAILABLE, manager().availability())
        assertTrue(manager().canBlock())
    }

    @Test
    fun userWhoMayNotBlockNumbers_isNeverOfferedBlock() {
        // E.g. a secondary user on a shared tablet: becoming the phone app wouldn't help.
        blockedNumbersProvider(userCanBlock = false)
        phoneApp(isBondwidth = true)
        assertEquals(BlockAvailability.UNAVAILABLE, manager().availability())
        phoneApp(isBondwidth = false)
        assertEquals(BlockAvailability.UNAVAILABLE, manager().availability())
    }

    @Test
    fun noBlockedNumbersProvider_isUnavailableNotACrash() {
        phoneApp(isBondwidth = false)
        assertEquals(BlockAvailability.UNAVAILABLE, manager().availability())
    }
}
