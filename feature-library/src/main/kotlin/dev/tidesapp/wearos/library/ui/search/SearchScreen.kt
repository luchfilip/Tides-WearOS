package dev.tidesapp.wearos.library.ui.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil3.compose.AsyncImage
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.ArtistItem
import dev.tidesapp.wearos.core.domain.model.PlaylistItem
import dev.tidesapp.wearos.core.domain.model.SearchResult
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SearchScreen(
    onNavigateToAlbumDetail: (String) -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SearchUiEffect.NavigateToAlbumDetail ->
                    onNavigateToAlbumDetail(effect.albumId)

                is SearchUiEffect.NavigateToPlaylistDetail ->
                    onNavigateToPlaylistDetail(effect.playlistId)

                SearchUiEffect.NavigateToNowPlaying -> onNavigateToNowPlaying()

                is SearchUiEffect.ShowError -> { /* handled by snackbar in real app */ }
            }
        }
    }
}

@Composable
fun SearchContent(
    uiState: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

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
            Text(
                text = "Search",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
            )
        }
        item {
            CompactChip(
                onClick = {
                    // TODO: Launch voice input or text input dialog
                    // For now, this is a placeholder for triggering search
                },
                label = {
                    Text(
                        text = if (searchQuery.isBlank()) "Tap to search..." else searchQuery,
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        when (uiState) {
            SearchUiState.Initial -> { /* empty state, waiting for query */ }
            SearchUiState.Loading -> item {
                LoadingScreen()
            }

            SearchUiState.Empty -> item {
                Text(
                    text = "No results found",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            is SearchUiState.Success -> {
                val result = uiState.result
                if (result.albums.isNotEmpty()) {
                    item {
                        SectionHeader("Albums")
                    }
                    items(result.albums.size) { index ->
                        AlbumResultChip(
                            album = result.albums[index],
                            onClick = {
                                onEvent(
                                    SearchUiEvent.ResultClicked(
                                        SearchResultType.Album(result.albums[index].id)
                                    )
                                )
                            },
                        )
                    }
                }
                if (result.tracks.isNotEmpty()) {
                    item {
                        SectionHeader("Tracks")
                    }
                    items(result.tracks.size) { index ->
                        TrackResultChip(
                            track = result.tracks[index],
                            onClick = {
                                onEvent(
                                    SearchUiEvent.ResultClicked(
                                        SearchResultType.Track(result.tracks[index])
                                    )
                                )
                            },
                        )
                    }
                }
                if (result.playlists.isNotEmpty()) {
                    item {
                        SectionHeader("Playlists")
                    }
                    items(result.playlists.size) { index ->
                        PlaylistResultChip(
                            playlist = result.playlists[index],
                            onClick = {
                                onEvent(
                                    SearchUiEvent.ResultClicked(
                                        SearchResultType.Playlist(result.playlists[index].id)
                                    )
                                )
                            },
                        )
                    }
                }
                if (result.artists.isNotEmpty()) {
                    item {
                        SectionHeader("Artists")
                    }
                    items(result.artists.size) { index ->
                        ArtistResultChip(artist = result.artists[index])
                    }
                }
            }

            is SearchUiState.Error -> item {
                ErrorScreen(
                    message = uiState.message,
                    onRetry = {
                        if (searchQuery.isNotBlank()) {
                            onEvent(SearchUiEvent.Search(searchQuery))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.primary,
    )
}

@Composable
private fun AlbumResultChip(
    album: AlbumItem,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = {
            Text(
                text = album.title,
                style = MaterialTheme.typography.button,
                maxLines = 1,
            )
        },
        secondaryLabel = {
            Text(
                text = album.artistName,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        },
        icon = {
            AsyncImage(
                model = album.imageUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

@Composable
private fun TrackResultChip(
    track: TrackItem,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = {
            Text(
                text = track.title,
                style = MaterialTheme.typography.button,
                maxLines = 1,
            )
        },
        secondaryLabel = {
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

@Composable
private fun PlaylistResultChip(
    playlist: PlaylistItem,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.button,
                maxLines = 1,
            )
        },
        secondaryLabel = {
            Text(
                text = "${playlist.numberOfTracks} tracks",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

@Composable
private fun ArtistResultChip(
    artist: ArtistItem,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* TODO: Navigate to artist detail */ },
        label = {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.button,
                maxLines = 1,
            )
        },
        icon = {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50)),
                contentScale = ContentScale.Crop,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}
