package com.phonepvr.friends.data.contacts

import com.phonepvr.friends.data.contacts.BondContactMatcher.Via
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BondContactMatcherTest {

    private fun contact(
        id: Long,
        key: String,
        name: String,
        vararg numbers: String,
    ) = DeviceContact(
        contactId = id,
        lookupKey = key,
        displayName = name,
        phoneNumbers = numbers.toList(),
    )

    private fun person(
        id: Long,
        name: String,
        key: String? = null,
        contactId: Long? = null,
        archived: Boolean = false,
    ) = PersonEntity(
        id = id,
        uuid = "uuid-$id",
        displayName = name,
        contactLookupKey = key,
        contactId = contactId,
        isArchived = archived,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun details(person: PersonEntity, vararg numbers: String) = PersonWithDetails(
        person = person,
        phoneNumbers = numbers.mapIndexed { i, n ->
            PhoneNumberEntity(
                id = person.id * 100 + i,
                personId = person.id,
                rawNumber = n,
                normalizedNumber = n.filter { it.isDigit() },
            )
        },
        events = emptyList(),
    )

    private val neverResolves: (String) -> Long? = { null }

    // ---- resolve ---------------------------------------------------------

    @Test
    fun resolve_exactLookupKeyWins() {
        val index = ContactIndex(listOf(contact(1, "k1", "Ann"), contact(2, "k2", "Bob")))
        val match = BondContactMatcher.resolve(
            person(1, "Ann", key = "k1", contactId = 99),
            emptyList(),
            index,
            neverResolves,
        )
        assertEquals(1L, match?.contact?.contactId)
        assertEquals(Via.LOOKUP_KEY, match?.via)
    }

    @Test
    fun resolve_staleKeyFollowedByProvider() {
        // The contact was renamed: its lookup key changed from "old" to "new".
        val index = ContactIndex(listOf(contact(7, "new", "Mother")))
        val match = BondContactMatcher.resolve(
            person(1, "Mom", key = "old"),
            emptyList(),
            index,
        ) { key -> if (key == "old") 7L else null }
        assertEquals(7L, match?.contact?.contactId)
        assertEquals(Via.PROVIDER, match?.via)
    }

    @Test
    fun resolve_providerPointingAtAContactNotInTheIndexIsIgnored() {
        val index = ContactIndex(listOf(contact(7, "new", "Someone")))
        val match = BondContactMatcher.resolve(
            person(1, "Mom", key = "old"),
            emptyList(),
            index,
        ) { 123L }
        assertNull(match)
    }

    @Test
    fun resolve_savedIdRescuesAKeyTheProviderCannotFollow_whenPhoneMatches() {
        // Renamed local contact: new key, provider can't follow the old one, but
        // the id survived and the number is unchanged.
        val index = ContactIndex(
            listOf(contact(5, "newKey", "Mother", "+44 7700 900 123")),
        )
        val p = person(1, "Mom", key = "oldKey", contactId = 5)
        val match = BondContactMatcher.resolve(p, listOf("07700 900123"), index, neverResolves)
        assertEquals(5L, match?.contact?.contactId)
        assertEquals(Via.CONTACT_ID, match?.via)
    }

    @Test
    fun resolve_savedIdRescuesWhenNameMatches() {
        val index = ContactIndex(listOf(contact(5, "newKey", "mom")))
        val p = person(1, "Mom", key = "oldKey", contactId = 5)
        val match = BondContactMatcher.resolve(p, emptyList(), index, neverResolves)
        assertEquals(Via.CONTACT_ID, match?.via)
    }

    @Test
    fun resolve_savedIdAloneIsNotTrusted_wouldLinkAStranger() {
        // Contact ids restart if the contacts database is cleared; an id that now
        // belongs to a different person (other name, other number) must not relink.
        val index = ContactIndex(listOf(contact(5, "k", "Stranger", "555 0100")))
        val p = person(1, "Mom", key = "oldKey", contactId = 5)
        val match = BondContactMatcher.resolve(p, listOf("07700 900123"), index, neverResolves)
        assertNull(match)
    }

    @Test
    fun resolve_tooShortNumbersNeverCorroborate() {
        val index = ContactIndex(listOf(contact(5, "k", "Other", "1234")))
        val p = person(1, "Mom", contactId = 5)
        assertNull(BondContactMatcher.resolve(p, listOf("1234"), index, neverResolves))
    }

    @Test
    fun resolve_deletedContactIsNull() {
        val index = ContactIndex(listOf(contact(2, "k2", "Bob")))
        val p = person(1, "Ann", key = "k1", contactId = 1)
        assertNull(BondContactMatcher.resolve(p, listOf("07700 900123"), index, neverResolves))
    }

    @Test
    fun resolve_blankKeyIsTreatedAsNoKey() {
        val index = ContactIndex(listOf(contact(2, "", "Ann")))
        val p = person(1, "Ann", key = "  ", contactId = 2)
        val match = BondContactMatcher.resolve(p, emptyList(), index) { error("not asked") }
        assertEquals(Via.CONTACT_ID, match?.via)
    }

    @Test
    fun isLinked_distinguishesStandaloneBonds() {
        assertFalse(BondContactMatcher.isLinked(person(1, "Solo")))
        assertFalse(BondContactMatcher.isLinked(person(1, "Solo", key = " ")))
        assertTrue(BondContactMatcher.isLinked(person(1, "A", key = "k")))
        assertTrue(BondContactMatcher.isLinked(person(1, "A", contactId = 3)))
    }

    // ---- BondIndex ----------------------------------------------------------

    @Test
    fun bondIndex_matchesByKeyThenById() {
        val index = BondIndex(
            listOf(
                person(1, "Ann", key = "k1", contactId = 10),
                person(2, "Bob", key = "oldB", contactId = 20),
            ),
        )
        // Exact key.
        assertEquals(1L, index.personFor(contact(10, "k1", "Ann"))?.id)
        // Renamed: the key changed but the id still identifies the bond (#33).
        assertEquals(2L, index.personFor(contact(20, "newB", "Robert"))?.id)
        // Unrelated contact.
        assertNull(index.personFor(contact(30, "k3", "Cy")))
        // Blank key and no id never match.
        assertNull(index.personFor("", null))
        assertNull(index.personFor(null, null))
    }

    @Test
    fun bondIndex_keyTakesPrecedenceOverId() {
        val index = BondIndex(
            listOf(
                person(1, "Ann", key = "kA", contactId = 10),
                person(2, "Bob", key = "kB", contactId = 20),
            ),
        )
        // Key says Ann, id says Bob: the key is the stronger signal.
        assertEquals(1L, index.personFor("kA", 20)?.id)
    }

    @Test
    fun bondIndex_lowestIdWinsOnCollision() {
        val index = BondIndex(
            listOf(
                person(9, "Dup", key = "k", contactId = 1),
                person(3, "Dup", key = "k", contactId = 1),
            ),
        )
        assertEquals(3L, index.personFor("k", 1)?.id)
    }

    // ---- plan ---------------------------------------------------------------

    @Test
    fun plan_renamedContactGetsFreshKeyIdAndName() {
        val p = person(1, "Mom", key = "old", contactId = 5)
        val plan = BondContactMatcher.plan(
            people = listOf(details(p, "07700 900123")),
            favourites = emptyList(),
            contacts = listOf(contact(5, "new", "Mother", "07700 900123")),
            resolveLookupKey = neverResolves,
        )
        assertEquals(
            listOf(BondContactMatcher.LinkUpdate(1, "new", 5, "Mother")),
            plan.linkUpdates,
        )
        assertTrue(plan.unlinkedPersonIds.isEmpty())
    }

    @Test
    fun plan_upToDateBondsProduceNoWrites() {
        val p = person(1, "Ann", key = "k1", contactId = 10)
        val plan = BondContactMatcher.plan(
            listOf(details(p)),
            emptyList(),
            listOf(contact(10, "k1", "Ann")),
            neverResolves,
        )
        assertTrue(plan.linkUpdates.isEmpty())
        assertTrue(plan.unlinkedPersonIds.isEmpty())
    }

    @Test
    fun plan_backfillsContactIdForPreMigrationBonds() {
        // Rows created before schema v5 have a key but no id.
        val p = person(1, "Ann", key = "k1", contactId = null)
        val plan = BondContactMatcher.plan(
            listOf(details(p)),
            emptyList(),
            listOf(contact(10, "k1", "Ann")),
            neverResolves,
        )
        assertEquals(
            listOf(BondContactMatcher.LinkUpdate(1, "k1", 10, "Ann")),
            plan.linkUpdates,
        )
    }

    @Test
    fun plan_deletedContactIsReportedUnlinked_notRemoved() {
        val gone = person(1, "Ann", key = "k1", contactId = 10)
        val here = person(2, "Bob", key = "k2", contactId = 20)
        val plan = BondContactMatcher.plan(
            listOf(details(gone, "07700 900111"), details(here)),
            emptyList(),
            listOf(contact(20, "k2", "Bob")),
            neverResolves,
        )
        assertEquals(setOf(1L), plan.unlinkedPersonIds)
        // The unlinked bond is left exactly as it was: no rewrite, no deletion.
        assertTrue(plan.linkUpdates.none { it.personId == 1L })
    }

    @Test
    fun plan_archivedBondsAreNeverFlaggedUnlinked() {
        val archived = person(1, "Ann", key = "k1", contactId = 10, archived = true)
        val plan = BondContactMatcher.plan(
            listOf(details(archived)),
            emptyList(),
            listOf(contact(20, "k2", "Bob")),
            neverResolves,
        )
        assertTrue(plan.unlinkedPersonIds.isEmpty())
    }

    @Test
    fun plan_standaloneBondsAreIgnored() {
        val solo = person(1, "Solo")
        val plan = BondContactMatcher.plan(
            listOf(details(solo)),
            emptyList(),
            listOf(contact(20, "k2", "Bob")),
            neverResolves,
        )
        assertTrue(plan.linkUpdates.isEmpty())
        assertTrue(plan.unlinkedPersonIds.isEmpty())
    }

    @Test
    fun plan_emptyAddressBookNeverMassUnlinks() {
        val p = person(1, "Ann", key = "k1", contactId = 10)
        val plan = BondContactMatcher.plan(listOf(details(p)), emptyList(), emptyList(), neverResolves)
        assertTrue(plan.linkUpdates.isEmpty())
        assertTrue(plan.unlinkedPersonIds.isEmpty())
        assertTrue(plan.favouriteUpdates.isEmpty())
    }

    @Test
    fun plan_blankContactNameKeepsTheBondName() {
        val p = person(1, "Ann", key = "old", contactId = 10)
        val plan = BondContactMatcher.plan(
            listOf(details(p)),
            emptyList(),
            listOf(contact(10, "new", "")),
            { key -> if (key == "old") 10L else null },
        )
        assertEquals("Ann", plan.linkUpdates.single().displayName)
        assertEquals("new", plan.linkUpdates.single().lookupKey)
    }

    // ---- favourites ------------------------------------------------------------

    private fun fav(key: String, name: String) = FavouriteContactEntity(
        lookupKey = key,
        displayName = name,
        primaryNumber = "123456",
        addedAt = 0L,
    )

    @Test
    fun plan_favouriteFollowsRenamedContactViaProvider() {
        val plan = BondContactMatcher.plan(
            people = emptyList(),
            favourites = listOf(fav("old", "Mom")),
            contacts = listOf(contact(5, "new", "Mother")),
            resolveLookupKey = { key -> if (key == "old") 5L else null },
        )
        assertEquals(
            listOf(BondContactMatcher.FavouriteUpdate("old", "new", "Mother")),
            plan.favouriteUpdates,
        )
    }

    @Test
    fun plan_favouriteWithCurrentKeyOnlySyncsAChangedName() {
        val same = BondContactMatcher.plan(
            emptyList(),
            listOf(fav("k", "Ann")),
            listOf(contact(1, "k", "Ann")),
            neverResolves,
        )
        assertTrue(same.favouriteUpdates.isEmpty())
        val renamed = BondContactMatcher.plan(
            emptyList(),
            listOf(fav("k", "Ann")),
            listOf(contact(1, "k", "Anna")),
            neverResolves,
        )
        assertEquals("Anna", renamed.favouriteUpdates.single().displayName)
        assertEquals("k", renamed.favouriteUpdates.single().newLookupKey)
    }

    @Test
    fun plan_favouriteNeverMovesOntoAKeyAnotherFavouriteUses() {
        // Both the stale and the fresh key are pinned; re-keying would violate the
        // primary key, so the stale one is left alone.
        val plan = BondContactMatcher.plan(
            emptyList(),
            listOf(fav("old", "Mom"), fav("new", "Mother")),
            listOf(contact(5, "new", "Mother")),
            { key -> if (key == "old") 5L else null },
        )
        assertTrue(plan.favouriteUpdates.none { it.oldLookupKey == "old" })
    }

    @Test
    fun plan_favouriteForADeletedContactIsLeftAlone() {
        val plan = BondContactMatcher.plan(
            emptyList(),
            listOf(fav("gone", "Zed")),
            listOf(contact(1, "k", "Ann")),
            neverResolves,
        )
        assertTrue(plan.favouriteUpdates.isEmpty())
    }
}
