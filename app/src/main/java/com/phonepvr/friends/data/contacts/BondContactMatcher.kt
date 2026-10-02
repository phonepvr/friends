package com.phonepvr.friends.data.contacts

import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails

/**
 * Pure (Android-free) logic for tying bonds to address-book contacts.
 *
 * A bond stores the contact's lookup key plus its numeric id. The lookup key is
 * what ContactsContract calls the long-lived handle, but for many local
 * contacts it embeds the display name, so renaming the contact changes it and a
 * plain string compare against the bond's saved key silently stops matching
 * (issue #33). Everything that needs "which bond is this contact?" or "which
 * contact is this bond?" goes through this file so the rule lives in one place.
 */

/** The current address book, indexed for O(1) lookup by key or id. */
class ContactIndex(contacts: List<DeviceContact>) {
    private val byKey: Map<String, DeviceContact> = HashMap<String, DeviceContact>().also { map ->
        for (c in contacts) if (c.lookupKey.isNotBlank()) map.putIfAbsent(c.lookupKey, c)
    }
    private val byId: Map<Long, DeviceContact> = HashMap<Long, DeviceContact>().also { map ->
        for (c in contacts) map.putIfAbsent(c.contactId, c)
    }

    val isEmpty: Boolean = contacts.isEmpty()

    fun byLookupKey(key: String): DeviceContact? = byKey[key]

    fun byContactId(id: Long): DeviceContact? = byId[id]
}

/**
 * The bonds, indexed so a contact can be mapped to the bond that tracks it —
 * by lookup key first, then by contact id (which survives a key change).
 * Build it from the *active* bonds; the lowest id wins if two bonds collide.
 */
class BondIndex(people: Collection<PersonEntity>) {
    private val byKey = HashMap<String, PersonEntity>()
    private val byContactId = HashMap<Long, PersonEntity>()

    init {
        for (p in people.sortedBy { it.id }) {
            p.contactLookupKey?.takeIf { it.isNotBlank() }?.let { byKey.putIfAbsent(it, p) }
            p.contactId?.let { byContactId.putIfAbsent(it, p) }
        }
    }

    /** The bond tracking [contact], or null when it isn't bonded. */
    fun personFor(contact: DeviceContact): PersonEntity? =
        personFor(contact.lookupKey, contact.contactId)

    fun personFor(lookupKey: String?, contactId: Long?): PersonEntity? {
        if (!lookupKey.isNullOrBlank()) byKey[lookupKey]?.let { return it }
        return contactId?.let { byContactId[it] }
    }
}

object BondContactMatcher {

    /** How a bond was matched to its contact — mostly useful in tests. */
    enum class Via {
        /** The bond's saved lookup key is still the contact's current key. */
        LOOKUP_KEY,

        /** The contacts provider followed a stale key to the contact. */
        PROVIDER,

        /** Saved contact id still exists and the contact is plausibly the same person. */
        CONTACT_ID,
    }

    data class Match(val contact: DeviceContact, val via: Via)

    /** True when [person] was ever linked to an address-book contact. */
    fun isLinked(person: PersonEntity): Boolean =
        !person.contactLookupKey.isNullOrBlank() || person.contactId != null

    /**
     * Finds the contact [person] points at today, or null if it can't be found
     * (deleted, or the link broke in a way we can't safely repair).
     *
     * @param phoneNumbers the bond's stored numbers, used to corroborate an
     *   id-only match.
     * @param resolveLookupKey asks the contacts provider where a stale lookup
     *   key now points (it follows merges and, for some keys, renames); returns
     *   the contact id or null.
     */
    fun resolve(
        person: PersonEntity,
        phoneNumbers: List<String>,
        index: ContactIndex,
        resolveLookupKey: (String) -> Long?,
    ): Match? {
        val key = person.contactLookupKey?.takeIf { it.isNotBlank() }
        if (key != null) {
            index.byLookupKey(key)?.let { return Match(it, Via.LOOKUP_KEY) }
            resolveLookupKey(key)
                ?.let { id -> index.byContactId(id) }
                ?.let { return Match(it, Via.PROVIDER) }
        }
        // Last resort: the saved id. Ids are not reused while the contacts
        // database lives, but they restart if its storage is cleared, so only
        // trust one when the contact shares the bond's name or a phone number.
        val id = person.contactId ?: return null
        val contact = index.byContactId(id) ?: return null
        return if (corroborates(person, phoneNumbers, contact)) {
            Match(contact, Via.CONTACT_ID)
        } else {
            null
        }
    }

    private fun corroborates(
        person: PersonEntity,
        phoneNumbers: List<String>,
        contact: DeviceContact,
    ): Boolean {
        if (person.displayName.trim().equals(contact.displayName.trim(), ignoreCase = true)) {
            return true
        }
        return sharesPhoneNumber(phoneNumbers, contact.phoneNumbers)
    }

    /** True when the two number lists share a number (compared by trailing digits). */
    fun sharesPhoneNumber(a: List<String>, b: List<String>): Boolean {
        val wanted = a.mapNotNull(::phoneSuffix).toSet()
        return b.mapNotNull(::phoneSuffix).any { it in wanted }
    }

    /** Last [SUFFIX_DIGITS] digits of a number, or null if it's too short to compare. */
    private fun phoneSuffix(number: String): String? =
        number.filter { it.isDigit() }.takeLast(SUFFIX_DIGITS).takeIf { it.length >= MIN_DIGITS }

    private const val SUFFIX_DIGITS = 9
    private const val MIN_DIGITS = 6

    /** A bond's link, rewritten to point at the contact it now resolves to. */
    data class LinkUpdate(
        val personId: Long,
        val lookupKey: String?,
        val contactId: Long,
        val displayName: String,
    )

    /** A favourite whose contact was found under a new lookup key and/or name. */
    data class FavouriteUpdate(
        val oldLookupKey: String,
        val newLookupKey: String,
        val displayName: String,
    )

    data class Plan(
        val linkUpdates: List<LinkUpdate>,
        /** Active bonds that were linked to a contact that can no longer be found. */
        val unlinkedPersonIds: Set<Long>,
        val favouriteUpdates: List<FavouriteUpdate>,
    )

    /**
     * Works out what the reconciler must change for the current [contacts]:
     * which bonds/favourites need their link rewritten and which active bonds
     * have lost their contact. Does no I/O; the caller applies the result.
     *
     * An empty [contacts] list is treated as "address book unavailable" and
     * yields an empty plan — never mass-unlink everything because the provider
     * hiccupped or permission was revoked.
     */
    fun plan(
        people: List<PersonWithDetails>,
        favourites: List<FavouriteContactEntity>,
        contacts: List<DeviceContact>,
        resolveLookupKey: (String) -> Long?,
    ): Plan {
        if (contacts.isEmpty()) return Plan(emptyList(), emptySet(), emptyList())
        val index = ContactIndex(contacts)

        val linkUpdates = ArrayList<LinkUpdate>()
        val unlinked = LinkedHashSet<Long>()
        for (details in people.sortedBy { it.person.id }) {
            val person = details.person
            if (!isLinked(person)) continue
            val match = resolve(
                person = person,
                phoneNumbers = details.phoneNumbers.map { it.rawNumber },
                index = index,
                resolveLookupKey = resolveLookupKey,
            )
            if (match == null) {
                if (!person.isArchived) unlinked.add(person.id)
                continue
            }
            val contact = match.contact
            val newKey = contact.lookupKey.takeIf { it.isNotBlank() }
            val newName = contact.displayName.ifBlank { person.displayName }
            if (person.contactLookupKey != newKey ||
                person.contactId != contact.contactId ||
                person.displayName != newName
            ) {
                linkUpdates.add(LinkUpdate(person.id, newKey, contact.contactId, newName))
            }
        }

        val favouriteUpdates = ArrayList<FavouriteUpdate>()
        val taken = favourites.map { it.lookupKey }.toMutableSet()
        for (fav in favourites) {
            val contact = index.byLookupKey(fav.lookupKey)
                ?: resolveLookupKey(fav.lookupKey)?.let { index.byContactId(it) }
                ?: continue
            val newKey = contact.lookupKey.takeIf { it.isNotBlank() } ?: continue
            if (newKey == fav.lookupKey && contact.displayName == fav.displayName) continue
            // Don't move onto a key another favourite already uses (the stale
            // and fresh keys both being pinned would violate the primary key).
            if (newKey != fav.lookupKey && newKey in taken) continue
            favouriteUpdates.add(FavouriteUpdate(fav.lookupKey, newKey, contact.displayName))
            taken.remove(fav.lookupKey)
            taken.add(newKey)
        }
        return Plan(linkUpdates, unlinked, favouriteUpdates)
    }
}
