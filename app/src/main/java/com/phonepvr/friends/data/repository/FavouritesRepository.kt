package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.db.dao.FavouriteContactDao
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny wrapper around [FavouriteContactDao] for the Favourites strip on
 * the Calls tab. Favourites are keyed by contact lookup-key so the
 * pin survives a contact rename/merge in the system address book.
 */
@Singleton
class FavouritesRepository @Inject constructor(
    private val dao: FavouriteContactDao,
) {
    fun observeAll(): Flow<List<FavouriteContactEntity>> = dao.observeAll()

    fun observeIsFavourite(lookupKey: String): Flow<Boolean> =
        dao.observeIsFavourite(lookupKey)

    suspend fun toggle(
        lookupKey: String,
        displayName: String,
        primaryNumber: String,
        photoRelativePath: String?,
    ) {
        if (lookupKey.isBlank()) return
        val isFavourite = dao.observeIsFavourite(lookupKey).first()
        if (isFavourite) {
            dao.delete(lookupKey)
        } else {
            val nextPosition = (dao.maxPosition() ?: -1) + 1
            dao.upsert(
                FavouriteContactEntity(
                    lookupKey = lookupKey,
                    displayName = displayName,
                    primaryNumber = primaryNumber,
                    photoRelativePath = photoRelativePath,
                    position = nextPosition,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
