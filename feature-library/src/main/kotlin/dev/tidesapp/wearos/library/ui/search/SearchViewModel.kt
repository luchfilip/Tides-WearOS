package dev.tidesapp.wearos.library.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidesapp.wearos.core.domain.model.SearchResult
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface SearchUiState {
    data object Initial : SearchUiState
    data object Loading : SearchUiState
    data class Success(val result: SearchResult) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

sealed class SearchResultType {
    data class Album(val albumId: String) : SearchResultType()
    data class Playlist(val playlistId: String) : SearchResultType()
    data class Track(val track: TrackItem) : SearchResultType()
    data class Artist(val artistId: String) : SearchResultType()
}

@Immutable
sealed interface SearchUiEvent {
    data class Search(val query: String) : SearchUiEvent
    data class ResultClicked(val resultType: SearchResultType) : SearchUiEvent
    data object ClearSearch : SearchUiEvent
}

@Immutable
sealed interface SearchUiEffect {
    data class NavigateToAlbumDetail(val albumId: String) : SearchUiEffect
    data class NavigateToPlaylistDetail(val playlistId: String) : SearchUiEffect
    data object NavigateToNowPlaying : SearchUiEffect
    data class ShowError(val message: String) : SearchUiEffect
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val playbackControl: PlaybackControl,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<SearchUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.Search -> search(event.query)
            is SearchUiEvent.ResultClicked -> onResultClicked(event.resultType)
            SearchUiEvent.ClearSearch -> clearSearch()
        }
    }

    private fun search(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading

            searchRepository.search(query)
                .onSuccess { result ->
                    val hasResults = result.albums.isNotEmpty() ||
                        result.tracks.isNotEmpty() ||
                        result.playlists.isNotEmpty() ||
                        result.artists.isNotEmpty()

                    _uiState.value = if (hasResults) {
                        SearchUiState.Success(result)
                    } else {
                        SearchUiState.Empty
                    }
                }
                .onFailure { error ->
                    _uiState.value = SearchUiState.Error(
                        error.message ?: "Search failed"
                    )
                }
        }
    }

    private fun onResultClicked(resultType: SearchResultType) {
        viewModelScope.launch {
            when (resultType) {
                is SearchResultType.Album ->
                    _uiEffect.send(SearchUiEffect.NavigateToAlbumDetail(resultType.albumId))

                is SearchResultType.Playlist ->
                    _uiEffect.send(SearchUiEffect.NavigateToPlaylistDetail(resultType.playlistId))

                is SearchResultType.Track ->
                    playbackControl.playTracks(listOf(resultType.track), startIndex = 0)
                        .onSuccess { _uiEffect.send(SearchUiEffect.NavigateToNowPlaying) }
                        .onFailure { error ->
                            _uiEffect.send(
                                SearchUiEffect.ShowError(
                                    error.message ?: "Failed to start playback",
                                ),
                            )
                        }

                is SearchResultType.Artist -> { /* TODO: Navigate to artist detail */ }
            }
        }
    }

    private fun clearSearch() {
        _uiState.value = SearchUiState.Initial
    }
}
