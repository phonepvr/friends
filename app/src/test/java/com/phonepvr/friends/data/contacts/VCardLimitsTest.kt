package com.phonepvr.friends.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class VCardLimitsTest {

    @Test
    fun smallFilesReadNormallyIncludingUtf8() {
        val text = "BEGIN:VCARD\nFN:José Álvarez\nEND:VCARD\n"
        assertEquals(text, VCardLimits.readCapped(ByteArrayInputStream(text.toByteArray())))
    }

    @Test
    fun aFileExactlyAtTheLimitIsAccepted() {
        val bytes = ByteArray(1000) { 'a'.code.toByte() }
        assertEquals(1000, VCardLimits.readCapped(ByteArrayInputStream(bytes), maxBytes = 1000).length)
    }

    @Test
    fun oneByteOverTheLimitIsRejected() {
        val bytes = ByteArray(1001) { 'a'.code.toByte() }
        try {
            VCardLimits.readCapped(ByteArrayInputStream(bytes), maxBytes = 1000)
            fail("expected VCardTooLargeException")
        } catch (e: VCardTooLargeException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test
    fun aNeverEndingStreamIsCutOffInsteadOfExhaustingMemory() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 'x'.code.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'x'.code.toByte())
                served += len
                return len
            }
        }
        try {
            VCardLimits.readCapped(endless, maxBytes = 64 * 1024)
            fail("expected VCardTooLargeException")
        } catch (e: VCardTooLargeException) {
            // It stopped reading right after the limit, not gigabytes later.
            assertTrue("read $served bytes", served <= 64 * 1024 + 8 * 1024)
        }
    }

    @Test
    fun cardCountIsCapped() {
        VCardLimits.requireCardCount(VCardLimits.MAX_CARDS)
        try {
            VCardLimits.requireCardCount(VCardLimits.MAX_CARDS + 1)
            fail("expected VCardTooLargeException")
        } catch (e: VCardTooLargeException) {
            assertTrue(e.message!!.contains("too many"))
        }
    }
}
