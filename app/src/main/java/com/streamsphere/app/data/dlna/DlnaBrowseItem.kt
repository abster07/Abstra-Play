package com.streamsphere.app.data.dlna

enum class DlnaBrowseItemType { CONTAINER, VIDEO, AUDIO, IMAGE, OTHER }

data class DlnaBrowseItem(
    val id: String,
    val parentId: String,
    val title: String,
    val type: DlnaBrowseItemType,
    val url: String? = null,
    val mimeType: String? = null,
    val childCount: Int? = null
)
