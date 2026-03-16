package com.streamsphere.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamsphere.app.data.dlna.DlnaBrowseItem
import com.streamsphere.app.data.dlna.DlnaDevice
import com.streamsphere.app.data.dlna.DlnaDeviceType
import com.streamsphere.app.viewmodel.DlnaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DlnaScreen(
    viewModel: DlnaViewModel = hiltViewModel()
) {
    val renderers by viewModel.renderers.collectAsState()
    val mediaServers by viewModel.mediaServers.collectAsState()
    val browseItems by viewModel.browseItems.collectAsState()
    val isBrowseLoading by viewModel.isBrowseLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DLNA / UPnP") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan network")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Renderers ────────────────────────────────────────────────
            item {
                SectionHeader(icon = { Icon(Icons.Default.Tv, null) }, title = "Renderers")
            }

            if (renderers.isEmpty()) {
                item {
                    EmptyHint("No renderers found. Tap refresh to scan.")
                }
            } else {
                items(renderers, key = { it.udn }) { device ->
                    DlnaDeviceCard(device = device)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── Media Servers ─────────────────────────────────────────────
            item {
                SectionHeader(icon = { Icon(Icons.Default.Storage, null) }, title = "Media Servers")
            }

            if (mediaServers.isEmpty()) {
                item {
                    EmptyHint("No media servers found.")
                }
            } else {
                items(mediaServers, key = { it.udn }) { device ->
                    DlnaDeviceCard(
                        device = device,
                        onBrowse = { viewModel.browseServer(device) }
                    )
                }
            }

            // ── Browse Results ────────────────────────────────────────────
            if (isBrowseLoading) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            } else if (browseItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Browse Results", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                items(browseItems) { item ->
                    BrowseItemRow(item)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: @Composable () -> Unit, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DlnaDeviceCard(
    device: DlnaDevice,
    onBrowse: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(device.friendlyName, style = MaterialTheme.typography.bodyLarge)
            device.modelName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            device.manufacturer?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onBrowse != null) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onBrowse) {
                    Text("Browse content")
                }
            }
        }
    }
}

@Composable
private fun BrowseItemRow(item: DlnaBrowseItem) {
    when (item) {
        is DlnaBrowseItem.Container -> ListItem(
            headlineContent = { Text(item.title) },
            supportingContent = { Text("${item.childCount} items") },
            leadingContent = { Icon(Icons.Default.Folder, null) }
        )
        is DlnaBrowseItem.Track -> ListItem(
            headlineContent = { Text(item.title) },
            supportingContent = { Text(item.mimeType) },
            leadingContent = { Icon(Icons.Default.MusicNote, null) }
        )
    }
}

@Composable
private fun EmptyHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}
