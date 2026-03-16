package com.streamsphere.app.di

import android.content.Context
import com.streamsphere.app.data.dlna.DlnaRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DlnaModule {

    @Provides
    @Singleton
    fun provideDlnaRepository(
        @ApplicationContext context: Context
    ): DlnaRepository = DlnaRepository(context)
}
