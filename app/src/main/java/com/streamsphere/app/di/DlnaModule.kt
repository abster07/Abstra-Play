package com.streamsphere.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DlnaRepository is annotated with @Singleton and has an @Inject constructor
 * that receives @ApplicationContext.  Hilt resolves it automatically — no
 * @Provides method is needed.  This module is intentionally empty but must be
 * kept so the package compiles; you can add other DLNA-related bindings here
 * in the future (e.g. a @Binds for an interface).
 */
@Module
@InstallIn(SingletonComponent::class)
object DlnaModule
