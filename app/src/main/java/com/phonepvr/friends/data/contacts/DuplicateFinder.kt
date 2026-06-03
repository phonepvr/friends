package com.phonepvr.friends.data.contacts

/**
 * Finds likely-duplicate contacts so the user can merge them. Pure and
 * in-memory over the already-loaded contact list — no provider access — so
 * detection is cheap and unit-testable.
 *
 * v1 clusters on the normalised display name (case- and whitespace-
 * insensitive). That's the canonical "same contact imported twice" case and
 * keeps false positives low; number-based matching (which can wrongly group
 * people sharing a household line) is intentionally left out.
 */
object DuplicateFinder {

    /** One contact in a duplicate cluster, with the detail the user needs to
     *  decide whether to merge: its stored name and its phone numbers. */
    data class Member(
        val contactId: Long,
        val displayName: String,
        val numbers: List<String> = emptyList(),
    )

    data class Cluster(
        /** A representative display name for the cluster. */
        val displayName: String,
        /** The contacts that share the normalised name (size >= 2). */
        val members: List<Member>,
    ) {
        val contactIds: List<Long> get() = members.map { it.contactId }
    }

    fun find(contacts: List<Member>): List<Cluster> {
        val byKey = LinkedHashMap<String, MutableList<Member>>()
        for (member in contacts) {
            val key = normalize(member.displayName)
            if (key.isEmpty()) continue
            byKey.getOrPut(key) { mutableListOf() }.add(member)
        }
        return byKey.values
            .filter { it.size >= 2 }
            .map { group ->
                Cluster(
                    // Pick the nicest label deterministically: longest tidied
                    // spelling (usually the most complete — "Jon" vs "Jon
                    // Smith"), and on a length tie prefer proper case over
                    // ALL CAPS by favouring more lowercase letters ("John
                    // Smith" beats "JOHN SMITH"). Tidying first ignores stray
                    // whitespace.
                    displayName = group
                        .map { it.displayName.trim().replace(Regex("\\s+"), " ") }
                        .maxWith(
                            compareBy({ it.length }, { s -> s.count(Char::isLowerCase) }),
                        ),
                    members = group,
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    /** Lowercase, collapse internal whitespace, trim. */
    fun normalize(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")
}
