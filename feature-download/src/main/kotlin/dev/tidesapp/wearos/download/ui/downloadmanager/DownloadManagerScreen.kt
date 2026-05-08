package dev.tidesapp.wearos.download.ui.downloadmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
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
import dev.tidesapp.wearos.download.domain.model.DownloadedCollection
import dev.tidesapp.wearos.download.domain.model.StorageInfo
import kotlinx.collections.immutable.ImmutableList

@Composable
fun DownloadManagerScreen(
    viewModel: DownloadManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Flint.tools {
        tool("delete_collection", "Delete downloaded collection by index (0-based)") {
            param("index", "string", "Collection index")
            action { params ->
                val idx = params["index"]?.toString()?.toIntOrNull() ?: return@action null
                val state = viewModel.uiState.value
                if (state is DownloadManagerUiState.Loaded && idx in state.collections.indices) {
                    viewModel.onEvent(DownloadManagerUiEvent.DeleteCollection(state.collections[idx]))
                }
                null
            }
        }
        tool("clear_all", "Clear all downloads") {
            action { viewModel.onEvent(DownloadManagerUiEvent.ClearAll); null }
        }
    }

    Box(modifier = Modifier.height(0.dp).flintContent("collection_count").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is DownloadManagerUiState.Loaded -> state.collections.size.toString()
            else -> "0"
        })
    })

    Box(modifier = Modifier.height(0.dp).flintContent("storage_used").semantics {
        text = AnnotatedString(when (val state = uiState) {
            is DownloadManagerUiState.Loaded -> state.storageInfo.usedBytes.toString()
            else -> "0"
        })
    })

    DownloadManagerContent(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
private fun DownloadManagerContent(
    uiState: DownloadManagerUiState,
    onEvent: (DownloadManagerUiEvent) -> Unit,
) {
    when (uiState) {
        DownloadManagerUiState.Loading -> LoadingScreen()

        is DownloadManagerUiState.Loaded -> DownloadManagerList(
            collections = uiState.collections,
            storageInfo = uiState.storageInfo,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun DownloadManagerList(
    collections: ImmutableList<DownloadedCollection>,
    storageInfo: StorageInfo,
    onEvent: (DownloadManagerUiEvent) -> Unit,
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
                text = "Manage Downloads",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
            )
        }
        item {
            Text(
                text = "Used: ${formatBytes(storageInfo.usedBytes)} of ${formatBytes(storageInfo.limitBytes)}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        items(collections.size) { index ->
            val collection = collections[index]
            ManageCollectionChip(
                collection = collection,
                onDelete = { onEvent(DownloadManagerUiEvent.DeleteCollection(collection)) },
            )
        }
        if (collections.isNotEmpty()) {
            item {
                CompactChip(
                    onClick = { onEvent(DownloadManagerUiEvent.ClearAll) },
                    label = {
                        Text(
                            text = "Clear All Downloads",
                            color = MaterialTheme.colors.error,
                        )
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun ManageCollectionChip(
    collection: DownloadedCollection,
    onDelete: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onDelete,
        label = {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.button,
                maxLines = 1,
            )
        },
        secondaryLabel = {
            Text(
                text = "Delete",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.error,
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
