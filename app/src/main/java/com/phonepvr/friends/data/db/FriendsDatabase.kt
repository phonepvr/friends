package com.phonepvr.friends.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.phonepvr.friends.data.db.dao.EventDao
import com.phonepvr.friends.data.db.dao.FavouriteContactDao
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity

@Database(
    entities = [
        PersonEntity::class,
        PhoneNumberEntity::class,
        EventEntity::class,
        TimelineEntryEntity::class,
        FavouriteContactEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FriendsDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun phoneNumberDao(): PhoneNumberDao
    abstract fun eventDao(): EventDao
    abstract fun timelineDao(): TimelineDao
    abstract fun favouriteContactDao(): FavouriteContactDao
}

/**
 * Adds call direction + duration to timeline entries and duration to pending
 * confirmations. All three columns are nullable so existing rows keep their
 * meaning (direction/duration unknown) without any backfill.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE timeline_entries ADD COLUMN callDirection TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE timeline_entries ADD COLUMN callDurationSeconds INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE pending_confirmations ADD COLUMN durationSeconds INTEGER DEFAULT NULL")
    }
}

/**
 * Drops the now-orphaned pending_confirmations table. The Calls tab and its
 * global confirmation queue were replaced by an on-demand per-profile scan
 * (see PersonDetailViewModel.scanCalls + CallLogRepository.scanForPerson) —
 * call attribution lives entirely in the user's timeline now, with dedupe by
 * callDedupKey, so no persisted intermediate state is needed.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS pending_confirmations")
    }
}

/**
 * Adds the favourite_contacts table that backs the Favourites strip on
 * the Calls tab. Pure additive — no existing rows touched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favourite_contacts (
                lookupKey TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                primaryNumber TEXT NOT NULL,
                photoRelativePath TEXT,
                position INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
