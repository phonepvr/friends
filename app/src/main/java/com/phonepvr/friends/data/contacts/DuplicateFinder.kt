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

    data class Cluster(
        /** A representative display name for the cluster. */
        val displayName: String,
        /** Contact ids that share the normalised name (size >= 2). */
        val contactIds: List<Long>,
    )

    /** [contacts] is (contactId, displayName). */
    fun find(contacts: List<Pair<Long, String>>): List<Cluster> {
        val byKey = LinkedHashMap<String, MutableList<Pair<Long, String>>>()
        for ((id, name) in contacts) {
            val key = normalize(name)
            if (key.isEmpty()) continue
            byKey.getOrPut(key) { mutableListOf() }.add(id to name)
        }
        return byKey.values
            .filter { it.size >= 2 }
            .map { group ->
                Cluster(
                    // Prefer the longest *tidied* spelling as the label (usually
                    // the most complete: "Jon" vs "Jon Smith"). Tidying first
                    // avoids picking a messier "JOHN  smith " over "John Smith"
                    // just because of stray whitespace.
                    displayName = group
                        .map { it.second.trim().replace(Regex("\\s+"), " ") }
                        .maxByOrNull { it.length }!!,
                    contactIds = group.map { it.first },
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    /** Lowercase, collapse internal whitespace, trim. */
    fun normalize(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")
}
