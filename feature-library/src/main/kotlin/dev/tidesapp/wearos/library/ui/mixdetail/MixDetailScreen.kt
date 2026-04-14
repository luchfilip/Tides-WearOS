package dev.tidesapp.wearos.library.ui.mixdetail

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
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import kotlinx.collections.immutable.ImmutableList

@Composable
fun MixDetailScreen(
    onNavigateToNowPlaying: () -> Unit,
    viewModel: MixDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MixDetailContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                MixDetailUiEffect.NavigateToNowPlaying -> onNavigateToNowPlaying()
                is MixDetailUiEffect.ShowError -> { /* surfaced via state elsewhere */ }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (uiState is MixDetailUiState.Initial) {
            viewModel.onEvent(MixDetailUiEvent.LoadMixDetail)
        }
    }
}

@Composable
fun MixDetailContent(
    uiState: MixDetailUiState,
    onEvent: (MixDetailUiEvent) -> Unit,
) {
    when (uiState) {
        MixDetailUiState.Initial,
        MixDetailUiState.Loading -> LoadingScreen()

        is MixDetailUiState.Success -> MixDetailBody(
            header = uiState.header,
            tracks = uiState.tracks,
            onEvent = onEvent,
        )

        is MixDetailUiState.Error -> ErrorScreen(
            message = uiState.message,
            onRetry = { onEvent(MixDetailUiEvent.LoadMixDetail) },
        )
    }
}

@Composable
private fun MixDetailBody(
    header: MixHeader,
    tracks: ImmutableList<TrackItem>,
    onEvent: (MixDetailUiEvent) -> Unit,
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
                text = header.title,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                maxLines = 2,
            )
        }
        item {
            val subtitleText = header.subTitle ?: "${tracks.size} tracks"
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        item {
            CompactChip(
                onClick = { onEvent(MixDetailUiEvent.PlayAll) },
                label = { Text("Play All") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        item {
            CompactChip(
                onClick = { onEvent(MixDetailUiEvent.ShufflePlay) },
                label = { Text("Shuffle") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            TrackChip(
                track = track,
                trackNumber = index + 1,
                onClick = { onEvent(MixDetailUiEvent.PlayTrack(track)) },
            )
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
