package dev.tidesapp.wearos.player.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidesapp.wearos.player.domain.repository.PlayerRepository
import dev.tidesapp.wearos.player.domain.repository.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NowPlayingUiState {
    data object Initial : NowPlayingUiState
    data object Loading : NowPlayingUiState
    data class Playing(
        val trackTitle: String,
        val artistName: String,
        val albumArtUrl: String?,
        val isPlaying: Boolean,
        val progressMs: Long,
        val durationMs: Long,
    ) : NowPlayingUiState

    data class Error(val message: String) : NowPlayingUiState
}

sealed interface NowPlayingUiEvent {
    data object ObservePlayerState : NowPlayingUiEvent
    data object PlayPause : NowPlayingUiEvent
    data object SkipNext : NowPlayingUiEvent
    data object SkipPrevious : NowPlayingUiEvent
    data class SeekTo(val positionMs: Long) : NowPlayingUiEvent
}

sealed interface NowPlayingUiEffect {
    data object NavigateBack : NowPlayingUiEffect
    data class ShowError(val message: String) : NowPlayingUiEffect
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _uiEffect = Channel<NowPlayingUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    val uiState: StateFlow<NowPlayingUiState> = playerRepository.playerState
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NowPlayingUiState.Initial,
        )

    fun onEvent(event: NowPlayingUiEvent) {
        when (event) {
            NowPlayingUiEvent.ObservePlayerState -> observePlayerState()
            is NowPlayingUiEvent.PlayPause -> playerRepository.playPause()
            is NowPlayingUiEvent.SkipNext -> playerRepository.skipNext()
            is NowPlayingUiEvent.SkipPrevious -> playerRepository.skipPrevious()
            is NowPlayingUiEvent.SeekTo -> playerRepository.seekTo(event.positionMs)
        }
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            playerRepository.playerState.collect { state ->
                if (state.error != null) {
                    _uiEffect.send(NowPlayingUiEffect.ShowError(state.error))
                }
            }
        }
    }

    private fun PlayerState.toUiState(): NowPlayingUiState {
        if (error != null) return NowPlayingUiState.Error(error)
        val track = this.track ?: return NowPlayingUiState.Initial
        return NowPlayingUiState.Playing(
            trackTitle = track.title,
            artistName = track.artistName,
            albumArtUrl = track.imageUrl,
            isPlaying = isPlaying,
            progressMs = currentPositionMs,
            durationMs = durationMs,
        )
    }
}
