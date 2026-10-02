package com.phonepvr.friends.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {

    private fun match(
        query: String,
        name: String = "Alex Morgan",
        phones: List<String> = emptyList(),
        extra: String? = null,
    ) = ContactSearch.matches(query, name, phones, extra?.let(ContactSearch::fold))

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(match(""))
        assertTrue(match("   "))
    }

    @Test
    fun namesMatchIgnoringCaseAndAccents() {
        assertTrue(match("morg"))
        assertTrue(match("ALEX"))
        assertTrue(match("jose", name = "José Álvarez"))
        assertTrue(match("alvarez", name = "José Álvarez"))
        assertTrue(match("é", name = "Zoe"))
    }

    @Test
    fun foldStripsAccentsAndLowercases() {
        assertEquals("jose alvarez", ContactSearch.fold("José Álvarez"))
        assertEquals("strasse", ContactSearch.fold("STRASSE"))
    }

    @Test
    fun organisationNicknameNotesAndEmailAreSearchable() {
        val extra = "Acme Corp\nSales Director\nBig Al\nmet at the 2019 conference\nalex@example.com"
        assertTrue(match("acme", extra = extra))
        assertTrue(match("director", extra = extra))
        assertTrue(match("big al", extra = extra))
        assertTrue(match("conference", extra = extra))
        assertTrue(match("example.com", extra = extra))
        assertFalse(match("globex", extra = extra))
    }

    @Test
    fun extraTextNotYetLoadedJustMeansNoExtraMatches() {
        assertFalse(match("acme", extra = null))
        assertTrue(match("alex", extra = null))
    }

    @Test
    fun phoneQueriesMatchAnyNumberNotJustTheFirst() {
        val phones = listOf("+44 7700 900111", "020 7946 0000")
        assertTrue(match("900111", phones = phones))
        assertTrue(match("7946", phones = phones))
        assertTrue(match("+44 7700", phones = phones))
        assertTrue(match("(020) 7946", phones = phones))
        assertFalse(match("5550000", phones = phones))
    }

    @Test
    fun aQueryWithLettersIsNeverAPhoneSearch() {
        // "gmail2" contains a digit, but must not match every number containing a 2.
        assertFalse(match("gmail2", phones = listOf("07700 900222")))
        assertFalse(match("a1", phones = listOf("07700 900111")))
        // ...yet it still finds text that really contains it.
        assertTrue(match("gmail2", extra = "alex@gmail2.example"))
    }

    @Test
    fun aSeparatorOnlyQueryIsNotAPhoneSearch() {
        assertFalse(match("+", phones = listOf("+44 7700 900111")))
        assertFalse(match("-", phones = listOf("07700-900111")))
    }

    @Test
    fun digitsInNamesStillMatchByName() {
        assertTrue(match("2pac", name = "2Pac"))
    }
}
