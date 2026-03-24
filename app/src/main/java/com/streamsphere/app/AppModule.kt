package com.streamsphere.app

import android.content.Context
import androidx.room.Room
import com.streamsphere.app.data.api.AppDatabase
import com.streamsphere.app.data.api.IptvApi
import com.streamsphere.app.data.preferences.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val BASE_URL = "https://iptv-org.github.io/api/"

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideApi(retrofit: Retrofit): IptvApi = retrofit.create(IptvApi::class.java)

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext ctx: Context): SettingsDataStore =
        SettingsDataStore(ctx)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "streamsphere.db")
            .fallbackToDestructiveMigration()
            .build()

    // CastRepository and DlnaRepository both have @Inject constructors with
    // @ApplicationContext — Hilt resolves them automatically, no @Provides needed.
}
