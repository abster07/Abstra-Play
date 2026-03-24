package com.streamsphere.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streamsphere.app.data.cast.ChromecastState
import com.streamsphere.app.data.dlna.DlnaDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePickerSheet(
    // DLNA
    dlnaRenderers: List<DlnaDevice>,
    dlnaIsBound: Boolean,
    onDlnaDeviceSelected: (DlnaDevice) -> Unit,
    onDlnaRefresh: () -> Unit,
    // Chromecast
    chromecastState: ChromecastState,
    onChromeCastSelected: () -> Unit,   // triggers system MediaRoute dialog
    onStopChromecast: () -> Unit,
    // Sheet
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {

            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Cast to device", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDlnaRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan for devices")
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Chromecast section ─────────────────────────────────────────
            Text(
                "Chromecast",
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            if (chromecastState.isCasting) {
                // Currently casting — show stop option.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStopChromecast(); onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Casting to ${chromecastState.deviceName ?: "Chromecast"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Tap to stop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Show the system "Cast to…" entry that opens MediaRouter dialog.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChromeCastSelected(); onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("Cast via Chromecast / Google Cast…", style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── DLNA / UPnP section ────────────────────────────────────────
            Text(
                "DLNA / UPnP",
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            when {
                !dlnaIsBound -> {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Connecting to UPnP service…")
                    }
                }

                dlnaRenderers.isEmpty() -> {
                    Text(
                        text = "No DLNA renderers found on your network.\nMake sure your TV or device is on the same Wi-Fi.",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }

                else -> {
                    LazyColumn {
                        items(dlnaRenderers, key = { it.udn }) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDlnaDeviceSelected(device); onDismiss() }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(device.friendlyName, style = MaterialTheme.typography.bodyLarge)
                                    device.modelName?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
