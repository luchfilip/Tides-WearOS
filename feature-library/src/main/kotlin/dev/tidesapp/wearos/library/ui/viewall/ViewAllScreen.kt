package dev.tidesapp.wearos.library.ui.viewall

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.ViewAllPage
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import dev.tidesapp.wearos.core.ui.components.TidesChip
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ViewAllScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToMix: (mixId: String, title: String, subTitle: String?, imageUrl: String?) -> Unit,
    viewModel: ViewAllViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ViewAllContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ViewAllUiEffect.NavigateToAlbum -> onNavigateToAlbum(effect.albumId)
                is ViewAllUiEffect.NavigateToPlaylist -> onNavigateToPlaylist(effect.playlistId)
                is ViewAllUiEffect.NavigateToMix -> onNavigateToMix(
                    effect.mixId,
                    effect.title,
                    effect.subTitle,
                    effect.imageUrl,
                )
            }
        }
    }
}

@Composable
fun ViewAllContent(
    uiState: ViewAllUiState,
    onEvent: (ViewAllUiEvent) -> Unit,
) {
    when (uiState) {
        ViewAllUiState.Initial,
        is ViewAllUiState.Loading -> LoadingScreen()

        is ViewAllUiState.Success -> ViewAllList(page = uiState.page, onEvent = onEvent)

        is ViewAllUiState.Error -> ErrorScreen(
            message = uiState.message,
            onRetry = { onEvent(ViewAllUiEvent.Load) },
        )
    }
}

@Composable
private fun ViewAllList(
    page: ViewAllPage,
    onEvent: (ViewAllUiEvent) -> Unit,
) {
    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Text,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        ),
    )

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        columnState = columnState,
    ) {
        item {
            Text(
                text = page.title,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                maxLines = 2,
            )
        }
        val subtitle = page.subtitle
        if (!subtitle.isNullOrBlank()) {
            item {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }
        items(page.items.size) { index ->
            val item = page.items[index]
            TidesChip(
                label = item.title,
                secondaryLabel = when (item) {
                    is HomeFeedItem.Album -> item.artistName
                    is HomeFeedItem.Playlist -> item.creator
                    is HomeFeedItem.Mix -> item.subTitle
                },
                onClick = { onEvent(ViewAllUiEvent.ItemClicked(item)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------- Previews ----------------------------------------------------------------------------

private val samplePage = ViewAllPage(
    title = "Popular playlists",
    subtitle = "Editor's picks",
    items = persistentListOf(
        HomeFeedItem.Playlist(
            id = "p1",
            title = "Deep Focus",
            imageUrl = null,
            creator = "TIDAL",
        ),
        HomeFeedItem.Album(
            id = "a1",
            title = "Sample Album",
            imageUrl = null,
            artistName = "Sample Artist",
        ),
        HomeFeedItem.Mix(
            id = "m1",
            title = "Daily Mix 1",
            imageUrl = null,
            subTitle = "For you",
        ),
    ),
)

@Preview
@Composable
private fun ViewAllContentSuccessPreview() {
    ViewAllContent(
        uiState = ViewAllUiState.Success(samplePage),
        onEvent = {},
    )
}

@Preview
@Composable
private fun ViewAllContentLoadingPreview() {
    ViewAllContent(
        uiState = ViewAllUiState.Loading(headerTitle = "Popular playlists"),
        onEvent = {},
    )
}

@Preview
@Composable
private fun ViewAllContentErrorPreview() {
    ViewAllContent(
        uiState = ViewAllUiState.Error(
            message = "Network error",
            headerTitle = "Popular playlists",
        ),
        onEvent = {},
    )
}
