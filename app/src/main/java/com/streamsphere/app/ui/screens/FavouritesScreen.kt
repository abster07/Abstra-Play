package com.streamsphere.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamsphere.app.data.model.*
import com.streamsphere.app.ui.components.*
import com.streamsphere.app.viewmodel.ChannelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    onChannelClick: (String) -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val favourites     by viewModel.favourites.collectAsState()
    val allChannels    by viewModel.filteredChannels.collectAsState()
    val widgetChannels by viewModel.widgetChannels.collectAsState()

    val favModels = remember(favourites, allChannels) {
        favourites.mapNotNull { fav ->
            // Prefer the enriched model from the live channel list (has full streamOptions).
            // Fall back to a minimal model built from the Room record if the channel
            // isn't in the current filtered list (e.g. after clearing search).
            allChannels.find { it.id == fav.id } ?: run {
                // Build a single StreamOption from the stored streamUrl so the
                // card/detail still works without crashing.
                val options = if (fav.streamUrl != null) {
                    listOf(
                        StreamOption(
                            feedId        = null,
                            feedName      = fav.name,
                            languages     = emptyList(),
                            languageNames = emptyList(),
                            quality       = null,
                            url           = fav.streamUrl,
                            referrer      = null,
                            userAgent     = null,
                            isMain        = true
                        )
                    )
                } else emptyList()

                ChannelUiModel(
                    id                  = fav.id,
                    name                = fav.name,
                    country             = fav.country,
                    countryFlag         = "🌐",
                    categories          = fav.categories.split(",").filter { it.isNotBlank() },
                    logoUrl             = fav.logoUrl,
                    streamOptions       = options,
                    selectedStreamIndex = 0,
                    isFavourite         = true,
                    isWidget            = fav.isWidget
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Favourites", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${favModels.size} channels saved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (favModels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("💜", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text("No favourites yet", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap the heart icon on any channel to save it here.",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start  = 16.dp,
                    end    = 16.dp,
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (widgetChannels.isNotEmpty()) {
                    item {
                        Text(
                            "📱 Widget Channels",
                            style    = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(widgetChannels, key = { "widget_${it.id}" }) { fav ->
                        val model = favModels.find { it.id == fav.id } ?: return@items
                        ChannelCard(
                            model            = model,
                            onFavouriteClick = { viewModel.toggleFavourite(model) },
                            onWidgetClick    = { viewModel.toggleWidget(model) },
                            onCardClick      = { onChannelClick(model.id) }
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    item {
                        Text(
                            "⭐ All Favourites",
                            style    = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                items(favModels, key = { it.id }) { model ->
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically()
                    ) {
                        ChannelCard(
                            model            = model,
                            onFavouriteClick = { viewModel.toggleFavourite(model) },
                            onWidgetClick    = { viewModel.toggleWidget(model) },
                            onCardClick      = { onChannelClick(model.id) }
                        )
                    }
                }
            }
        }
    }
}
