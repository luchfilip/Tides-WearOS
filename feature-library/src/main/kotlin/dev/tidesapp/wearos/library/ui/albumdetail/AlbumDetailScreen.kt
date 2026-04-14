package dev.tidesapp.wearos.library.ui.albumdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumDetailScreen(
    albumId: String,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
    onEvent: (AlbumDetailUiEvent) -> Unit,
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
        items(tracks.size) { index ->
            val track = tracks[index]
            TrackChip(
                track = track,
                onClick = { onEvent(AlbumDetailUiEvent.PlayTrack(track)) },
            )
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
