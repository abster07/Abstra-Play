package com.streamsphere.app.data.dlna

import android.util.Log
import org.jupnp.controlpoint.ActionCallback
import org.jupnp.controlpoint.ControlPoint
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.Service
import org.jupnp.model.types.UDAServiceType
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class DlnaBrowserRepository(
    private val controlPoint: ControlPoint
) {
    companion object {
        private const val TAG = "DlnaBrowserRepository"
        private val CONTENT_DIR = UDAServiceType("ContentDirectory", 1)
    }

    fun browse(
        device: RemoteDevice,
        containerId: String = "0",
        onResult: (List<DlnaBrowseItem>) -> Unit,
        onError: (String?) -> Unit
    ) {
        val service = device.findService(CONTENT_DIR) ?: run {
            onError("ContentDirectory service not found on device")
            return
        }

        @Suppress("UNCHECKED_CAST")
        val action = service.getAction("Browse")
            ?: run { onError("Browse action not found"); return }

        @Suppress("UNCHECKED_CAST")
        val invocation = ActionInvocation(action as org.jupnp.model.meta.Action<Service<*, *>>).apply {
            setInput("ObjectID",       containerId)
            setInput("BrowseFlag",     "BrowseDirectChildren")
            setInput("Filter",         "*")
            setInput("StartingIndex",  "0")
            setInput("RequestedCount", "200")
            setInput("SortCriteria",   "")
        }

        controlPoint.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>) {
                val didlXml = invocation.getOutput("Result")?.value?.toString()
                if (didlXml.isNullOrBlank()) {
                    onResult(emptyList())
                    return
                }
                try {
                    onResult(parseDidl(didlXml))
                } catch (e: Exception) {
                    Log.e(TAG, "DIDL parse error", e)
                    onError("Failed to parse browse results: ${e.message}")
                }
            }

            override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) {
                Log.e(TAG, "Browse failed: $defaultMsg")
                onError(defaultMsg)
            }
        })
    }

    // ── DIDL-Lite XML parser ───────────────────────────────────────────────

    private fun parseDidl(xml: String): List<DlnaBrowseItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

        val items = mutableListOf<DlnaBrowseItem>()

        // containers
        val containers = doc.getElementsByTagNameNS("*", "container")
        for (i in 0 until containers.length) {
            val el = containers.item(i) as org.w3c.dom.Element
            items += DlnaBrowseItem(
                id         = el.getAttribute("id"),
                parentId   = el.getAttribute("parentID"),
                title      = el.getElementsByTagNameNS("*", "title").item(0)?.textContent ?: "Unknown",
                type       = DlnaBrowseItemType.CONTAINER,
                childCount = el.getAttribute("childCount").toIntOrNull()
            )
        }

        // items
        val elements = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until elements.length) {
            val el = elements.item(i) as org.w3c.dom.Element
            val upnpClass = el.getElementsByTagNameNS("*", "class").item(0)?.textContent.orEmpty()
            val resNode   = el.getElementsByTagNameNS("*", "res").item(0)
            val url       = resNode?.textContent?.trim()
            val mimeType  = (resNode as? org.w3c.dom.Element)?.getAttribute("protocolInfo")
                ?.split(":")?.getOrNull(2)

            val type = when {
                upnpClass.contains("videoItem",    ignoreCase = true) -> DlnaBrowseItemType.VIDEO
                upnpClass.contains("audioItem",    ignoreCase = true) -> DlnaBrowseItemType.AUDIO
                upnpClass.contains("imageItem",    ignoreCase = true) -> DlnaBrowseItemType.IMAGE
                else -> DlnaBrowseItemType.OTHER
            }

            items += DlnaBrowseItem(
                id       = el.getAttribute("id"),
                parentId = el.getAttribute("parentID"),
                title    = el.getElementsByTagNameNS("*", "title").item(0)?.textContent ?: "Unknown",
                type     = type,
                url      = url,
                mimeType = mimeType
            )
        }

        return items
    }
}
