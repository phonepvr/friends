package com.phonepvr.friends.data.dialer

import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.data.contacts.DeviceContact
import com.phonepvr.friends.domain.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentContactsTest {

    private fun contact(id: Long, name: String, vararg numbers: String) =
        DeviceContact(contactId = id, lookupKey = "k$id", displayName = name, phoneNumbers = numbers.toList())

    private fun call(number: String, at: Long, type: CallType = CallType.OUTGOING) =
        DeviceCall(number = number, type = type, timestampMillis = at, durationSeconds = 30)

    private val ann = contact(1, "Ann", "07700 900111")
    private val bob = contact(2, "Bob", "+44 7700 900222", "020 7946 0000")
    private val cy = contact(3, "Cy", "07700 900333")

    @Test
    fun mostRecentCallComesFirst() {
        val calls = listOf(
            call("07700900111", at = 100),
            call("07700900333", at = 300),
            call("07700900222", at = 200),
        )
        val result = RecentContacts.fromCalls(calls, listOf(ann, bob, cy))
        assertEquals(listOf("Cy", "Bob", "Ann"), result.map { it.displayName })
    }

    @Test
    fun aContactCalledSeveralTimesAppearsOnceAtItsLatestCall() {
        val calls = listOf(
            call("07700900111", at = 100),
            call("07700900222", at = 150),
            call("07700900111", at = 200, type = CallType.MISSED),
        )
        val result = RecentContacts.fromCalls(calls, listOf(ann, bob))
        assertEquals(listOf("Ann", "Bob"), result.map { it.displayName })
    }

    @Test
    fun differentCountryCodePrefixesStillMatch() {
        // Logged with +44, saved with a leading 0; and the contact's second number matches too.
        val calls = listOf(
            call("+447700900111", at = 100),
            call("+442079460000", at = 200),
        )
        val result = RecentContacts.fromCalls(calls, listOf(ann, bob))
        assertEquals(listOf("Bob", "Ann"), result.map { it.displayName })
    }

    @Test
    fun unknownHiddenAndTooShortNumbersAreSkipped() {
        val calls = listOf(
            call("07700999999", at = 400), // not in the address book
            call("", at = 300), // hidden caller id
            call("123", at = 200), // too short to compare
            call("07700900111", at = 100),
        )
        val result = RecentContacts.fromCalls(calls, listOf(ann, bob))
        assertEquals(listOf("Ann"), result.map { it.displayName })
    }

    @Test
    fun theListIsCappedAtTheLimit() {
        val contacts = (1L..30L).map { contact(it, "C$it", "0770090%04d".format(it)) }
        val calls = contacts.mapIndexed { i, c -> call(c.phoneNumbers.single(), at = i.toLong()) }
        val result = RecentContacts.fromCalls(calls, contacts, limit = 5)
        assertEquals(5, result.size)
        assertEquals("C30", result.first().displayName)
    }

    @Test
    fun emptyInputsGiveAnEmptyList() {
        assertTrue(RecentContacts.fromCalls(emptyList(), listOf(ann)).isEmpty())
        assertTrue(RecentContacts.fromCalls(listOf(call("07700900111", 1)), emptyList()).isEmpty())
        assertTrue(RecentContacts.fromCalls(listOf(call("07700900111", 1)), listOf(ann), limit = 0).isEmpty())
    }

    @Test
    fun everyCallTypeCounts() {
        val calls = CallType.entries.mapIndexed { i, type ->
            call("0770090011${i + 1}".take(11), at = i.toLong(), type = type)
        }
        // Types don't matter: a rejected or missed call still means "you were in touch".
        val contacts = calls.mapIndexed { i, c -> contact(i + 10L, "P$i", c.number) }
        assertEquals(CallType.entries.size, RecentContacts.fromCalls(calls, contacts).size)
    }
}
