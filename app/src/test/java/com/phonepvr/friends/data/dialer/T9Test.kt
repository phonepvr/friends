package com.phonepvr.friends.data.dialer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9Test {

    @Test
    fun toDigits_mapsLettersAndDropsPunctuation() {
        assertEquals("72724", T9.toDigits("Sarah"))
        assertEquals("5646", T9.toDigits("John"))
        assertEquals("76484", T9.toDigits("Smith"))
        // Digits pass through; '+' and spaces are dropped.
        assertEquals("447700123", T9.toDigits("+44 7700 123"))
    }

    @Test
    fun nameKey_encodesWordsStartsAndInitials() {
        val key = T9.nameKey("John Smith")
        assertEquals("564676484", key.nameDigits)
        assertEquals(listOf(0, 4), key.wordStarts)
        assertEquals("57", key.initials)
    }

    @Test
    fun nameKey_splitsOnPunctuation() {
        val key = T9.nameKey("Mary-Jane")
        assertEquals("62795263", key.nameDigits)
        assertEquals(listOf(0, 4), key.wordStarts)
        assertEquals("65", key.initials)
    }

    @Test
    fun rank_fullNamePrefixIsStrongest() {
        val key = T9.nameKey("John Smith")
        assertEquals(T9.RANK_NAME_PREFIX, T9.rank(key, "5646")) // "John"
    }

    @Test
    fun rank_laterWordPrefixIsWordRank() {
        val key = T9.nameKey("John Smith")
        assertEquals(T9.RANK_WORD_PREFIX, T9.rank(key, "76484")) // "Smith"
    }

    @Test
    fun rank_initialsMatch() {
        val key = T9.nameKey("John Smith")
        assertEquals(T9.RANK_INITIALS, T9.rank(key, "57")) // J,S
    }

    @Test
    fun rank_looseSubstringIsWeakest() {
        val key = T9.nameKey("John Smith") // nameDigits = "564676484"
        // "46" sits mid-name (the h,n of "John") — not a word start, not
        // initials — so it falls through to the weakest bucket.
        assertEquals(T9.RANK_SUBSTRING, T9.rank(key, "46"))
    }

    @Test
    fun rank_noMatchReturnsNull() {
        val key = T9.nameKey("John Smith")
        assertNull(T9.rank(key, "00"))
    }

    @Test
    fun rank_emptyQueryIsNull() {
        assertNull(T9.rank(T9.nameKey("John Smith"), ""))
    }

    @Test
    fun rank_singleWordPrefixAndSubstring() {
        val key = T9.nameKey("Sarah")
        assertEquals(T9.RANK_NAME_PREFIX, T9.rank(key, "72")) // "Sa"
        // "27" only appears mid-string, not at a word start.
        assertEquals(T9.RANK_SUBSTRING, T9.rank(key, "27"))
    }
}
