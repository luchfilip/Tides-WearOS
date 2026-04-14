package dev.tidesapp.wearos.library.ui.tracks

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.TrackFavoritesRepository
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
sealed interface TracksUiState {
    data object Initial : TracksUiState
    data object Loading : TracksUiState
    data class Success(val tracks: ImmutableList<TrackItem>) : TracksUiState
    data class Error(val message: String) : TracksUiState
}

@Immutable
sealed interface TracksUiEvent {
    data object LoadTracks : TracksUiEvent
    data object Retry : TracksUiEvent
    data class TrackClicked(val index: Int) : TracksUiEvent
}

@Immutable
sealed interface TracksUiEffect {
    data object NavigateToNowPlaying : TracksUiEffect
    data class ShowError(val message: String) : TracksUiEffect
}

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val repository: TrackFavoritesRepository,
    private val playbackControl: PlaybackControl,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TracksUiState>(TracksUiState.Initial)
    val uiState: StateFlow<TracksUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<TracksUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        load(forceRefresh = false)
    }

    fun onEvent(event: TracksUiEvent) {
        when (event) {
            TracksUiEvent.LoadTracks -> load(forceRefresh = false)
            TracksUiEvent.Retry -> load(forceRefresh = true)
            is TracksUiEvent.TrackClicked -> playFromIndex(event.index)
        }
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            if (_uiState.value is TracksUiState.Loading) return@launch
            _uiState.value = TracksUiState.Loading
            repository.getUserFavoriteTracks(forceRefresh)
                .onSuccess { tracks ->
                    _uiState.value = TracksUiState.Success(tracks.toImmutableList())
                }
                .onFailure { error ->
                    _uiState.value = TracksUiState.Error(
                        error.message ?: "Failed to load tracks",
                    )
                }
        }
    }

    private fun playFromIndex(startIndex: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state !is TracksUiState.Success || state.tracks.isEmpty()) return@launch
            val index = startIndex.coerceIn(0, state.tracks.lastIndex)
            playbackControl.playTracks(state.tracks, index)
                .onSuccess { _uiEffect.send(TracksUiEffect.NavigateToNowPlaying) }
                .onFailure { error ->
                    _uiEffect.send(
                        TracksUiEffect.ShowError(error.message ?: "Failed to start playback"),
                    )
                }
        }
    }
}
