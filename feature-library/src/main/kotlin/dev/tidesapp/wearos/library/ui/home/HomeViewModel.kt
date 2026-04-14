package dev.tidesapp.wearos.library.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.library.domain.repository.HomeRepository
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
sealed interface HomeUiState {
    data object Initial : HomeUiState
    data object Loading : HomeUiState
    data class Success(
        val feedSections: ImmutableList<HomeFeedSection>,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@Immutable
sealed interface HomeUiEvent {
    data object LoadHome : HomeUiEvent
    data class FeedItemClicked(val item: HomeFeedItem) : HomeUiEvent
}

@Immutable
sealed interface HomeUiEffect {
    data class NavigateToAlbum(val albumId: String) : HomeUiEffect
    data class NavigateToPlaylist(val playlistId: String) : HomeUiEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<HomeUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.LoadHome -> loadHome()
            is HomeUiEvent.FeedItemClicked -> navigateToFeedItem(event.item)
        }
    }

    private fun loadHome() {
        viewModelScope.launch {
            if (_uiState.value is HomeUiState.Loading) return@launch

            _uiState.value = HomeUiState.Loading

            homeRepository.getHomeFeed()
                .onSuccess { sections ->
                    _uiState.value = HomeUiState.Success(
                        feedSections = sections.toImmutableList(),
                    )
                }
                .onFailure {
                    _uiState.value = HomeUiState.Error(
                        message = it.message ?: "Failed to load home feed",
                    )
                }
        }
    }

    private fun navigateToFeedItem(item: HomeFeedItem) {
        viewModelScope.launch {
            when (item) {
                is HomeFeedItem.Album -> _uiEffect.send(HomeUiEffect.NavigateToAlbum(item.id))
                is HomeFeedItem.Playlist -> _uiEffect.send(HomeUiEffect.NavigateToPlaylist(item.id))
                is HomeFeedItem.Mix -> {} // Mixes not navigable yet
            }
        }
    }
}
