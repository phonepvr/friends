package com.phonepvr.friends.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.phonepvr.friends.data.db.dao.EventDao
import com.phonepvr.friends.data.db.dao.PendingConfirmationDao
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PendingConfirmationEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity

@Database(
    entities = [
        PersonEntity::class,
        PhoneNumberEntity::class,
        EventEntity::class,
        TimelineEntryEntity::class,
        PendingConfirmationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FriendsDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun phoneNumberDao(): PhoneNumberDao
    abstract fun eventDao(): EventDao
    abstract fun timelineDao(): TimelineDao
    abstract fun pendingConfirmationDao(): PendingConfirmationDao
}
