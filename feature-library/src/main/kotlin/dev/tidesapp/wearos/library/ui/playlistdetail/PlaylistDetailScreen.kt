package dev.tidesapp.wearos.library.ui.playlistdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.PlaylistItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import kotlinx.collections.immutable.ImmutableList

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("play_all", "Play all tracks") {
            action { viewModel.onEvent(PlaylistDetailUiEvent.PlayAll); null }
        }
        tool("shuffle_play", "Shuffle play all tracks") {
            action { viewModel.onEvent(PlaylistDetailUiEvent.ShufflePlay); null }
        }
        tool("play_track", "Play track by index (0-based)") {
            param("index", "string", "Track index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                if (state is PlaylistDetailUiState.Success && idx in state.tracks.indices) {
                    viewModel.onEvent(PlaylistDetailUiEvent.PlayTrack(state.tracks[idx]))
                }
                null
            }
        }
        tool("download_playlist", "Download this playlist for offline") {
            action { viewModel.onEvent(PlaylistDetailUiEvent.DownloadPlaylist); null }
        }
        tool("remove_download", "Remove offline download") {
            action { viewModel.onEvent(PlaylistDetailUiEvent.RemoveDownload); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            PlaylistDetailUiState.Initial -> "initial"
            PlaylistDetailUiState.Loading -> "loading"
            is PlaylistDetailUiState.Success -> "success"
            is PlaylistDetailUiState.Error -> "error"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("playlist_title").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is PlaylistDetailUiState.Success -> state.playlist.title
            else -> ""
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("track_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is PlaylistDetailUiState.Success -> state.tracks.size.toString()
            else -> "0"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("download_state").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is PlaylistDetailUiState.Success -> when {
                state.isDownloading -> "downloading"
                state.isDownloaded -> "downloaded"
                else -> "not_downloaded"
            }
            else -> "unknown"
        })
    })

    PlaylistDetailContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                PlaylistDetailUiEffect.NavigateToNowPlaying -> onNavigateToNowPlaying()
                is PlaylistDetailUiEffect.ShowError -> { /* handled by snackbar in real app */ }
            }
        }
    }

    LaunchedEffect(playlistId) {
        if (uiState is PlaylistDetailUiState.Initial) {
            viewModel.onEvent(PlaylistDetailUiEvent.LoadPlaylistDetail(playlistId))
        }
    }
}

@Composable
fun PlaylistDetailContent(
    uiState: PlaylistDetailUiState,
    onEvent: (PlaylistDetailUiEvent) -> Unit,
) {
    when (uiState) {
        PlaylistDetailUiState.Initial,
        PlaylistDetailUiState.Loading -> LoadingScreen()

        is PlaylistDetailUiState.Success -> PlaylistDetailBody(
            playlist = uiState.playlist,
            tracks = uiState.tracks,
            isDownloaded = uiState.isDownloaded,
            isDownloading = uiState.isDownloading,
            onEvent = onEvent,
        )

        is PlaylistDetailUiState.Error -> ErrorScreen(
            message = uiState.message,
            onRetry = { },
        )
    }
}

@Composable
private fun PlaylistDetailBody(
    playlist: PlaylistItem,
    tracks: ImmutableList<TrackItem>,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onEvent: (PlaylistDetailUiEvent) -> Unit,
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
                text = playlist.title,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                maxLines = 2,
            )
        }
        item {
            Text(
                text = "${playlist.numberOfTracks} tracks • ${playlist.creator}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        item {
            CompactChip(
                onClick = { onEvent(PlaylistDetailUiEvent.PlayAll) },
                label = { Text("Play All") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        item {
            CompactChip(
                onClick = { onEvent(PlaylistDetailUiEvent.ShufflePlay) },
                label = { Text("Shuffle") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            CompactChip(
                onClick = {
                    if (isDownloaded) {
                        onEvent(PlaylistDetailUiEvent.RemoveDownload)
                    } else {
                        onEvent(PlaylistDetailUiEvent.DownloadPlaylist)
                    }
                },
                label = {
                    Text(
                        when {
                            isDownloading -> "Downloading..."
                            isDownloaded -> "Remove Download"
                            else -> "Download"
                        }
                    )
                },
                colors = if (isDownloaded) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
            )
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            TrackChip(
                track = track,
                trackNumber = index + 1,
                onClick = { onEvent(PlaylistDetailUiEvent.PlayTrack(track)) },
            )
        }
    }
    }
}

@Composable
private fun TrackChip(
    track: TrackItem,
    trackNumber: Int,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = {
            Text(
                text = "$trackNumber. ${track.title}",
                style = MaterialTheme.typography.button,
                maxLines = 1,
            )
        },
        secondaryLabel = {
            val minutes = track.duration / 60
            val seconds = track.duration % 60
            Text(
                text = "%d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}
