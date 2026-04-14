package dev.tidesapp.wearos.library.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.ui.components.TidesChip

@Composable
fun LibraryHubScreen(
    onNavigateToPlaylists: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToTracks: () -> Unit,
    onNavigateToRecent: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: LibraryHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryHubContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                LibraryHubUiEffect.NavigateToPlaylists -> onNavigateToPlaylists()
                LibraryHubUiEffect.NavigateToAlbums -> onNavigateToAlbums()
                LibraryHubUiEffect.NavigateToTracks -> onNavigateToTracks()
                LibraryHubUiEffect.NavigateToRecent -> onNavigateToRecent()
                LibraryHubUiEffect.NavigateToSettings -> onNavigateToSettings()
            }
        }
    }
}

@Composable
private fun LibraryHubContent(
    uiState: LibraryHubUiState,
    onEvent: (LibraryHubUiEvent) -> Unit,
) {
    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Chip,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        ),
    )

    when (uiState) {
        is LibraryHubUiState.Loaded -> {
            val contentItems = uiState.items.filter { it != LibraryItem.Settings }
            val accountItems = uiState.items.filter { it == LibraryItem.Settings }

            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                columnState = columnState,
            ) {
                items(contentItems.size) { index ->
                    val item = contentItems[index]
                    TidesChip(
                        label = item.label,
                        onClick = { onEvent(LibraryHubUiEvent.ItemClicked(item)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (accountItems.isNotEmpty()) {
                    item {
                        ListHeader {
                            Text(text = "Account")
                        }
                    }
                    items(accountItems.size) { index ->
                        val item = accountItems[index]
                        TidesChip(
                            label = item.label,
                            onClick = { onEvent(LibraryHubUiEvent.ItemClicked(item)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
