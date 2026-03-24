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
    var isBuffering  by remember { mutableStateOf(false) }
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
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
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
    if (exoPlayer != null && !isPlaying && !isBuffering && playerError == null) {
        ThumbnailOverlay(channel, catColor) {
            playerError = null
            exoPlayer.playWhenReady = true
        }
    }

    // 2b. Buffering overlay — shown while stream is loading
    if (exoPlayer != null && isBuffering && playerError == null) {
        BufferingOverlay()
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

@Composable
private fun AudioTrackRow(
    track: AudioTrack,
    catColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick  = onClick,
        color    = if (track.isSelected) catColor.copy(0.08f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selection indicator
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (track.isSelected) {
                    Icon(Icons.Filled.CheckCircle, null, tint = catColor, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Outlined.RadioButtonUnchecked, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = track.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (track.isSelected) catColor else MaterialTheme.colorScheme.onSurface
                )
                val detail = buildList {
                    if (track.channelCount >= 6) add("5.1 Surround")
                    else if (track.channelCount == 2) add("Stereo")
                    else if (track.channelCount == 1) add("Mono")
                    if (track.sampleRate > 0) add("${track.sampleRate / 1000} kHz")
                }.joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Language code badge
            track.language?.let { lang ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text     = lang.uppercase(),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feed selector bar (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeedSelectorBar(
    channel: ChannelUiModel,
    onOpenPicker: () -> Unit,
    catColor: Color
) {
    val current = channel.currentStream
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Subtitles, null, tint = catColor, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(current?.feedName ?: "Default", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                if (!current?.languageNames.isNullOrEmpty()) {
                    Text(current!!.languageNames.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            current?.quality?.let {
                Surface(shape = RoundedCornerShape(4.dp), color = catColor.copy(0.15f)) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = catColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            TextButton(onClick = onOpenPicker, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Change", style = MaterialTheme.typography.labelMedium, color = catColor)
                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp), tint = catColor)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feed picker sheet (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPickerSheet(
    channel: ChannelUiModel,
    catColor: Color,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Language, null, tint = catColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose Feed / Quality", style = MaterialTheme.typography.titleMedium)
            }
            Text("${channel.streamOptions.size} feeds available",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            channel.streamOptions.forEachIndexed { idx, option ->
                val isSelected = idx == channel.selectedStreamIndex
                Surface(
                    onClick  = { onSelect(idx) },
                    color    = if (isSelected) catColor.copy(0.08f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = catColor, modifier = Modifier.size(22.dp))
                            else Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(option.feedName, style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface)
                                if (option.isMain) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = catColor.copy(0.15f)) {
                                        Text("MAIN", style = MaterialTheme.typography.labelSmall, color = catColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            if (option.languageNames.isNotEmpty()) {
                                Text("🌐 " + option.languageNames.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        option.quality?.let { q ->
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(q, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                if (idx < channel.streamOptions.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fullscreen player
// ─────────────────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullscreenPlayer(
    channel: ChannelUiModel,
    onExitFullscreen: () -> Unit,
    onFavourite: () -> Unit,
    onWidget: () -> Unit,
    onSelectStream: (Int) -> Unit
) {
    val context   = LocalContext.current
    val activity  = context as Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val catColor  = categoryColorFor(channel)

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol       = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    var isPlaying       by remember { mutableStateOf(true) }
    var playerError     by remember { mutableStateOf<String?>(null) }
    var isBuffering     by remember { mutableStateOf(false) }
    var isLocked        by remember { mutableStateOf(false) }
    var showControls    by remember { mutableStateOf(true) }
    var isRotLocked     by remember { mutableStateOf(false) }
    var showFeedPicker  by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var audioTracks     by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }

    var volumeLevel by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    var brightnessLevel by remember {
        val cur = activity.window.attributes.screenBrightness
        mutableStateOf(if (cur < 0) 0.5f else cur)
    }

    fun applyBrightness(value: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = value.coerceIn(0.01f, 1.0f)
        activity.window.attributes = lp
    }
    fun applyVolume(value: Float) {
        val safe = value.coerceAtLeast(0.05f)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
            (safe * maxVol).roundToInt().coerceIn(0, maxVol.roundToInt()), 0)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            val v = (maxVol * 0.3f).roundToInt().coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            volumeLevel = v / maxVol
        }
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.apply {
                hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val lp = activity.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) { delay(4000); showControls = false }
    }

    val exoPlayer = rememberExoPlayer(context, channel.streamUrl, true) { err ->
        playerError = err; isPlaying = false
    }
    LaunchedEffect(exoPlayer) {
        exoPlayer?.volume = 1f
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = extractAudioTracks(tracks)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
            if (!isLocked) {
            detectVerticalDragGestures { change, dragAmount ->
            val delta  = -dragAmount / size.height.toFloat()
            val isLeft = change.position.x < size.width / 2f
            if (isLeft) {
                val b = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                brightnessLevel = b
                applyBrightness(b)
            } else {
                val v = (volumeLevel + delta).coerceAtLeast(0.05f).coerceAtMost(1f)
                volumeLevel = v
                applyVolume(v)
            }

            showControls = true
        }
    }
}
            .pointerInput(Unit) {
              detectTapGestures(
                onTap = { showControls = !showControls }
              )
}
    ) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer; useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        EdgeLevelBars(brightnessLevel = brightnessLevel, volumeLevel = volumeLevel)

        // Buffering indicator — shown while stream is loading
        if (isBuffering && playerError == null) {
            BufferingOverlay()
        }

        if (isLocked) {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(0.55f)) {
                        IconButton(onClick = { isLocked = false; showControls = true },
                            modifier = Modifier.padding(12.dp).size(48.dp)) {
                            Icon(Icons.Filled.Lock, "Unlock", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        } else {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                FullscreenControls(
                    channel          = channel,
                    isPlaying        = isPlaying,
                    catColor         = catColor,
                    isRotLocked      = isRotLocked,
                    audioTracks      = audioTracks,
                    onPlayPause      = { if (isPlaying) exoPlayer?.pause() else exoPlayer?.play() },
                    onReplay         = { exoPlayer?.seekBack() },
                    onForward        = { exoPlayer?.seekForward() },
                    onExitFullscreen = onExitFullscreen,
                    onLock           = { isLocked = true; showControls = false },
                    onToggleRot      = {
                        isRotLocked = !isRotLocked
                        activity.requestedOrientation = if (isRotLocked)
                            ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                    onOpenFeedPicker  = if (channel.hasMultipleFeeds) {{ showFeedPicker = true }} else null,
                    onOpenAudioPicker = if (audioTracks.size > 1) {{ showAudioPicker = true }} else null
                )
            }
            playerError?.let { err ->
                ErrorOverlay(err) { playerError = null; exoPlayer?.prepare(); exoPlayer?.play() }
            }
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
                audioTracks = audioTracks.map {
                    it.copy(isSelected = it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex)
                }
                showAudioPicker = false
            },
            onDismiss   = { showAudioPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fullscreen controls — now with audio track button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullscreenControls(
    channel: ChannelUiModel,
    isPlaying: Boolean,
    catColor: Color,
    isRotLocked: Boolean,
    audioTracks: List<AudioTrack>,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit,
    onExitFullscreen: () -> Unit,
    onLock: () -> Unit,
    onToggleRot: () -> Unit,
    onOpenFeedPicker: (() -> Unit)?,
    onOpenAudioPicker: (() -> Unit)?
) {
    val selectedAudio = audioTracks.firstOrNull { it.isSelected }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f))) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExitFullscreen) {
                Icon(Icons.Filled.FullscreenExit, "Exit fullscreen", tint = Color.White)
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (channel.hasMultipleFeeds) {
                    channel.currentStream?.let { s ->
                        Text(
                            s.feedName + if (s.languageNames.isNotEmpty()) " · ${s.languageNames.first()}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(0.7f)
                        )
                    }
                }
            }
            LiveBadge()
            Spacer(Modifier.width(4.dp))

            // Audio track picker button — shows current language code
            if (onOpenAudioPicker != null) {
                IconButton(onClick = onOpenAudioPicker) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.RecordVoiceOver, "Audio Track", tint = catColor,
                            modifier = Modifier.size(20.dp))
                        selectedAudio?.language?.let { lang ->
                            Text(lang.uppercase(), style = MaterialTheme.typography.labelSmall,
                                color = catColor)
                        }
                    }
                }
            }
            // Feed picker button
            if (onOpenFeedPicker != null) {
                IconButton(onClick = onOpenFeedPicker) {
                    Icon(Icons.Filled.Subtitles, "Change Feed", tint = catColor)
                }
            }
            // Rotation lock
            IconButton(onClick = onToggleRot) {
                Icon(
                    imageVector        = if (isRotLocked) Icons.Filled.ScreenLockRotation else Icons.Filled.ScreenRotation,
                    contentDescription = "Rotation",
                    tint               = if (isRotLocked) catColor else Color.White
                )
            }
            // Screen lock
            IconButton(onClick = onLock) {
                Icon(Icons.Outlined.LockOpen, "Lock", tint = Color.White)
            }
        }

        // Center controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onReplay, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Replay10, "Replay 10s", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick  = onPlayPause,
                modifier = Modifier.size(64.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(containerColor = catColor)
            ) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        Text(
            text     = "← Brightness  |  Volume →",
            style    = MaterialTheme.typography.labelSmall,
            color    = Color.White.copy(0.45f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edge level bars
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EdgeLevelBars(brightnessLevel: Float, volumeLevel: Float) {
    Box(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxHeight().width(5.dp).align(Alignment.CenterStart).padding(vertical = 60.dp)) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(0.12f), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth().fillMaxHeight(brightnessLevel.coerceIn(0.01f, 1f)).align(Alignment.BottomStart).background(Color(0xFFFBD38D).copy(0.85f), RoundedCornerShape(3.dp)))
        }
        Box(modifier = Modifier.fillMaxHeight().width(5.dp).align(Alignment.CenterEnd).padding(vertical = 60.dp)) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(0.12f), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth().fillMaxHeight(volumeLevel.coerceIn(0.05f, 1f)).align(Alignment.BottomStart).background(Color(0xFF4F8EF7).copy(0.85f), RoundedCornerShape(3.dp)))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared overlays
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThumbnailOverlay(channel: ChannelUiModel, catColor: Color, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize().background(catColor.copy(0.08f)), contentAlignment = Alignment.Center) {
        if (channel.logoUrl != null)
            AsyncImage(model = channel.logoUrl, contentDescription = channel.name,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(32.dp))
        else Text(channel.countryFlag, style = MaterialTheme.typography.displayLarge)
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.30f)), contentAlignment = Alignment.Center) {
        FilledIconButton(onClick = onPlay, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = catColor)) {
            Icon(Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.75f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFFC8181), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text      = message,
                style     = MaterialTheme.typography.bodyMedium,
                color     = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC8181))
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun BufferingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExoPlayer helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Extract all audio track groups from the current Tracks object. */
@androidx.annotation.OptIn(UnstableApi::class)
fun extractAudioTracks(tracks: Tracks): List<AudioTrack> {
    val result = mutableListOf<AudioTrack>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val isSelected = group.isTrackSelected(trackIndex)
            val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }
            // Build a human-readable label
            val label = when {
                lang != null -> {
                    try {
                        val locale = Locale.forLanguageTag(lang)
                        locale.getDisplayLanguage(Locale.ENGLISH)
                            .takeIf { it.isNotBlank() && it != lang } ?: lang.uppercase()
                    } catch (e: Exception) { lang.uppercase() }
                }
                format.label != null && format.label!!.isNotBlank() -> format.label!!
                else -> "Audio ${result.size + 1}"
            }
            result.add(
                AudioTrack(
                    groupIndex   = groupIndex,
                    trackIndex   = trackIndex,
                    language     = lang,
                    label        = label,
                    channelCount = format.channelCount,
                    sampleRate   = format.sampleRate,
                    isSelected   = isSelected
                )
            )
        }
    }
    return result
}

/** Force ExoPlayer to play a specific audio track and disable automatic selection for audio. */
@androidx.annotation.OptIn(UnstableApi::class)
fun selectAudioTrack(player: ExoPlayer, track: AudioTrack) {
    val currentTracks = player.currentTracks
    val group = currentTracks.groups.getOrNull(track.groupIndex) ?: return
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        // Override: select exactly this group+track, disable auto for audio
        .setOverrideForType(
            androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
        )
        // Ensure audio is never disabled
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .build()
    // Make sure player volume is audible
    player.volume = 1f
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun rememberExoPlayer(
    context: Context,
    streamUrl: String?,
    autoPlay: Boolean,
    onError: (String) -> Unit
): ExoPlayer? {
    val playerRef = remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(streamUrl) {
        if (streamUrl == null) {
            playerRef.value = null
            return@DisposableEffect onDispose {}
        }

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        val player = ExoPlayer.Builder(context, renderersFactory).build().apply {
            volume = 1f
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android) VLC/3.0")
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept"     to "*/*",
                        "Connection" to "keep-alive"
                    )
                )
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)

            val src = HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)))

            setMediaSource(src)
            prepare()
            playWhenReady = autoPlay
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onError(friendlyPlaybackError(error))
                }
            })
        }

        playerRef.value = player

        onDispose {
            player.stop()
            player.release()
            playerRef.value = null
        }
    }

    return playerRef.value
}

private fun friendlyPlaybackError(error: PlaybackException): String {
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "No connection — check your internet and try again."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Stream unavailable (server returned an error)."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Stream not found. It may have moved or gone offline."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "Unsupported stream format."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "Playback failed — codec or decoding error."
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
            "Fell behind the live stream. Retrying…"
        else -> "Playback error (${error.errorCode}): ${error.message?.take(120) ?: "Unknown error"}"
    }
}

private fun categoryColorFor(channel: ChannelUiModel): Color = when {
    channel.categories.any { it in listOf("music", "entertainment") } -> MusicPurple
    channel.categories.any { it in listOf("science", "education", "kids") } -> ScienceBlue
    channel.country.contains("Nepal", ignoreCase = true) -> NepalRed
    channel.country.contains("India", ignoreCase = true) -> IndiaOrange
    else -> Primary
}