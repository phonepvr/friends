package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FavouriteOrderTest {

    @Test
    fun moveShiftsTheOthers() {
        assertEquals(listOf("b", "c", "a"), FavouriteOrder.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), FavouriteOrder.move(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "c", "b"), FavouriteOrder.move(listOf("a", "b", "c"), 1, 2))
    }

    @Test
    fun moveIgnoresNoOpsAndOutOfRange() {
        val list = listOf("a", "b")
        assertEquals(list, FavouriteOrder.move(list, 0, 0))
        assertEquals(list, FavouriteOrder.move(list, -1, 1))
        assertEquals(list, FavouriteOrder.move(list, 0, 5))
    }

    @Test
    fun dragSwapsAfterHalfARow() {
        val h = 100f
        assertEquals(1, FavouriteOrder.dragTarget(1, 49f, h, 4))
        assertEquals(2, FavouriteOrder.dragTarget(1, 51f, h, 4))
        assertEquals(3, FavouriteOrder.dragTarget(1, 160f, h, 4))
        assertEquals(0, FavouriteOrder.dragTarget(1, -60f, h, 4))
    }

    @Test
    fun dragStaysInsideTheList() {
        assertEquals(3, FavouriteOrder.dragTarget(1, 10_000f, 100f, 4))
        assertEquals(0, FavouriteOrder.dragTarget(1, -10_000f, 100f, 4))
        assertEquals(1, FavouriteOrder.dragTarget(1, 50f, 0f, 4))
    }

    private fun fav(key: String, position: Int, addedAt: Long = position.toLong()) =
        FavouriteContactEntity(
            lookupKey = key,
            displayName = key,
            primaryNumber = "555$position",
            position = position,
            addedAt = addedAt,
        )

    @Test
    fun withPositionsRenumbersInTheGivenOrder() {
        val current = listOf(fav("a", 0), fav("b", 1), fav("c", 2))
        val result = FavouriteOrder.withPositions(current, listOf("c", "a", "b"))
        assertEquals(listOf("c" to 0, "a" to 1, "b" to 2), result.map { it.lookupKey to it.position })
    }

    @Test
    fun withPositionsKeepsUnlistedFavouritesAfterAndIgnoresUnknownKeys() {
        val current = listOf(fav("a", 0), fav("b", 1), fav("c", 2), fav("d", 3))
        val result = FavouriteOrder.withPositions(current, listOf("c", "zzz", "a", "c"))
        assertEquals(
            listOf("c" to 0, "a" to 1, "b" to 2, "d" to 3),
            result.map { it.lookupKey to it.position },
        )
    }
}
