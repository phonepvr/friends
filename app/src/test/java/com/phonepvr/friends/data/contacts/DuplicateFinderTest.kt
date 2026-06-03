package com.phonepvr.friends.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateFinderTest {

    private fun m(id: Long, name: String, vararg numbers: String) =
        DuplicateFinder.Member(id, name, numbers.toList())

    @Test
    fun clustersByNormalizedName_ignoringCaseWhitespaceAndBlanks() {
        val clusters = DuplicateFinder.find(
            listOf(
                m(1L, "John Smith"),
                m(2L, "JOHN  smith "),
                m(3L, "Alice"),
                m(4L, "Bob Jones"),
                m(5L, "bob jones"),
                m(6L, ""),
                m(7L, "   "),
            ),
        )
        assertEquals(2, clusters.size)
        val byKey = clusters.associateBy { DuplicateFinder.normalize(it.displayName) }
        assertEquals(setOf(1L, 2L), byKey["john smith"]?.contactIds?.toSet())
        assertEquals(setOf(4L, 5L), byKey["bob jones"]?.contactIds?.toSet())
        assertNull(clusters.find { 3L in it.contactIds })
    }

    @Test
    fun labelPrefersTidiestLongestSpelling() {
        val clusters = DuplicateFinder.find(
            listOf(m(1L, "JOHN  smith "), m(2L, "John Smith")),
        )
        assertEquals(1, clusters.size)
        assertEquals("John Smith", clusters.single().displayName)
    }

    @Test
    fun clusterCarriesMembersWithNumbers() {
        val clusters = DuplicateFinder.find(
            listOf(
                m(1L, "John Smith", "+1 555 0001"),
                m(2L, "John Smith", "+1 555 0002"),
            ),
        )
        val members = clusters.single().members
        assertEquals(2, members.size)
        assertEquals(listOf("+1 555 0001"), members.first { it.contactId == 1L }.numbers)
        assertEquals(listOf("+1 555 0002"), members.first { it.contactId == 2L }.numbers)
    }

    @Test
    fun singletonsAreNotDuplicates() {
        assertEquals(0, DuplicateFinder.find(listOf(m(1L, "Solo Person"))).size)
    }

    @Test
    fun normalizeCollapsesAndLowercases() {
        assertEquals("john smith", DuplicateFinder.normalize("  JoHn   Smith "))
    }
}
