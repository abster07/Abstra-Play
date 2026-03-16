package com.streamsphere.app.data.dlna

sealed class DlnaBrowseItem {
    data class Container(
        val id: String,
        val title: String,
        val childCount: Int = 0
    ) : DlnaBrowseItem()

    data class Track(
        val id: String,
        val title: String,
        val uri: String,
        val mimeType: String,
        val duration: String? = null,
        val albumArtUri: String? = null
    ) : DlnaBrowseItem()
}
