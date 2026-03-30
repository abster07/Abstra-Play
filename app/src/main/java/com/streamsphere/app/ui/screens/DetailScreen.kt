package com.streamsphere.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.streamsphere.app.data.model.ChannelUiModel
import com.streamsphere.app.data.model.StreamOption
import com.streamsphere.app.ui.components.*
import com.streamsphere.app.ui.theme.*
import com.streamsphere.app.viewmodel.ChannelViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt


// ─────────────────────────────────────────────────────────────────────────────
// Audio track data class
// ─────────────────────────────────────────────────────────────────────────────

data class AudioTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,        // BCP-47 / ISO 639 tag from the stream
    val label: String,            // human-readable display name
    val channelCount: Int,
    val sampleRate: Int,
    val isSelected: Boolean
)

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    channelId: String,
    autoPlay: Boolean = false,
    startInFullscreen: Boolean = false,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val channels by viewModel.filteredChannels.collectAsState()
    val channel  = remember(channels, channelId) { channels.find { it.id == channelId } }

    var isFullscreen by remember { mutableStateOf(startInFullscreen) }

    if (isFullscreen) {
        channel?.let { ch ->
            FullscreenPlayer(
                channel          = ch,
                onExitFullscreen = { isFullscreen = false },
                onFavourite      = { viewModel.toggleFavourite(ch) },
                onWidget         = { viewModel.toggleWidget(ch) },
                onSelectStream   = { idx -> viewModel.selectStream(ch.id, idx) }
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(channel?.name ?: "Channel") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        channel?.let { ch ->
                            IconButton(onClick = { viewModel.toggleFavourite(ch) }) {
                                Icon(
                                    imageVector        = if (ch.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favourite",
                                    tint               = if (ch.isFavourite) MaterialTheme.colorScheme.error
                                                         else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            if (channel == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DetailContent(
                    channel           = channel,
                    autoPlay          = autoPlay,
                    onWidget          = { viewModel.toggleWidget(channel) },
                    onEnterFullscreen = { isFullscreen = true },
                    onSelectStream    = { idx -> viewModel.selectStream(channel.id, idx) },
                    modifier          = Modifier.padding(padding)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Portrait inline player + channel info
// ─────────────────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DetailContent(
    channel: ChannelUiModel,
    autoPlay: Boolean,
    onWidget: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onSelectStream: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val catColor  = categoryColorFor(channel)

    var isPlaying    by remember { mutableStateOf(autoPlay) }
    var playerError  by remember { mutableStateOf<String?>(null) }
    var audioTracks  by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }

    val exoPlayer = rememberExoPlayer(context, channel.streamUrl, autoPlay) { err ->
        playerError = err; isPlaying = false
    }

    // Listen for track changes to populate audio track list
    LaunchedEffect(exoPlayer) {
        exoPlayer ?: return@LaunchedEffect
        exoPlayer.volume = 1f
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = extractAudioTracks(tracks)
                // If somehow no track is selected, force the first one
                if (audioTracks.isNotEmpty() && audioTracks.none { it.isSelected }) {
                    selectAudioTrack(exoPlayer, audioTracks.first())
                }
            }
        })
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (isPlaying) exoPlayer?.play()
                Lifecycle.Event.ON_PAUSE  -> exoPlayer?.pause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    var showFeedPicker  by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // ── 16:9 player ──────────────────────────────────────────────────────
        Box(
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .background(Color.Black),
    contentAlignment = Alignment.Center
) {
    // 1. Video surface (bottom layer)
    if (exoPlayer != null) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player        = exoPlayer
                    useController = false
                    resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams  = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    // 2. Thumbnail overlay — only when truly not playing AND no error
    if (exoPlayer != null && !isPlaying && playerError == null) {
        ThumbnailOverlay(channel, catColor) {
            playerError = null
            exoPlayer.playWhenReady = true
        }
    }

    // 3. Error overlay — highest priority visual layer
    if (exoPlayer != null && playerError != null) {
        ErrorOverlay(playerError!!) {
            playerError = null
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // 4. Controls — always rendered on top, shown when playing OR tapped
    if (exoPlayer != null && playerError == null) {
        var controlsVisible by remember { mutableStateOf(true) }
        var lastInteraction by remember { mutableStateOf(0L) }

        // Auto-hide controls after 3 seconds when playing
        LaunchedEffect(controlsVisible, isPlaying) {
            if (controlsVisible && isPlaying) {
                delay(3000)
                // Only hide if no new interaction since we started waiting
                val now = System.currentTimeMillis()
                if (now - lastInteraction >= 2900) {
                    controlsVisible = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        lastInteraction = System.currentTimeMillis()
                        controlsVisible = !controlsVisible
                    }
                }
        ) {
            // Fullscreen button — top-right, shown when controls visible
            if (controlsVisible) {
                IconButton(
                    onClick  = onEnterFullscreen,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Fullscreen, "Fullscreen",
                        tint     = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Play/pause button — center, shown when controls visible
            if (controlsVisible) {
                FilledIconButton(
                    onClick  = {
                        lastInteraction = System.currentTimeMillis()
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = catColor.copy(alpha = 0.85f)
                    )
                ) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier           = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

        // ── Selector bars ────────────────────────────────────────────────────
        if (channel.hasMultipleFeeds) {
            FeedSelectorBar(channel = channel, catColor = catColor, onOpenPicker = { showFeedPicker = true })
        }
        if (audioTracks.size > 1) {
            AudioSelectorBar(
                audioTracks  = audioTracks,
                catColor     = catColor,
                onOpenPicker = { showAudioPicker = true }
            )
        }

        // ── Channel info ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(channel.name, style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(channel.countryFlag, style = MaterialTheme.typography.titleLarge)
                Text(channel.country, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LiveBadge()
            }
            if (channel.categories.isNotEmpty()) {
                Text("Categories", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    channel.categories.forEach { cat -> CategoryChip(cat, catColor) }
                }
            }
            OutlinedButton(
                onClick  = onWidget,
                modifier = Modifier.fillMaxWidth(),
                border   = BorderStroke(1.dp, if (channel.isWidget) catColor else MaterialTheme.colorScheme.outline.copy(0.5f)),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (channel.isWidget) catColor else MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Icon(if (channel.isWidget) Icons.Filled.Widgets else Icons.Outlined.Widgets, null)
                Spacer(Modifier.width(8.dp))
                Text(if (channel.isWidget) "Remove from Widget" else "Add to Home Screen Widget")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showFeedPicker) {
        FeedPickerSheet(channel = channel, catColor = catColor,
            onSelect  = { idx -> onSelectStream(idx); showFeedPicker = false },
            onDismiss = { showFeedPicker = false })
    }
    if (showAudioPicker && exoPlayer != null) {
        AudioPickerSheet(
            audioTracks = audioTracks,
            catColor    = catColor,
            onSelect    = { track ->
                selectAudioTrack(exoPlayer, track)
                // Refresh list to show new selection
                audioTracks = audioTracks.map { it.copy(isSelected = it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex) }
                showAudioPicker = false
            },
            onDismiss   = { showAudioPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audio selector bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AudioSelectorBar(
    audioTracks: List<AudioTrack>,
    catColor: Color,
    onOpenPicker: () -> Unit
) {
    val selected = audioTracks.firstOrNull { it.isSelected } ?: audioTracks.first()
    Surface(
        color    = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.RecordVoiceOver, null, tint = catColor, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = selected.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val detail = buildString {
                    if (selected.channelCount > 0) append(if (selected.channelCount >= 6) "5.1" else if (selected.channelCount == 2) "Stereo" else "Mono")
                    if (selected.sampleRate > 0) append(" · ${selected.sampleRate / 1000}kHz")
                }
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(shape = RoundedCornerShape(4.dp), color = catColor.copy(0.12f)) {
                Text(
                    text     = "${audioTracks.size} audio",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = catColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            TextButton(onClick = onOpenPicker, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Change", style = MaterialTheme.typography.labelMedium, color = catColor)
                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp), tint = catColor)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audio picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPickerSheet(
    audioTracks: List<AudioTrack>,
    catColor: Color,
    onSelect: (AudioTrack) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.RecordVoiceOver, null, tint = catColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Audio Track / Language", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${audioTracks.size} tracks available",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            audioTracks.forEachIndexed { idx, track ->
                AudioTrackRow(track = track, catColor = catColor, onClick = { onSelect(track) })
                if (idx < audioTracks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
    }
}
