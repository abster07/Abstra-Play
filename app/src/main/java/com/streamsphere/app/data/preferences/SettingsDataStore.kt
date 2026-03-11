package com.streamsphere.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        val DARK_MODE       = booleanPreferencesKey("dark_mode")
        val COMPACT_VIEW    = booleanPreferencesKey("compact_view")
        val NOTIFICATIONS   = booleanPreferencesKey("notifications")
    }

    val darkMode:     Flow<Boolean> = context.settingsDataStore.data.map { it[DARK_MODE]     ?: true  }
    val compactView:  Flow<Boolean> = context.settingsDataStore.data.map { it[COMPACT_VIEW]  ?: false }
    val notifications:Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIFICATIONS] ?: false }

    suspend fun setDarkMode(v: Boolean)      { context.settingsDataStore.edit { it[DARK_MODE]     = v } }
    suspend fun setCompactView(v: Boolean)   { context.settingsDataStore.edit { it[COMPACT_VIEW]  = v } }
    suspend fun setNotifications(v: Boolean) { context.settingsDataStore.edit { it[NOTIFICATIONS] = v } }
}