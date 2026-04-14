package dev.tidesapp.wearos.player.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import dev.tidesapp.wearos.player.R

@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(NowPlayingUiEvent.ObservePlayerState)
    }

    NowPlayingContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is NowPlayingUiEffect.NavigateBack -> onNavigateBack()
                is NowPlayingUiEffect.ShowError -> { /* handled via state */ }
            }
        }
    }
}

@Composable
fun NowPlayingContent(
    uiState: NowPlayingUiState,
    onEvent: (NowPlayingUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is NowPlayingUiState.Initial,
        is NowPlayingUiState.Loading,
        -> LoadingContent(modifier)

        is NowPlayingUiState.Playing -> PlayingContent(
            state = uiState,
            onEvent = onEvent,
            modifier = modifier,
        )

        is NowPlayingUiState.Error -> ErrorContent(
            message = uiState.message,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            indicatorColor = MaterialTheme.colors.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Loading...",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
        )
    }
}

@Composable
private fun PlayingContent(
    state: NowPlayingUiState.Playing,
    onEvent: (NowPlayingUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.trackTitle,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = state.artistName,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onEvent(NowPlayingUiEvent.SkipPrevious) },
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_media_backward),
                    contentDescription = "Previous",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onEvent(NowPlayingUiEvent.PlayPause) },
                modifier = Modifier.size(ButtonDefaults.DefaultButtonSize),
            ) {
                Icon(
                    painter = painterResource(
                        if (state.isPlaying) R.drawable.ic_media_pause
                        else R.drawable.ic_media_play,
                    ),
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onEvent(NowPlayingUiEvent.SkipNext) },
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_media_forward),
                    contentDescription = "Next",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.error,
            textAlign = TextAlign.Center,
        )
    }
}
