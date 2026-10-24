package com.phonepvr.friends.ui.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BondSelectionTest {

    @Test
    fun longPressStartsSelectionAndTapsAddOrDrop() {
        var s = BondSelection()
        assertFalse(s.active)
        s = s.toggle(1)
        assertTrue(s.active)
        s = s.toggle(2)
        assertEquals(setOf(1L, 2L), s.ids)
        s = s.toggle(1)
        assertEquals(setOf(2L), s.ids)
        s = s.toggle(2)
        assertFalse(s.active)
    }

    @Test
    fun selectAllSelectsEveryShownBondThenClears() {
        val shown = listOf(1L, 2L, 3L)
        val all = BondSelection(setOf(2L)).selectAll(shown)
        assertEquals(shown.toSet(), all.ids)
        assertFalse(all.selectAll(shown).active)
    }

    @Test
    fun bondsNoLongerShownDropOutOfTheSelection() {
        val s = BondSelection(setOf(1L, 2L, 3L)).keepOnly(listOf(2L, 3L, 4L))
        assertEquals(setOf(2L, 3L), s.ids)
    }
}
