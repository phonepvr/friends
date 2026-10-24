package com.phonepvr.friends.data.calllog

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.dialer.RecentsFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Blocked calls show in Recents only; everything else reading the log skips them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockedCallsTest {

    class FakeCallLog : ContentProvider() {
        override fun onCreate() = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(projection).apply {
            fun row(id: Long, number: String, type: Int) =
                addRow(projection!!.map { col ->
                    when (col) {
                        CallLog.Calls._ID -> id
                        CallLog.Calls.NUMBER -> number
                        CallLog.Calls.TYPE -> type
                        CallLog.Calls.DATE -> 1_000L + id
                        CallLog.Calls.DURATION -> 0L
                        else -> null
                    }
                }.toTypedArray())
            row(1, "5550100", CallLog.Calls.INCOMING_TYPE)
            row(2, "", CallLog.Calls.BLOCKED_TYPE)
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private val reader get() = CallLogReader(RuntimeEnvironment.getApplication())

    @Before
    fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALL_LOG)
        Robolectric.setupContentProvider(FakeCallLog::class.java, CallLog.AUTHORITY)
    }

    @Test
    fun recentsSeeBlockedCalls() {
        assertEquals(
            listOf(CallType.INCOMING, CallType.BLOCKED),
            reader.recentCalls(0L, includeBlocked = true).map { it.type }.sorted(),
        )
    }

    @Test
    fun everythingElseLeavesThemOut() {
        assertEquals(listOf(CallType.INCOMING), reader.recentCalls(0L).map { it.type })
        assertEquals(listOf(CallType.INCOMING), reader.allCalls().map { it.type })
    }

    @Test
    fun blockedCallsSitUnderIncomingInRecents() {
        assertTrue(RecentsFilter.INCOMING.matches(CallType.BLOCKED))
        assertEquals(1, RecentsFilter.entries.count { it != RecentsFilter.ALL && it.matches(CallType.BLOCKED) })
    }
}
