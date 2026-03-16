package com.streamsphere.app.data.dlna

import org.jupnp.model.meta.RemoteDevice

enum class DlnaDeviceType { RENDERER, MEDIA_SERVER, OTHER }

data class DlnaDevice(
    val udn: String,
    val friendlyName: String,
    val manufacturer: String?,
    val modelName: String?,
    val type: DlnaDeviceType
)

fun RemoteDevice.toDlnaDevice(): DlnaDevice {
    val typeString = type?.type?.toString().orEmpty()
    val deviceType = when {
        typeString.contains("MediaRenderer", ignoreCase = true) -> DlnaDeviceType.RENDERER
        typeString.contains("MediaServer", ignoreCase = true)   -> DlnaDeviceType.MEDIA_SERVER
        else -> DlnaDeviceType.OTHER
    }
    return DlnaDevice(
        udn          = identity.udn.identifierString,
        friendlyName = details?.friendlyName ?: identity.udn.identifierString,
        manufacturer = details?.manufacturerDetails?.manufacturer,
        modelName    = details?.modelDetails?.modelName,
        type         = deviceType
    )
}
