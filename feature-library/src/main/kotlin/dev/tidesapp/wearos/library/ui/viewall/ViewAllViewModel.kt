package dev.tidesapp.wearos.library.ui.viewall

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.ViewAllPage
import dev.tidesapp.wearos.library.domain.repository.ViewAllRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@Immutable
sealed interface ViewAllUiState {
    data object Initial : ViewAllUiState
    data class Loading(val headerTitle: String) : ViewAllUiState
    data class Success(val page: ViewAllPage) : ViewAllUiState
    data class Error(val message: String, val headerTitle: String) : ViewAllUiState
}

@Immutable
sealed interface ViewAllUiEvent {
    data object Load : ViewAllUiEvent
    data class ItemClicked(val item: HomeFeedItem) : ViewAllUiEvent
}

@Immutable
sealed interface ViewAllUiEffect {
    data class NavigateToAlbum(val albumId: String) : ViewAllUiEffect
    data class NavigateToPlaylist(val playlistId: String) : ViewAllUiEffect
    data class NavigateToMix(
        val mixId: String,
        val title: String,
        val subTitle: String?,
        val imageUrl: String?,
    ) : ViewAllUiEffect
}

@HiltViewModel
class ViewAllViewModel @Inject constructor(
    private val repository: ViewAllRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Both args arrive URL-encoded to survive the nav route template. We decode here so the
    // path can be passed verbatim to the API call (Retrofit re-encodes what it needs to) and
    // so the title renders correctly in the Initial/Loading header before the response lands.
    // Using URLDecoder instead of android.net.Uri keeps this unit-testable on the JVM.
    private val viewAllPath: String =
        decode(savedStateHandle.get<String>(ARG_PATH).orEmpty())
    private val initialTitle: String =
        decode(savedStateHandle.get<String>(ARG_TITLE).orEmpty())

    private fun decode(value: String): String =
        if (value.isEmpty()) "" else URLDecoder.decode(value, Charsets.UTF_8.name())

    private val _uiState = MutableStateFlow<ViewAllUiState>(ViewAllUiState.Initial)
    val uiState: StateFlow<ViewAllUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<ViewAllUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        // Auto-load on construction, mirroring how HomeScreen kicks off LoadHome on Initial.
        // Doing it here keeps the Composable dumb and avoids a LaunchedEffect race if the
        // screen recomposes before the effect fires.
        if (_uiState.value is ViewAllUiState.Initial) {
            loadViewAll()
        }
    }

    fun onEvent(event: ViewAllUiEvent) {
        when (event) {
            ViewAllUiEvent.Load -> loadViewAll()
            is ViewAllUiEvent.ItemClicked -> navigateToItem(event.item)
        }
    }

    private fun loadViewAll() {
        viewModelScope.launch {
            if (_uiState.value is ViewAllUiState.Loading) return@launch
            _uiState.value = ViewAllUiState.Loading(headerTitle = initialTitle)

            repository.getViewAll(viewAllPath)
                .onSuccess { page ->
                    _uiState.value = ViewAllUiState.Success(page = page)
                }
                .onFailure { error ->
                    _uiState.value = ViewAllUiState.Error(
                        message = error.message ?: "Failed to load",
                        headerTitle = initialTitle,
                    )
                }
        }
    }

    private fun navigateToItem(item: HomeFeedItem) {
        viewModelScope.launch {
            when (item) {
                is HomeFeedItem.Album ->
                    _uiEffect.send(ViewAllUiEffect.NavigateToAlbum(item.id))
                is HomeFeedItem.Playlist ->
                    _uiEffect.send(ViewAllUiEffect.NavigateToPlaylist(item.id))
                is HomeFeedItem.Mix -> _uiEffect.send(
                    ViewAllUiEffect.NavigateToMix(
                        mixId = item.id,
                        title = item.title,
                        subTitle = item.subTitle,
                        imageUrl = item.imageUrl,
                    ),
                )
            }
        }
    }

    companion object {
        const val ARG_PATH = "path"
        const val ARG_TITLE = "title"
    }
}
