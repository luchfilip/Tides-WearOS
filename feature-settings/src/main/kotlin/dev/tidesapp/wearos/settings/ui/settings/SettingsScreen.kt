package dev.tidesapp.wearos.settings.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import dev.tidesapp.wearos.core.domain.model.AudioQualityPreference
import dev.tidesapp.wearos.core.ui.components.LoadingScreen

@Composable
fun SettingsScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState is SettingsUiState.Initial) {
            viewModel.onEvent(SettingsUiEvent.LoadSettings)
        }
    }

    SettingsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SettingsUiEffect.NavigateToLogin -> onNavigateToLogin()
                is SettingsUiEffect.ShowError -> { /* handled via state */ }
            }
        }
    }
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is SettingsUiState.Initial -> LoadingScreen(modifier)
        is SettingsUiState.Loaded -> LoadedContent(
            state = uiState,
            onEvent = onEvent,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadedContent(
    state: SettingsUiState.Loaded,
    onEvent: (SettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScalingLazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            ListHeader {
                Text("Settings")
            }
        }

        item {
            Chip(
                onClick = {
                    onEvent(SettingsUiEvent.ChangeQuality(state.quality.next()))
                },
                label = { Text("Audio Quality") },
                secondaryLabel = { Text(state.quality.name) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            ToggleChip(
                checked = state.wifiOnly,
                onCheckedChange = { onEvent(SettingsUiEvent.ToggleWifiOnly) },
                label = { Text("WiFi Only") },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(checked = state.wifiOnly),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "Storage: ${state.storageUsed}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Chip(
                onClick = { onEvent(SettingsUiEvent.Logout) },
                label = { Text("Logout") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun AudioQualityPreference.next(): AudioQualityPreference {
    val values = AudioQualityPreference.entries
    return values[(ordinal + 1) % values.size]
}
