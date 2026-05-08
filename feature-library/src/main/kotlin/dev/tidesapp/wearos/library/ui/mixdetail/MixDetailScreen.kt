package dev.tidesapp.wearos.library.ui.mixdetail

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
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import kotlinx.collections.immutable.ImmutableList

@Composable
fun MixDetailScreen(
    onNavigateToNowPlaying: () -> Unit,
    viewModel: MixDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("play_all", "Play all tracks") {
            action { viewModel.onEvent(MixDetailUiEvent.PlayAll); null }
        }
        tool("shuffle_play", "Shuffle play all tracks") {
            action { viewModel.onEvent(MixDetailUiEvent.ShufflePlay); null }
        }
        tool("play_track", "Play track by index (0-based)") {
            param("index", "string", "Track index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                if (state is MixDetailUiState.Success && idx in state.tracks.indices) {
                    viewModel.onEvent(MixDetailUiEvent.PlayTrack(state.tracks[idx]))
                }
                null
            }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            MixDetailUiState.Initial -> "initial"
            MixDetailUiState.Loading -> "loading"
            is MixDetailUiState.Success -> "success"
            is MixDetailUiState.Error -> "error"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("mix_title").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is MixDetailUiState.Success -> state.header.title
            else -> ""
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("track_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is MixDetailUiState.Success -> state.tracks.size.toString()
            else -> "0"
        })
    })

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

    ScreenScaffold(scrollState = columnState) {
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
