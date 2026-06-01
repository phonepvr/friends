package com.phonepvr.friends.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateFinderTest {

    @Test
    fun clustersByNormalizedName_ignoringCaseWhitespaceAndBlanks() {
        val clusters = DuplicateFinder.find(
            listOf(
                1L to "John Smith",
                2L to "JOHN  smith ",
                3L to "Alice",
                4L to "Bob Jones",
                5L to "bob jones",
                6L to "",
                7L to "   ",
            ),
        )
        assertEquals(2, clusters.size)
        val byKey = clusters.associateBy { DuplicateFinder.normalize(it.displayName) }
        assertEquals(setOf(1L, 2L), byKey["john smith"]?.contactIds?.toSet())
        assertEquals(setOf(4L, 5L), byKey["bob jones"]?.contactIds?.toSet())
        // No cluster contains the unique contact.
        assertNull(clusters.find { 3L in it.contactIds })
    }

    @Test
    fun labelPrefersTidiestLongestSpelling() {
        val clusters = DuplicateFinder.find(
            listOf(1L to "JOHN  smith ", 2L to "John Smith"),
        )
        assertEquals(1, clusters.size)
        assertEquals("John Smith", clusters.single().displayName)
    }

    @Test
    fun singletonsAreNotDuplicates() {
        val clusters = DuplicateFinder.find(listOf(1L to "Solo Person"))
        assertEquals(0, clusters.size)
    }

    @Test
    fun normalizeCollapsesAndLowercases() {
        assertEquals("john smith", DuplicateFinder.normalize("  JoHn   Smith "))
    }
}
