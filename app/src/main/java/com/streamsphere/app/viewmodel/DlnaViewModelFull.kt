package com.streamsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamsphere.app.data.cast.CastRepository
import com.streamsphere.app.data.cast.ChromecastState
import com.streamsphere.app.data.dlna.DlnaBrowserRepository
import com.streamsphere.app.data.dlna.DlnaBrowseItem
import com.streamsphere.app.data.dlna.DlnaDevice
import com.streamsphere.app.data.dlna.DlnaDeviceType
import com.streamsphere.app.data.dlna.DlnaRendererController
import com.streamsphere.app.data.dlna.DlnaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    private val dlnaRepository: DlnaRepository,
    private val castRepository: CastRepository
) : ViewModel() {

    // ── DLNA state ─────────────────────────────────────────────────────────

    val isBound: StateFlow<Boolean> = dlnaRepository.isBound

    val renderers: StateFlow<List<DlnaDevice>> = dlnaRepository.devices
        .map { list -> list.filter { d -> d.type == DlnaDeviceType.RENDERER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mediaServers: StateFlow<List<DlnaDevice>> = dlnaRepository.devices
        .map { list -> list.filter { d -> d.type == DlnaDeviceType.MEDIA_SERVER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _castState = MutableStateFlow(DlnaCastState())
    val castState: StateFlow<DlnaCastState> = _castState.asStateFlow()

    // ── Chromecast state ───────────────────────────────────────────────────

    /** Exposed directly from CastRepository. */
    val chromecastState: StateFlow<ChromecastState> = castRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChromecastState())

    // ── Browse ─────────────────────────────────────────────────────────────

    private val _browseItems = MutableStateFlow<List<DlnaBrowseItem>>(emptyList())
    val browseItems: StateFlow<List<DlnaBrowseItem>> = _browseItems.asStateFlow()

    private val _isBrowseLoading = MutableStateFlow(false)
    val isBrowseLoading: StateFlow<Boolean> = _isBrowseLoading.asStateFlow()

    // ── DLNA cast ──────────────────────────────────────────────────────────

    fun castToRenderer(device: DlnaDevice, streamUrl: String, channelName: String) {
        val cp = dlnaRepository.getControlPoint() ?: run {
            _castState.value = _castState.value.copy(
                errorMessage = "UPnP service not ready — please wait a moment and try again."
            )
            return
        }
        val remoteDevice = dlnaRepository.getRemoteDevice(device.udn) ?: run {
            _castState.value = _castState.value.copy(
                errorMessage = "Device no longer available on the network."
            )
            return
        }

        _castState.value = DlnaCastState(isCasting = true, castingToDevice = device)

        viewModelScope.launch(Dispatchers.IO) {
            DlnaRendererController(cp, remoteDevice).castStream(
                streamUrl = streamUrl,
                title     = channelName,
                onSuccess = {
                    viewModelScope.launch(Dispatchers.Main) {
                        _castState.value = _castState.value.copy(errorMessage = null)
                    }
                },
                onFailure = { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _castState.value = DlnaCastState(
                            errorMessage = msg ?: "Cast failed — the device may not support this stream format."
                        )
                    }
                }
            )
        }
    }

    fun stopDlnaCast() {
        val castingDevice = _castState.value.castingToDevice ?: return
        val cp            = dlnaRepository.getControlPoint() ?: run {
            _castState.value = DlnaCastState()
            return
        }
        val remoteDevice  = dlnaRepository.getRemoteDevice(castingDevice.udn) ?: run {
            _castState.value = DlnaCastState()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            DlnaRendererController(cp, remoteDevice).stop(
                onSuccess = {
                    viewModelScope.launch(Dispatchers.Main) { _castState.value = DlnaCastState() }
                },
                onFailure = {
                    viewModelScope.launch(Dispatchers.Main) { _castState.value = DlnaCastState() }
                }
            )
        }
    }

    // Keep old name for backwards compat with existing call sites.
    fun stopCast() = stopDlnaCast()

    // ── Chromecast cast ────────────────────────────────────────────────────

    /**
     * Load [streamUrl] on the currently connected Chromecast session.
     * The Cast SDK must already have a session (user connected via MediaRoute dialog).
     */
    fun castToChromecast(streamUrl: String, channelName: String, logoUrl: String? = null) {
        val ok = castRepository.loadMedia(streamUrl, channelName, logoUrl)
        if (!ok) {
            // Surface error through DLNA error state so the existing snackbar shows it.
            _castState.value = _castState.value.copy(
                errorMessage = "No Chromecast session — tap the Cast icon to connect first."
            )
        }
    }

    fun stopChromecast() = castRepository.stopCast()

    // ── Volume ─────────────────────────────────────────────────────────────

    fun setVolume(device: DlnaDevice, volume: Int) {
        val cp           = dlnaRepository.getControlPoint() ?: return
        val remoteDevice = dlnaRepository.getRemoteDevice(device.udn) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            DlnaRendererController(cp, remoteDevice).setVolume(volume)
        }
    }

    // ── Media Server Browsing ──────────────────────────────────────────────

    fun browseServer(device: DlnaDevice, containerId: String = "0") {
        val cp           = dlnaRepository.getControlPoint() ?: return
        val remoteDevice = dlnaRepository.getRemoteDevice(device.udn) ?: return

        _isBrowseLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            DlnaBrowserRepository(cp).browse(
                device      = remoteDevice,
                containerId = containerId,
                onResult    = { items ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _browseItems.value     = items
                        _isBrowseLoading.value = false
                    }
                },
                onError     = { msg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _browseItems.value     = emptyList()
                        _isBrowseLoading.value = false
                        _castState.value       = _castState.value.copy(errorMessage = msg)
                    }
                }
            )
        }
    }

    // ── Discovery & Lifecycle ──────────────────────────────────────────────

    fun bind()    = dlnaRepository.bind()
    fun unbind()  = dlnaRepository.unbind()
    fun refresh() = dlnaRepository.search()

    fun clearError() {
        _castState.value = _castState.value.copy(errorMessage = null)
        castRepository.clearError()
    }
}
