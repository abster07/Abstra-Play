package com.streamsphere.app.data.dlna

import android.util.Log
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.controlpoint.ControlPoint
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.Service
import org.jupnp.model.types.UDAServiceType

class DlnaRendererController(
    private val controlPoint: ControlPoint,
    private val device: RemoteDevice
) {
    companion object {
        private const val TAG = "DlnaRendererController"
        private val AV_TRANSPORT  = UDAServiceType("AVTransport", 1)
        private val RENDERING_CTL = UDAServiceType("RenderingControl", 1)
    }

    // ── Cast (SetAVTransportURI → Play) ────────────────────────────────────

    fun castStream(
        streamUrl: String,
        title: String,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit
    ) {
        val service = device.findService(AV_TRANSPORT) ?: run {
            onFailure("AVTransport service not found on device")
            return
        }

        val didlMetadata = buildDidlMetadata(title, streamUrl)
        setUri(service, streamUrl, didlMetadata,
            onSuccess = { play(service, onSuccess, onFailure) },
            onFailure  = onFailure
        )
    }

    private fun setUri(
        service: Service<*, *>,
        uri: String,
        metadata: String,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val action = (service as Service<*, *>).getAction("SetAVTransportURI")
            ?: run { onFailure("SetAVTransportURI action not found"); return }

        @Suppress("UNCHECKED_CAST")
        val invocation = ActionInvocation(action as org.jupnp.model.meta.Action<Service<*, *>>).apply {
            setInput("InstanceID", "0")
            setInput("CurrentURI", uri)
            setInput("CurrentURIMetaData", metadata)
        }

        controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) {
                Log.d(TAG, "SetAVTransportURI success")
                onSuccess()
            }
            override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) {
                Log.e(TAG, "SetAVTransportURI failed: $defaultMsg")
                onFailure(defaultMsg)
            }
        })
    }

    private fun play(
        service: Service<*, *>,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val action = service.getAction("Play")
            ?: run { onFailure("Play action not found"); return }

        @Suppress("UNCHECKED_CAST")
        val invocation = ActionInvocation(action as org.jupnp.model.meta.Action<Service<*, *>>).apply {
            setInput("InstanceID", "0")
            setInput("Speed", "1")
        }

        controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) {
                Log.d(TAG, "Play success")
                onSuccess()
            }
            override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) {
                Log.e(TAG, "Play failed: $defaultMsg")
                onFailure(defaultMsg)
            }
        })
    }

    // ── Stop ───────────────────────────────────────────────────────────────

    fun stop(onSuccess: () -> Unit, onFailure: (String?) -> Unit) {
        val service = device.findService(AV_TRANSPORT) ?: run {
            onFailure("AVTransport service not found")
            return
        }

        @Suppress("UNCHECKED_CAST")
        val action = service.getAction("Stop")
            ?: run { onFailure("Stop action not found"); return }

        @Suppress("UNCHECKED_CAST")
        val invocation = ActionInvocation(action as org.jupnp.model.meta.Action<Service<*, *>>).apply {
            setInput("InstanceID", "0")
        }

        controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) {
                Log.d(TAG, "Stop success")
                onSuccess()
            }
            override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) {
                Log.e(TAG, "Stop failed: $defaultMsg")
                onFailure(defaultMsg)
            }
        })
    }

    // ── Volume ─────────────────────────────────────────────────────────────

    fun setVolume(volume: Int) {
        val service = device.findService(RENDERING_CTL) ?: run {
            Log.w(TAG, "RenderingControl service not found")
            return
        }

        @Suppress("UNCHECKED_CAST")
        val action = service.getAction("SetVolume") ?: return

        @Suppress("UNCHECKED_CAST")
        val invocation = ActionInvocation(action as org.jupnp.model.meta.Action<Service<*, *>>).apply {
            setInput("InstanceID", "0")
            setInput("Channel", "Master")
            setInput("DesiredVolume", volume.coerceIn(0, 100).toString())
        }

        controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) = Log.d(TAG, "SetVolume success")
            override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) =
                Log.e(TAG, "SetVolume failed: $defaultMsg")
        })
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun buildDidlMetadata(title: String, uri: String): String {
        val escapedTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val escapedUri   = uri.replace("&", "&amp;")
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
               """xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
               """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
               """<item id="0" parentID="-1" restricted="1">""" +
               """<dc:title>$escapedTitle</dc:title>""" +
               """<upnp:class>object.item.videoItem</upnp:class>""" +
               """<res>$escapedUri</res>""" +
               """</item></DIDL-Lite>"""
    }
}
