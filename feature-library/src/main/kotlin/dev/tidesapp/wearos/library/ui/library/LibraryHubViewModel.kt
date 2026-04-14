package dev.tidesapp.wearos.library.ui.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface LibraryHubUiState {
    data class Loaded(val items: ImmutableList<LibraryItem>) : LibraryHubUiState
}

@Immutable
sealed interface LibraryHubUiEvent {
    data class ItemClicked(val item: LibraryItem) : LibraryHubUiEvent
}

@Immutable
sealed interface LibraryHubUiEffect {
    data object NavigateToPlaylists : LibraryHubUiEffect
    data object NavigateToAlbums : LibraryHubUiEffect
    data object NavigateToTracks : LibraryHubUiEffect
    data object NavigateToRecent : LibraryHubUiEffect
    data object NavigateToSettings : LibraryHubUiEffect
}

@HiltViewModel
class LibraryHubViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryHubUiState>(
        LibraryHubUiState.Loaded(
            items = persistentListOf(
                LibraryItem.Playlists,
                LibraryItem.Albums,
                LibraryItem.Tracks,
                LibraryItem.Recent,
                LibraryItem.Settings,
            ),
        ),
    )
    val uiState: StateFlow<LibraryHubUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<LibraryHubUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onEvent(event: LibraryHubUiEvent) {
        when (event) {
            is LibraryHubUiEvent.ItemClicked -> onItemClicked(event.item)
        }
    }

    private fun onItemClicked(item: LibraryItem) {
        viewModelScope.launch {
            val effect = when (item) {
                LibraryItem.Playlists -> LibraryHubUiEffect.NavigateToPlaylists
                LibraryItem.Albums -> LibraryHubUiEffect.NavigateToAlbums
                LibraryItem.Tracks -> LibraryHubUiEffect.NavigateToTracks
                LibraryItem.Recent -> LibraryHubUiEffect.NavigateToRecent
                LibraryItem.Settings -> LibraryHubUiEffect.NavigateToSettings
            }
            _uiEffect.send(effect)
        }
    }
}
