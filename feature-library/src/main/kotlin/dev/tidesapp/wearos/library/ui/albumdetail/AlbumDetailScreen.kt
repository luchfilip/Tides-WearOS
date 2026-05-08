package dev.tidesapp.wearos.library.ui.albumdetail

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
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumDetailScreen(
    albumId: String,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("play_all", "Play all tracks") {
            action { viewModel.onEvent(AlbumDetailUiEvent.PlayAll); null }
        }
        tool("play_track", "Play track by index (0-based)") {
            param("index", "string", "Track index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                if (state is AlbumDetailUiState.Success && idx in state.tracks.indices) {
                    viewModel.onEvent(AlbumDetailUiEvent.PlayTrack(state.tracks[idx]))
                }
                null
            }
        }
        tool("download_album", "Download this album for offline") {
            action { viewModel.onEvent(AlbumDetailUiEvent.DownloadAlbum); null }
        }
        tool("remove_download", "Remove offline download") {
            action { viewModel.onEvent(AlbumDetailUiEvent.RemoveDownload); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            AlbumDetailUiState.Initial -> "initial"
            AlbumDetailUiState.Loading -> "loading"
            is AlbumDetailUiState.Success -> "success"
            is AlbumDetailUiState.Error -> "error"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("album_title").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is AlbumDetailUiState.Success -> state.album.title
            else -> ""
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("artist_name").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is AlbumDetailUiState.Success -> state.album.artistName
            else -> ""
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("track_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is AlbumDetailUiState.Success -> state.tracks.size.toString()
            else -> "0"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("download_state").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is AlbumDetailUiState.Success -> when {
                state.isDownloading -> "downloading"
                state.isDownloaded -> "downloaded"
                else -> "not_downloaded"
            }
            else -> "unknown"
        })
    })

    AlbumDetailContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                AlbumDetailUiEffect.NavigateToNowPlaying -> onNavigateToNowPlaying()
                is AlbumDetailUiEffect.ShowError -> { /* surfaced via state elsewhere */ }
            }
        }
    }

    LaunchedEffect(albumId) {
        if (uiState is AlbumDetailUiState.Initial) {
            viewModel.onEvent(AlbumDetailUiEvent.LoadAlbumDetail(albumId))
        }
    }
}

@Composable
fun AlbumDetailContent(
    uiState: AlbumDetailUiState,
    onEvent: (AlbumDetailUiEvent) -> Unit,
) {
    when (uiState) {
        AlbumDetailUiState.Initial,
        AlbumDetailUiState.Loading -> LoadingScreen()

        is AlbumDetailUiState.Success -> AlbumDetailBody(
            album = uiState.album,
            tracks = uiState.tracks,
            isDownloaded = uiState.isDownloaded,
            isDownloading = uiState.isDownloading,
            onEvent = onEvent,
        )

        is AlbumDetailUiState.Error -> ErrorScreen(
            message = uiState.message,
            onRetry = { },
        )
    }
}

@Composable
private fun AlbumDetailBody(
    album: AlbumItem,
    tracks: ImmutableList<TrackItem>,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onEvent: (AlbumDetailUiEvent) -> Unit,
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
                text = album.title,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                maxLines = 2,
            )
        }
        item {
            Text(
                text = album.artistName,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        item {
            CompactChip(
                onClick = { onEvent(AlbumDetailUiEvent.PlayAll) },
                label = { Text("Play All") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        item {
            CompactChip(
                onClick = {
                    if (isDownloaded) {
                        onEvent(AlbumDetailUiEvent.RemoveDownload)
                    } else {
                        onEvent(AlbumDetailUiEvent.DownloadAlbum)
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
                onClick = { onEvent(AlbumDetailUiEvent.PlayTrack(track)) },
            )
        }
    }
    }
}

@Composable
private fun TrackChip(
    track: TrackItem,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = {
            Text(
                text = "${track.trackNumber}. ${track.title}",
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
