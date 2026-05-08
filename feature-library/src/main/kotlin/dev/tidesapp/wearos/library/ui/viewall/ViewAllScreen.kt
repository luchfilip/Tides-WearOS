package dev.tidesapp.wearos.library.ui.viewall

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.ViewAllPage
import dev.tidesapp.wearos.core.ui.components.ErrorScreen
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import dev.tidesapp.wearos.core.ui.components.TidesChip
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ViewAllScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToMix: (mixId: String, title: String, subTitle: String?, imageUrl: String?) -> Unit,
    viewModel: ViewAllViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("tap_item", "Tap item by index (0-based)") {
            param("index", "string", "Item index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                if (state is ViewAllUiState.Success && idx in state.page.items.indices) {
                    viewModel.onEvent(ViewAllUiEvent.ItemClicked(state.page.items[idx]))
                }
                null
            }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("screen_state").semantics {
        text = AnnotatedString(when (uiState) {
            ViewAllUiState.Initial -> "initial"
            is ViewAllUiState.Loading -> "loading"
            is ViewAllUiState.Success -> "success"
            is ViewAllUiState.Error -> "error"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("title").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is ViewAllUiState.Success -> state.page.title
            is ViewAllUiState.Loading -> state.headerTitle
            is ViewAllUiState.Error -> state.headerTitle
            else -> ""
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("item_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is ViewAllUiState.Success -> state.page.items.size.toString()
            else -> "0"
        })
    })

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

    ScreenScaffold(scrollState = columnState) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
        val subtitle = page.subtitle
        if (!subtitle.isNullOrBlank()) {
            item {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
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
