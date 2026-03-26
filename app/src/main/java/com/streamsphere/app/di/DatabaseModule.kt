package com.streamsphere.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.streamsphere.app.data.api.AppDatabase
import com.streamsphere.app.data.api.FavouritesDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "streamsphere_db"
        )
        // Explicitly disable multi-instance invalidation.
        // This prevents Room from binding to MultiInstanceInvalidationService,
        // which causes a NullPointerException when jUPnP triggers a service
        // connection before Room's registry is initialized.
        // See: https://issuetracker.google.com/issues/175237602
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideFavouritesDao(db: AppDatabase): FavouritesDao {
        return db.favouritesDao()
    }
}
