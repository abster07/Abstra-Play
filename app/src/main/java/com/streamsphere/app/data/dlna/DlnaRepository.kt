package com.streamsphere.app.data.dlna

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jupnp.android.AndroidUpnpService
import org.jupnp.android.AndroidUpnpServiceImpl
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlnaRepository @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "DlnaRepository"
    }

    private var upnpService: AndroidUpnpService? = null

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound.asStateFlow()

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "UPnP service connected")
            upnpService = (service as AndroidUpnpService.LocalBinder).service
            upnpService?.registry?.addListener(registryListener)
            upnpService?.controlPoint?.search()
            _isBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "UPnP service disconnected")
            upnpService = null
            _isBound.value = false
        }
    }

    fun bind() {
        context.bindService(
            Intent(context, AndroidUpnpServiceImpl::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbind() {
        upnpService?.registry?.removeListener(registryListener)
        try {
            context.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Service was not bound: ${e.message}")
        }
        upnpService = null
        _isBound.value = false
    }

    fun search() {
        upnpService?.controlPoint?.search()
    }

    fun getControlPoint() = upnpService?.controlPoint

    fun getRemoteDevice(udn: String): RemoteDevice? {
        return upnpService?.registry?.devices
            ?.filterIsInstance<RemoteDevice>()
            ?.firstOrNull { it.identity.udn.identifierString == udn }
    }

    private fun refreshDevices(registry: Registry) {
        _devices.value = registry.devices
            .filterIsInstance<RemoteDevice>()
            .mapNotNull { device ->
                runCatching { device.toDlnaDevice() }.getOrNull()
            }
    }
}
