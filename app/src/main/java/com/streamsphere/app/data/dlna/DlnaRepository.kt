package com.streamsphere.app.data.dlna

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jupnp.android.AndroidUpnpService
import org.jupnp.android.AndroidUpnpServiceImpl
import org.jupnp.controlpoint.ControlPoint
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class DlnaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DlnaRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // The bound AndroidUpnpService instance — null until ServiceConnection fires.
    private var upnpService: AndroidUpnpService? = null

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound.asStateFlow()

    // ── Registry listener ──────────────────────────────────────────────────

    private val registryListener = object : DefaultRegistryListener() {
        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
            Log.d(TAG, "Device added: ${device.details?.friendlyName}")
            refreshDevices(registry)
        }

        override fun remoteDeviceUpdated(registry: Registry, device: RemoteDevice) {
            refreshDevices(registry)
        }

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            Log.d(TAG, "Device removed: ${device.details?.friendlyName}")
            refreshDevices(registry)
        }
    }

    // ── ServiceConnection — the correct Android UPnP binding pattern ───────

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as AndroidUpnpService)
            upnpService = svc

            // Re-add any existing registry devices that arrived before we connected.
            refreshDevices(svc.registry)

            svc.registry.addListener(registryListener)

            // Trigger a fresh search so discoverable devices show up quickly.
            scope.launch(Dispatchers.IO) {
                try {
                    svc.controlPoint.search()
                } catch (e: Exception) {
                    Log.e(TAG, "Search failed", e)
                }
            }

            _isBound.value = true
            Log.d(TAG, "UPnP service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            upnpService = null
            _isBound.value = false
            _devices.value = emptyList()
            Log.w(TAG, "UPnP service disconnected unexpectedly")
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    fun bind() {
        if (_isBound.value) return
        delay(500)
        context.bindService(
            Intent(context, AndroidUpnpServiceImpl::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        Log.d(TAG, "Binding to UPnP service")
    }

    fun unbind() {
        if (!_isBound.value && upnpService == null) return
        upnpService?.registry?.removeListener(registryListener)
        try {
            context.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Service was not bound: ${e.message}")
        }
        upnpService = null
        _isBound.value = false
        _devices.value = emptyList()
        Log.d(TAG, "UPnP service unbound")
    }

    fun search() {
        scope.launch(Dispatchers.IO) {
            try {
                upnpService?.controlPoint?.search()
                    ?: Log.w(TAG, "Cannot search — service not bound yet")
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
            }
        }
    }

    fun getControlPoint(): ControlPoint? = upnpService?.controlPoint

    fun getRemoteDevice(udn: String): RemoteDevice? =
        upnpService?.registry?.devices
            ?.filterIsInstance<RemoteDevice>()
            ?.firstOrNull { it.identity.udn.identifierString == udn }

    // ── Internal ───────────────────────────────────────────────────────────

    private fun refreshDevices(registry: Registry) {
        val list = registry.devices
            .filterIsInstance<RemoteDevice>()
            .mapNotNull { device -> runCatching { device.toDlnaDevice() }.getOrNull() }
        // StateFlow updates must be on main thread.
        scope.launch(Dispatchers.Main) {
            _devices.value = list
        }
    }
}
