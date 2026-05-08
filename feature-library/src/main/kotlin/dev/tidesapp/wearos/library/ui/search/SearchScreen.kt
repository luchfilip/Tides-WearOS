package dev.tidesapp.wearos.library.ui.search

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import coil3.compose.AsyncImage
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.ArtistItem
import dev.tidesapp.wearos.core.domain.model.PlaylistItem
import dev.tidesapp.wearos.core.domain.model.SearchResult
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import kotlinx.collections.immutable.ImmutableList

internal const val REMOTE_INPUT_QUERY_KEY = "query_key"

/**
 * Extracts the trimmed search query from a RemoteInput activity result intent.
 * Returns `null` when the intent is null, has no remote input results, or the
 * query is blank after trimming.
 */
internal fun extractSearchQuery(intent: Intent?): String? {
    if (intent == null) return null
    val results = RemoteInput.getResultsFromIntent(intent) ?: return null
    val raw = results.getCharSequence(REMOTE_INPUT_QUERY_KEY)?.toString() ?: return null
    val trimmed = raw.trim()
    return trimmed.ifBlank { null }
}

@Composable
fun SearchScreen(
    onNavigateToAlbumDetail: (String) -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("search", "Search for music") {
            param("query", "string", "Search query")
            action { params ->
                val query = params["query"]?.toString() ?: return@action null
                viewModel.onEvent(SearchUiEvent.Search(query))
                null
            }
        }
        tool("tap_result", "Tap search result by index and type") {
            param("index", "string", "Result index (0-based)")
            param("type", "string", "Result type: album, track, playlist")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val type = params["type"]?.toString() ?: return@action null
                val state = viewModel.uiState.value
                if (state is SearchUiState.Success) {
                    val result = state.result
                    when (type) {
                        "album" -> if (idx in result.albums.indices) {
                            viewModel.onEvent(SearchUiEvent.ResultClicked(SearchResultType.Album(result.albums[idx].id)))
                        }
                        "track" -> if (idx in result.tracks.indices) {
                            viewModel.onEvent(SearchUiEvent.ResultClicked(SearchResultType.Track(result.tracks[idx])))
                        }
                        "playlist" -> if (idx in result.playlists.indices) {
                            viewModel.onEvent(SearchUiEvent.ResultClicked(SearchResultType.Playlist(result.playlists[idx].id)))
                        }
                    }
                }
                null
            }
        }
        tool("clear_search", "Clear search results") {
            action { viewModel.onEvent(SearchUiEvent.ClearSearch); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            SearchUiState.Initial -> "initial"
            SearchUiState.Loading -> "loading"
            is SearchUiState.Success -> "success"
            SearchUiState.Empty -> "empty"
            is SearchUiState.Error -> "error"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("result_counts").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is SearchUiState.Success -> {
                val r = state.result
                "albums=${r.albums.size},tracks=${r.tracks.size},playlists=${r.playlists.size},artists=${r.artists.size}"
            }
            else -> "albums=0,tracks=0,playlists=0,artists=0"
        })
    })

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

    val remoteInputIntent = remember {
        val remoteInputs = listOf(
            RemoteInput.Builder(REMOTE_INPUT_QUERY_KEY)
                .setLabel("Search TIDAL")
                .wearableExtender { setEmojisAllowed(false) }
                .build(),
        )
        RemoteInputIntentHelper.createActionRemoteInputIntent().apply {
            RemoteInputIntentHelper.putRemoteInputsExtra(this, remoteInputs)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val query = extractSearchQuery(result.data)
        if (!query.isNullOrBlank()) {
            searchQuery = query
            onEvent(SearchUiEvent.Search(query))
        }
    }

    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Text,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        ),
    )

    ScreenScaffold(scrollState = columnState) {
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
                onClick = { launcher.launch(remoteInputIntent) },
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
