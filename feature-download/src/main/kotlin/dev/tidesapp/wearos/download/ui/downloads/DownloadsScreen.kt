package dev.tidesapp.wearos.download.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil3.compose.AsyncImage
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.tidesapp.wearos.core.ui.components.LoadingScreen
import com.flintsdk.Flint
import com.flintsdk.semantics.flintContent
import dev.tidesapp.wearos.download.domain.model.CollectionType
import dev.tidesapp.wearos.download.domain.model.DownloadState
import dev.tidesapp.wearos.download.domain.model.DownloadedCollection
import dev.tidesapp.wearos.download.domain.model.StorageInfo
import kotlinx.collections.immutable.ImmutableList

@Composable
fun DownloadsScreen(
    onNavigateToCollection: (String, CollectionType) -> Unit,
    onNavigateToDownloadManager: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("tap_collection", "Tap downloaded collection by index (0-based)") {
            param("index", "string", "Collection index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                if (state is DownloadsUiState.Success && idx in state.collections.indices) {
                    viewModel.onEvent(DownloadsUiEvent.CollectionClicked(state.collections[idx]))
                }
                null
            }
        }
        tool("manage_storage", "Go to storage management") {
            action { viewModel.onEvent(DownloadsUiEvent.ManageStorageClicked); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("collection_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is DownloadsUiState.Success -> state.collections.size.toString()
            else -> "0"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("storage_used").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is DownloadsUiState.Success -> state.storageInfo.usedBytes.toString()
            is DownloadsUiState.Empty -> state.storageInfo.usedBytes.toString()
            else -> "0"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("storage_limit").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is DownloadsUiState.Success -> state.storageInfo.limitBytes.toString()
            is DownloadsUiState.Empty -> state.storageInfo.limitBytes.toString()
            else -> "0"
        })
    })

    DownloadsContent(uiState = uiState, onEvent = viewModel::onEvent)

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is DownloadsUiEffect.NavigateToCollection ->
                    onNavigateToCollection(effect.collectionId, effect.type)

                DownloadsUiEffect.NavigateToDownloadManager ->
                    onNavigateToDownloadManager()
            }
        }
    }
}

@Composable
private fun DownloadsContent(
    uiState: DownloadsUiState,
    onEvent: (DownloadsUiEvent) -> Unit,
) {
    when (uiState) {
        DownloadsUiState.Loading -> LoadingScreen()

        is DownloadsUiState.Empty -> EmptyDownloads(
            storageInfo = uiState.storageInfo,
            onEvent = onEvent,
        )

        is DownloadsUiState.Success -> DownloadsList(
            collections = uiState.collections,
            storageInfo = uiState.storageInfo,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun EmptyDownloads(
    storageInfo: StorageInfo,
    onEvent: (DownloadsUiEvent) -> Unit,
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
                text = "Downloads",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
            )
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No downloads yet",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Text(
                text = "Storage: ${formatBytes(storageInfo.usedBytes)} / ${formatBytes(storageInfo.limitBytes)}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        item {
            CompactChip(
                onClick = { onEvent(DownloadsUiEvent.ManageStorageClicked) },
                label = { Text("Manage Storage") },
            )
        }
    }
    }
}

@Composable
private fun DownloadsList(
    collections: ImmutableList<DownloadedCollection>,
    storageInfo: StorageInfo,
    onEvent: (DownloadsUiEvent) -> Unit,
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
                text = "Downloads",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
            )
        }
        items(collections.size) { index ->
            val collection = collections[index]
            CollectionChip(
                collection = collection,
                onClick = { onEvent(DownloadsUiEvent.CollectionClicked(collection)) },
            )
        }
        item {
            Text(
                text = "Storage: ${formatBytes(storageInfo.usedBytes)} / ${formatBytes(storageInfo.limitBytes)}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        item {
            CompactChip(
                onClick = { onEvent(DownloadsUiEvent.ManageStorageClicked) },
                label = { Text("Manage Storage") },
            )
        }
    }
    }
}

@Composable
private fun CollectionChip(
    collection: DownloadedCollection,
    onClick: () -> Unit,
) {
    val secondaryText = when (collection.state) {
        DownloadState.DOWNLOADING ->
            "Downloading ${collection.downloadedTrackCount}/${collection.trackCount}"

        DownloadState.PENDING ->
            "Pending"

        DownloadState.FAILED ->
            "Failed"

        DownloadState.EXPIRED ->
            "Expired"

        DownloadState.COMPLETED ->
            "${collection.downloadedTrackCount}/${collection.trackCount} tracks"
    }

    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.button,
                maxLines = 1,
            )
        },
        secondaryLabel = {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        },
        icon = {
            AsyncImage(
                model = collection.imageUrl,
                contentDescription = collection.title,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
