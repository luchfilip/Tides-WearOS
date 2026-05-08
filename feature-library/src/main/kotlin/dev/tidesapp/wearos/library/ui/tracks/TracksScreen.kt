package dev.tidesapp.wearos.library.ui.tracks

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
fun TracksScreen(
    onNavigateToNowPlaying: () -> Unit,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("tap_track", "Tap track by index (0-based)") {
            param("index", "string", "Track index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                if (state is TracksUiState.Success && idx in state.tracks.indices) {
                    viewModel.onEvent(TracksUiEvent.TrackClicked(idx))
                }
                null
            }
        }
        tool("retry", "Retry loading tracks") {
            action { viewModel.onEvent(TracksUiEvent.Retry); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            TracksUiState.Initial -> "initial"
            TracksUiState.Loading -> "loading"
            is TracksUiState.Success -> "success"
            is TracksUiState.Error -> "error"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("track_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is TracksUiState.Success -> state.tracks.size.toString()
            else -> "0"
        })
    })

    TracksContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                TracksUiEffect.NavigateToNowPlaying -> onNavigateToNowPlaying()
                is TracksUiEffect.ShowError -> { /* handled by snackbar in real app */ }
            }
        }
    }
}

@Composable
fun TracksContent(
    uiState: TracksUiState,
    onEvent: (TracksUiEvent) -> Unit,
) {
    when (uiState) {
        TracksUiState.Initial,
        TracksUiState.Loading -> LoadingScreen()

        is TracksUiState.Success -> TracksList(
            title = "My Tracks",
            tracks = uiState.tracks,
            onEvent = onEvent,
        )

        is TracksUiState.Error -> ErrorScreen(
            message = uiState.message,
            onRetry = { onEvent(TracksUiEvent.Retry) },
        )
    }
}

@Composable
private fun TracksList(
    title: String,
    tracks: ImmutableList<TrackItem>,
    onEvent: (TracksUiEvent) -> Unit,
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
                text = title,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
            )
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            TrackChip(
                track = track,
                trackNumber = index + 1,
                onClick = { onEvent(TracksUiEvent.TrackClicked(index)) },
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
