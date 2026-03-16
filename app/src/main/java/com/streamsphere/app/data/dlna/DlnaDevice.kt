package com.streamsphere.app.data.dlna

import org.jupnp.model.meta.RemoteDevice

data class DlnaDevice(
    val udn: String,
    val friendlyName: String,
    val type: DlnaDeviceType,
    val modelName: String?,
    val manufacturer: String?,
    val remoteDevice: RemoteDevice
)

enum class DlnaDeviceType {
    RENDERER,   // TV, speakers, etc.
    MEDIA_SERVER,
    UNKNOWN
}

fun RemoteDevice.toDlnaDevice(): DlnaDevice {
    val deviceType = when (type?.type) {
        "MediaRenderer" -> DlnaDeviceType.RENDERER
        "MediaServer" -> DlnaDeviceType.MEDIA_SERVER
        else -> DlnaDeviceType.UNKNOWN
    }
    return DlnaDevice(
        udn = identity.udn.identifierString,
        friendlyName = details?.friendlyName ?: "Unknown Device",
        type = deviceType,
        modelName = details?.modelDetails?.modelName,
        manufacturer = details?.manufacturerDetails?.manufacturer,
        remoteDevice = this
    )
}
