package com.phonepvr.friends.di

import android.content.Context
import androidx.room.Room
import com.phonepvr.friends.data.db.FriendsDatabase
import com.phonepvr.friends.data.db.MIGRATION_1_2
import com.phonepvr.friends.data.db.MIGRATION_2_3
import com.phonepvr.friends.data.db.dao.EventDao
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FriendsDatabase =
        Room.databaseBuilder(context, FriendsDatabase::class.java, "friends.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun providePersonDao(database: FriendsDatabase): PersonDao = database.personDao()

    @Provides
    fun providePhoneNumberDao(database: FriendsDatabase): PhoneNumberDao =
        database.phoneNumberDao()

    @Provides
    fun provideEventDao(database: FriendsDatabase): EventDao = database.eventDao()

    @Provides
    fun provideTimelineDao(database: FriendsDatabase): TimelineDao = database.timelineDao()
}
