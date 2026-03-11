package com.streamsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsDataStore
) : ViewModel() {

    val darkMode      = store.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val compactView   = store.compactView.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifications = store.notifications.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDarkMode(v: Boolean)      = viewModelScope.launch { store.setDarkMode(v) }
    fun setCompactView(v: Boolean)   = viewModelScope.launch { store.setCompactView(v) }
    fun setNotifications(v: Boolean) = viewModelScope.launch { store.setNotifications(v) }
}