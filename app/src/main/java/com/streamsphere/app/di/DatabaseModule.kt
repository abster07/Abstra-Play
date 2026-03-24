package com.streamsphere.app.di

import android.content.Context
import androidx.room.Room
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
    @Singleton // This ensures only ONE instance exists for the whole app
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "streamsphere_db"
        )
        // DO NOT add enableMultiInstanceInvalidation()
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideFavouritesDao(db: AppDatabase): FavouritesDao {
        return db.favouritesDao()
    }
}