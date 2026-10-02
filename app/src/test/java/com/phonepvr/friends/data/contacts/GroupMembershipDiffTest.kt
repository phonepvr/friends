package com.phonepvr.friends.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMembershipDiffTest {

    private val editable = setOf(1L, 2L, 3L)

    @Test
    fun addingAGroupWritesOnlyThatGroup() {
        val change = GroupMembershipDiff.compute(current = setOf(1L), selected = setOf(1L, 2L), editable = editable)
        assertEquals(setOf(2L), change.add)
        assertTrue(change.remove.isEmpty())
    }

    @Test
    fun movingBetweenGroupsAddsOneAndRemovesTheOther() {
        val change = GroupMembershipDiff.compute(current = setOf(1L), selected = setOf(2L), editable = editable)
        assertEquals(setOf(2L), change.add)
        assertEquals(setOf(1L), change.remove)
    }

    @Test
    fun uncheckingEverythingRemovesAllMemberships() {
        val change = GroupMembershipDiff.compute(current = setOf(1L, 3L), selected = emptySet(), editable = editable)
        assertTrue(change.add.isEmpty())
        assertEquals(setOf(1L, 3L), change.remove)
    }

    @Test
    fun anUnchangedSelectionIsEmpty() {
        val change = GroupMembershipDiff.compute(current = setOf(1L, 2L), selected = setOf(2L, 1L), editable = editable)
        assertTrue(change.isEmpty)
    }

    @Test
    fun systemGroupsOutsideTheEditableSetAreNeverTouched() {
        // 99 is a hidden/system membership ("My Contacts"); it must survive even
        // though the user's selection (which can't contain it) doesn't mention it.
        val change = GroupMembershipDiff.compute(
            current = setOf(1L, 99L),
            selected = setOf(1L),
            editable = editable,
        )
        assertTrue(change.isEmpty)
        // And a selection can't add a group that isn't editable either.
        val sneaky = GroupMembershipDiff.compute(setOf(1L), setOf(1L, 98L), editable)
        assertTrue(sneaky.isEmpty)
    }
}
