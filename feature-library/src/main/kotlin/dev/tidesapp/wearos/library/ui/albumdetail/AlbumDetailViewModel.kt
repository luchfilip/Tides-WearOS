package dev.tidesapp.wearos.library.ui.albumdetail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
sealed interface AlbumDetailUiState {
    data object Initial : AlbumDetailUiState
    data object Loading : AlbumDetailUiState
    data class Success(
        val album: AlbumItem,
        val tracks: ImmutableList<TrackItem>,
    ) : AlbumDetailUiState

    data class Error(val message: String) : AlbumDetailUiState
}

@Immutable
sealed interface AlbumDetailUiEvent {
    data class LoadAlbumDetail(val albumId: String) : AlbumDetailUiEvent
    data object PlayAll : AlbumDetailUiEvent
    data class PlayTrack(val track: TrackItem) : AlbumDetailUiEvent
}

@Immutable
sealed interface AlbumDetailUiEffect {
    data object NavigateToNowPlaying : AlbumDetailUiEffect
    data class ShowError(val message: String) : AlbumDetailUiEffect
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val playbackControl: PlaybackControl,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Initial)
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<AlbumDetailUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onEvent(event: AlbumDetailUiEvent) {
        when (event) {
            is AlbumDetailUiEvent.LoadAlbumDetail -> loadAlbumDetail(event.albumId)
            AlbumDetailUiEvent.PlayAll -> playFromIndex(0)
            is AlbumDetailUiEvent.PlayTrack -> playTrack(event.track)
        }
    }

    private fun loadAlbumDetail(albumId: String) {
        viewModelScope.launch {
            if (_uiState.value is AlbumDetailUiState.Loading) return@launch

            _uiState.value = AlbumDetailUiState.Loading

            val albumResult = albumRepository.getAlbumDetail(albumId)
            val tracksResult = albumRepository.getAlbumTracks(albumId)

            val album = albumResult.getOrNull()
            val tracks = tracksResult.getOrNull()

            if (album != null && tracks != null) {
                _uiState.value = AlbumDetailUiState.Success(
                    album = album,
                    tracks = tracks.toImmutableList(),
                )
            } else {
                _uiState.value = AlbumDetailUiState.Error(
                    albumResult.exceptionOrNull()?.message
                        ?: tracksResult.exceptionOrNull()?.message
                        ?: "Failed to load album"
                )
            }
        }
    }

    private fun playTrack(track: TrackItem) {
        val state = _uiState.value as? AlbumDetailUiState.Success ?: return
        val startIndex = state.tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playFromIndex(startIndex)
    }

    private fun playFromIndex(startIndex: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state !is AlbumDetailUiState.Success || state.tracks.isEmpty()) return@launch

            playbackControl.playTracks(state.tracks, startIndex)
                .onSuccess { _uiEffect.send(AlbumDetailUiEffect.NavigateToNowPlaying) }
                .onFailure { error ->
                    _uiEffect.send(
                        AlbumDetailUiEffect.ShowError(error.message ?: "Failed to start playback"),
                    )
                }
        }
    }
}
