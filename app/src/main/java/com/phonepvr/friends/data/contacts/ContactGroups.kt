package com.phonepvr.friends.data.contacts

/** A contact group the user can put a contact into (Family, Work, …). */
data class ContactGroup(val id: Long, val title: String)

/**
 * Which groups a contact is in and which it could be moved into: the groups of the
 * contact's own account (a contact can only join groups of the account it lives
 * in) that are user-facing — see [ContactsReader.editableGroupsFor].
 */
data class ContactGroupState(
    /** Groups the contact may be added to or removed from, sorted by title. */
    val editable: List<ContactGroup> = emptyList(),
    /** Ids of [editable] groups the contact is currently in. */
    val memberOf: Set<Long> = emptySet(),
)

/** What to write to turn the current memberships into the user's selection. */
data class GroupMembershipChange(val add: Set<Long>, val remove: Set<Long>) {
    val isEmpty: Boolean get() = add.isEmpty() && remove.isEmpty()
}

object GroupMembershipDiff {

    /**
     * Compares [selected] with [current] and returns only the groups to add and to
     * remove. Anything outside [editable] is ignored on both sides, so the
     * contact's hidden / system memberships ("My Contacts", starred, sync groups)
     * are never touched: only the rows the user actually changed are written.
     */
    fun compute(
        current: Set<Long>,
        selected: Set<Long>,
        editable: Set<Long>,
    ): GroupMembershipChange {
        val wanted = selected.intersect(editable)
        val have = current.intersect(editable)
        return GroupMembershipChange(add = wanted - have, remove = have - wanted)
    }
}
