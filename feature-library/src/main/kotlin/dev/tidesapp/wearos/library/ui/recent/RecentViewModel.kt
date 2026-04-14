package dev.tidesapp.wearos.library.ui.recent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.RecentActivityRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface RecentUiState {
    data object Initial : RecentUiState
    data object Loading : RecentUiState
    data class Success(val tracks: ImmutableList<TrackItem>) : RecentUiState
    data class Error(val message: String) : RecentUiState
}

@Immutable
sealed interface RecentUiEvent {
    data object LoadTracks : RecentUiEvent
    data object Retry : RecentUiEvent
    data class TrackClicked(val index: Int) : RecentUiEvent
}

@Immutable
sealed interface RecentUiEffect {
    data object NavigateToNowPlaying : RecentUiEffect
    data class ShowError(val message: String) : RecentUiEffect
}

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val repository: RecentActivityRepository,
    private val playbackControl: PlaybackControl,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecentUiState>(RecentUiState.Initial)
    val uiState: StateFlow<RecentUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<RecentUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: RecentUiEvent) {
        when (event) {
            RecentUiEvent.LoadTracks -> load()
            RecentUiEvent.Retry -> load()
            is RecentUiEvent.TrackClicked -> playFromIndex(event.index)
        }
    }

    private fun load() {
        viewModelScope.launch {
            if (_uiState.value is RecentUiState.Loading) return@launch
            _uiState.value = RecentUiState.Loading
            repository.getRecentTracks()
                .onSuccess { tracks ->
                    _uiState.value = RecentUiState.Success(tracks.toImmutableList())
                }
                .onFailure { error ->
                    _uiState.value = RecentUiState.Error(
                        error.message ?: "Failed to load recent tracks",
                    )
                }
        }
    }

    private fun playFromIndex(startIndex: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state !is RecentUiState.Success || state.tracks.isEmpty()) return@launch
            val index = startIndex.coerceIn(0, state.tracks.lastIndex)
            playbackControl.playTracks(state.tracks, index)
                .onSuccess { _uiEffect.send(RecentUiEffect.NavigateToNowPlaying) }
                .onFailure { error ->
                    _uiEffect.send(
                        RecentUiEffect.ShowError(error.message ?: "Failed to start playback"),
                    )
                }
        }
    }
}
