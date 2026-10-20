package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import kotlin.math.roundToInt

/** Pure helpers behind reordering the favourites strip. */
object FavouriteOrder {

    /** [list] with the item at [from] moved to [to]; out-of-range moves are ignored. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Where a row picked up at [from] belongs after being dragged [offsetPx]
     * vertically, with every row [rowHeightPx] tall: it swaps with a neighbour once
     * it is dragged more than halfway over it.
     */
    fun dragTarget(from: Int, offsetPx: Float, rowHeightPx: Float, size: Int): Int {
        if (size <= 0 || rowHeightPx <= 0f) return from
        return (from + (offsetPx / rowHeightPx).roundToInt()).coerceIn(0, size - 1)
    }

    /**
     * [current] renumbered 0, 1, 2… in the order given by [lookupKeys]. Favourites
     * missing from [lookupKeys] (added meanwhile) keep their relative order after
     * the listed ones; unknown keys are ignored.
     */
    fun withPositions(
        current: List<FavouriteContactEntity>,
        lookupKeys: List<String>,
    ): List<FavouriteContactEntity> {
        val byKey = current.associateBy { it.lookupKey }
        val listed = lookupKeys.distinct().mapNotNull { byKey[it] }
        val listedKeys = listed.mapTo(HashSet()) { it.lookupKey }
        val rest = current.filter { it.lookupKey !in listedKeys }
            .sortedWith(compareBy({ it.position }, { it.addedAt }))
        return (listed + rest).mapIndexed { index, fav -> fav.copy(position = index) }
    }
}
