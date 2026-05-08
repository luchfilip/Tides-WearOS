package dev.tidesapp.wearos.library.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil3.compose.AsyncImage
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.PlaylistItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import kotlinx.collections.immutable.ImmutableList

@Composable
fun PlaylistsScreen(
    onNavigateToPlaylistDetail: (String) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("tap_playlist", "Tap playlist by index (0-based)") {
            param("index", "string", "Playlist index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                val playlists = when (state) {
                    is PlaylistsUiState.Success -> state.playlists
                    is PlaylistsUiState.Refreshing -> state.playlists
                    else -> return@action null
                }
                if (idx in playlists.indices) {
                    viewModel.onEvent(PlaylistsUiEvent.PlaylistClicked(playlists[idx]))
                }
                null
            }
        }
        tool("refresh_playlists", "Pull to refresh playlists") {
            action { viewModel.onEvent(PlaylistsUiEvent.RetryLoad); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            PlaylistsUiState.Initial -> "initial"
            PlaylistsUiState.Loading -> "loading"
            is PlaylistsUiState.Success -> "success"
            is PlaylistsUiState.Refreshing -> "refreshing"
            is PlaylistsUiState.Error -> "error"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("playlist_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is PlaylistsUiState.Success -> state.playlists.size.toString()
            is PlaylistsUiState.Refreshing -> state.playlists.size.toString()
            else -> "0"
        })
    })

    PlaylistsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is PlaylistsUiEffect.NavigateToPlaylistDetail ->
                    onNavigateToPlaylistDetail(effect.playlistId)

                is PlaylistsUiEffect.ShowError -> { /* handled by snackbar in real app */ }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (uiState is PlaylistsUiState.Initial) {
            viewModel.onEvent(PlaylistsUiEvent.LoadPlaylists)
        }
    }
}

@Composable
fun PlaylistsContent(
    uiState: PlaylistsUiState,
    onEvent: (PlaylistsUiEvent) -> Unit,
) {
    when (uiState) {
        PlaylistsUiState.Initial,
        PlaylistsUiState.Loading -> LoadingScreen()

        is PlaylistsUiState.Success -> PlaylistsList(
            playlists = uiState.playlists,
            onEvent = onEvent,
        )

        is PlaylistsUiState.Refreshing -> PlaylistsList(
            playlists = uiState.playlists,
            onEvent = onEvent,
        )

        is PlaylistsUiState.Error -> ErrorScreen(
            message = uiState.message,
            onRetry = { onEvent(PlaylistsUiEvent.RetryLoad) },
        )
    }
}

@Composable
private fun PlaylistsList(
    playlists: ImmutableList<PlaylistItem>,
    onEvent: (PlaylistsUiEvent) -> Unit,
) {
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
                text = "Playlists",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
            )
        }
        items(playlists.size) { index ->
            val playlist = playlists[index]
            PlaylistChip(
                playlist = playlist,
                onClick = { onEvent(PlaylistsUiEvent.PlaylistClicked(playlist)) },
            )
        }
    }
    }
}

@Composable
private fun PlaylistChip(
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
        icon = {
            AsyncImage(
                model = playlist.imageUrl,
                contentDescription = playlist.title,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}
