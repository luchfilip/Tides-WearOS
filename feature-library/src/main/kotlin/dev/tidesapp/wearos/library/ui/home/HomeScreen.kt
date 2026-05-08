package dev.tidesapp.wearos.library.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import dev.tidesapp.wearos.core.ui.components.TidesChip
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import kotlinx.collections.immutable.ImmutableList

@Composable
fun HomeScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToMix: (mixId: String, title: String, subTitle: String?, imageUrl: String?) -> Unit,
    onNavigateToViewAll: (viewAllPath: String, title: String) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("navigate_library", "Go to Library hub") {
            action { onNavigateToLibrary(); null }
        }
        tool("navigate_search", "Go to Search") {
            action { onNavigateToSearch(); null }
        }
        tool("navigate_now_playing", "Go to Now Playing") {
            action { onNavigateToNowPlaying(); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            HomeUiState.Initial -> "initial"
            HomeUiState.Loading -> "loading"
            is HomeUiState.Success -> "success"
            is HomeUiState.Error -> "error"
        })
    })

    HomeContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToNowPlaying = onNavigateToNowPlaying,
        onNavigateToLibrary = onNavigateToLibrary,
        onNavigateToSearch = onNavigateToSearch,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HomeUiEffect.NavigateToAlbum -> onNavigateToAlbum(effect.albumId)
                is HomeUiEffect.NavigateToPlaylist -> onNavigateToPlaylist(effect.playlistId)
                is HomeUiEffect.NavigateToMix -> onNavigateToMix(
                    effect.mixId,
                    effect.title,
                    effect.subTitle,
                    effect.imageUrl,
                )
                is HomeUiEffect.NavigateToViewAll ->
                    onNavigateToViewAll(effect.viewAllPath, effect.title)
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
    onNavigateToLibrary: () -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    when (uiState) {
        HomeUiState.Initial,
        HomeUiState.Loading -> LoadingScreen()

        is HomeUiState.Success -> HomeList(
            feedSections = uiState.feedSections,
            onEvent = onEvent,
            onNavigateToNowPlaying = onNavigateToNowPlaying,
            onNavigateToLibrary = onNavigateToLibrary,
            onNavigateToSearch = onNavigateToSearch,
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
    onNavigateToLibrary: () -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Chip,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        ),
    )

    ScreenScaffold(scrollState = columnState) {
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
        item {
            TidesChip(
                label = "Library",
                onClick = onNavigateToLibrary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TidesChip(
                label = "Search",
                onClick = onNavigateToSearch,
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
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
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
            val viewAllPath = section.viewAllPath
            if (viewAllPath != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-10).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompactChip(
                            onClick = {
                                onEvent(
                                    HomeUiEvent.SectionSeeAllClicked(
                                        viewAllPath = viewAllPath,
                                        sectionTitle = section.title,
                                    ),
                                )
                            },
                            label = {
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.caption2,
                                )
                            },
                            colors = ChipDefaults.childChipColors(),
                        )
                    }
                }
            }
        }

    }
    }
}
