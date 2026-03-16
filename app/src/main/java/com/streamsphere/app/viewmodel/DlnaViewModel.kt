package com.streamsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamsphere.app.data.dlna.DlnaBrowserRepository
import com.streamsphere.app.data.dlna.DlnaBrowseItem
import com.streamsphere.app.data.dlna.DlnaDevice
import com.streamsphere.app.data.dlna.DlnaDeviceType
import com.streamsphere.app.data.dlna.DlnaRendererController
import com.streamsphere.app.data.dlna.DlnaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DlnaCastState(
    val isCasting: Boolean = false,
    val castingToDevice: DlnaDevice? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DlnaViewModel @Inject constructor(
    private val dlnaRepository: DlnaRepository
) : ViewModel() {

    val isBound: StateFlow<Boolean> = dlnaRepository.isBound

    val renderers: StateFlow<List<DlnaDevice>> = dlnaRepository.devices
        .map { it.filter { d -> d.type == DlnaDeviceType.RENDERER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaServers: StateFlow<List<DlnaDevice>> = dlnaRepository.devices
        .map { it.filter { d -> d.type == DlnaDeviceType.MEDIA_SERVER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _castState = MutableStateFlow(DlnaCastState())
    val castState: StateFlow<DlnaCastState> = _castState.asStateFlow()

    private val _browseItems = MutableStateFlow<List<DlnaBrowseItem>>(emptyList())
    val browseItems: StateFlow<List<DlnaBrowseItem>> = _browseItems.asStateFlow()

    private val _isBrowseLoading = MutableStateFlow(false)
    val isBrowseLoading: StateFlow<Boolean> = _isBrowseLoading.asStateFlow()

    // ── Casting ────────────────────────────────────────────────────────────

    /**
     * Cast a channel's stream URL to the selected DLNA renderer.
     * Call this from DetailScreen when user picks a renderer.
     */
    fun castToRenderer(device: DlnaDevice, streamUrl: String, channelName: String) {
        val cp = dlnaRepository.getControlPoint() ?: run {
            _castState.value = _castState.value.copy(errorMessage = "UPnP service not ready")
            return
        }
        val remoteDevice = dlnaRepository.getRemoteDevice(device.udn) ?: run {
            _castState.value = _castState.value.copy(errorMessage = "Device no longer available")
            return
        }

        _castState.value = DlnaCastState(isCasting = true, castingToDevice = device)

        DlnaRendererController(cp, remoteDevice).castStream(
            streamUrl = streamUrl,
            title = channelName,
            onSuccess = {
                _castState.value = _castState.value.copy(errorMessage = null)
            },
            onFailure = { msg ->
                _castState.value = DlnaCastState(errorMessage = msg ?: "Cast failed")
            }
        )
    }

    fun stopCast() {
        val castingDevice = _castState.value.castingToDevice ?: return
        val cp = dlnaRepository.getControlPoint() ?: return
        val remoteDevice = dlnaRepository.getRemoteDevice(castingDevice.udn) ?: return

        DlnaRendererController(cp, remoteDevice).stop(
            onSuccess = { _castState.value = DlnaCastState() }
        )
    }

    fun setVolume(device: DlnaDevice, volume: Int) {
        val cp = dlnaRepository.getControlPoint() ?: return
        val remoteDevice = dlnaRepository.getRemoteDevice(device.udn) ?: return
        DlnaRendererController(cp, remoteDevice).setVolume(volume)
    }

    // ── Media Server Browsing ──────────────────────────────────────────────

    fun browseServer(device: DlnaDevice, containerId: String = "0") {
        val cp = dlnaRepository.getControlPoint() ?: return
        val remoteDevice = dlnaRepository.getRemoteDevice(device.udn) ?: return

        _isBrowseLoading.value = true
        DlnaBrowserRepository(cp).browse(
            device = remoteDevice,
            containerId = containerId,
            onResult = { items ->
                _browseItems.value = items
                _isBrowseLoading.value = false
            },
            onError = { msg ->
                _browseItems.value = emptyList()
                _isBrowseLoading.value = false
                _castState.value = _castState.value.copy(errorMessage = msg)
            }
        )
    }

    // ── Discovery & Lifecycle ──────────────────────────────────────────────

    /** Call from a DisposableEffect in the screen that owns the cast session. */
    fun bind() = dlnaRepository.bind()

    /** Call from the onDispose of the same DisposableEffect. */
    fun unbind() = dlnaRepository.unbind()

    fun refresh() = dlnaRepository.search()

    fun clearError() {
        _castState.value = _castState.value.copy(errorMessage = null)
    }
}
