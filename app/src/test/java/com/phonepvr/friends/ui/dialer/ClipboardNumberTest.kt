package com.phonepvr.friends.ui.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardNumberTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    @Test
    fun pastesTheNumberOutOfCopiedText() {
        clipboard.setPrimaryClip(ClipData.newPlainText("msg", "Call me on +1 (555) 123-4567 later"))
        assertEquals("+15551234567", readNumberFromClipboard(context))
    }

    @Test
    fun pastesAUssdCode() {
        clipboard.setPrimaryClip(ClipData.newPlainText("code", "*123#"))
        assertEquals("*123#", readNumberFromClipboard(context))
    }

    @Test
    fun nothingDialableOrEmptyClipboard_givesNull() {
        assertNull(readNumberFromClipboard(context))
        clipboard.setPrimaryClip(ClipData.newPlainText("msg", "see you soon"))
        assertNull(readNumberFromClipboard(context))
    }

    @Test
    fun copyPutsThePlainNumberOnTheClipboard() {
        copyNumberToClipboard(context, "+15551234567")
        assertEquals("+15551234567", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }
}
