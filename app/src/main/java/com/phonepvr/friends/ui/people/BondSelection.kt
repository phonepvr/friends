package com.phonepvr.friends.ui.people

/**
 * Which bonds are selected on the Bonds tab (long-press to start, tap to add or
 * drop one). Pure so it can be unit-tested; the ViewModel holds one.
 */
data class BondSelection(val ids: Set<Long> = emptySet()) {
    val active: Boolean get() = ids.isNotEmpty()
    val count: Int get() = ids.size

    fun toggle(id: Long): BondSelection = BondSelection(if (id in ids) ids - id else ids + id)

    /** Selects every bond shown, or clears the selection when all already are. */
    fun selectAll(shown: Collection<Long>): BondSelection =
        if (shown.isNotEmpty() && ids.containsAll(shown)) BondSelection() else BondSelection(ids + shown)

    /** Drops ids no longer shown (removed elsewhere, or filtered out by search). */
    fun keepOnly(shown: Collection<Long>): BondSelection = BondSelection(ids intersect shown.toSet())
}
