package com.phonepvr.friends.data.dialer

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #46: USSD / service codes must reach Telecom with their `#` intact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelUriTest {

    @Test
    fun ussdAndServiceCodes_keepTheirHash() {
        for (code in listOf("*123#", "*#06#", "*100*2#", "#31#5551234567", "**21*5551234567#")) {
            val uri = telUri(code)
            assertEquals("tel", uri.scheme)
            // What Telecom and our own deep-link handler read back.
            assertEquals(code, uri.schemeSpecificPart)
        }
    }

    @Test
    fun hashIsEncodedInTheUri() {
        assertEquals("tel:*123%23", telUri("*123#").toString())
    }

    @Test
    fun ordinaryNumbersAreUnchanged() {
        assertEquals("+15551234567", telUri("+15551234567").schemeSpecificPart)
        assertEquals("0201234567", telUri("0201234567").schemeSpecificPart)
    }

    @Test
    fun concatenatedUri_wasTheBug() {
        // The old "tel:$number" form: '#' starts the URI fragment, so the code is cut.
        assertEquals("*123", Uri.parse("tel:*123#").schemeSpecificPart)
    }
}
