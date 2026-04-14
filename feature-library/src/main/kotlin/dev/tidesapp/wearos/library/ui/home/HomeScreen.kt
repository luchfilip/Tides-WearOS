package dev.tidesapp.wearos.library.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import dev.tidesapp.wearos.core.ui.components.TidesChip
import kotlinx.collections.immutable.ImmutableList

@Composable
fun HomeScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToNowPlaying = onNavigateToNowPlaying,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToDownloads = onNavigateToDownloads,
        onNavigateToSettings = onNavigateToSettings,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HomeUiEffect.NavigateToAlbum -> onNavigateToAlbum(effect.albumId)
                is HomeUiEffect.NavigateToPlaylist -> onNavigateToPlaylist(effect.playlistId)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (uiState is HomeUiState.Initial) {
            viewModel.onEvent(HomeUiEvent.LoadHome)
        }
    }
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    when (uiState) {
        HomeUiState.Initial,
        HomeUiState.Loading -> LoadingScreen()

        is HomeUiState.Success -> HomeList(
            feedSections = uiState.feedSections,
            onEvent = onEvent,
            onNavigateToNowPlaying = onNavigateToNowPlaying,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToSettings = onNavigateToSettings,
        )

        is HomeUiState.Error -> ErrorScreen(
            message = uiState.message,
            onRetry = { onEvent(HomeUiEvent.LoadHome) },
        )
    }
}

@Composable
private fun HomeList(
    feedSections: ImmutableList<HomeFeedSection>,
    onEvent: (HomeUiEvent) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Text,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        ),
    )

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        columnState = columnState,
    ) {
        item {
            TidesChip(
                label = "Now Playing",
                onClick = onNavigateToNowPlaying,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        feedSections.forEach { section ->
            if (section.title.isNotBlank()) {
                item {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary,
                    )
                }
            }
            items(section.items.size) { index ->
                val item = section.items[index]
                TidesChip(
                    label = item.title,
                    secondaryLabel = when (item) {
                        is HomeFeedItem.Album -> item.artistName
                        is HomeFeedItem.Playlist -> item.creator
                        is HomeFeedItem.Mix -> item.subTitle
                    },
                    onClick = { onEvent(HomeUiEvent.FeedItemClicked(item)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Text(
                text = "More",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
            )
        }
        item {
            TidesChip(
                label = "Search",
                onClick = onNavigateToSearch,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TidesChip(
                label = "Downloads",
                onClick = onNavigateToDownloads,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TidesChip(
                label = "Settings",
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
