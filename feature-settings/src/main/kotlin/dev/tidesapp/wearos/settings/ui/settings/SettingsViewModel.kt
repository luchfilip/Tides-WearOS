package dev.tidesapp.wearos.settings.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidesapp.wearos.core.domain.model.AudioQualityPreference
import dev.tidesapp.wearos.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface SettingsUiState {
    data object Initial : SettingsUiState
    data class Loaded(
        val quality: AudioQualityPreference,
        val wifiOnly: Boolean,
        val storageUsed: String,
    ) : SettingsUiState
}

@Immutable
sealed interface SettingsUiEvent {
    data object LoadSettings : SettingsUiEvent
    data class ChangeQuality(val quality: AudioQualityPreference) : SettingsUiEvent
    data object ToggleWifiOnly : SettingsUiEvent
    data object Logout : SettingsUiEvent
}

@Immutable
sealed interface SettingsUiEffect {
    data object NavigateToLogin : SettingsUiEffect
    data class ShowError(val message: String) : SettingsUiEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Initial)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<SettingsUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            SettingsUiEvent.LoadSettings -> loadSettings()
            is SettingsUiEvent.ChangeQuality -> changeQuality(event.quality)
            SettingsUiEvent.ToggleWifiOnly -> toggleWifiOnly()
            SettingsUiEvent.Logout -> logout()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                combine(
                    settingsRepository.getAudioQuality(),
                    settingsRepository.isWifiOnly(),
                ) { quality, wifiOnly ->
                    SettingsUiState.Loaded(
                        quality = quality,
                        wifiOnly = wifiOnly,
                        storageUsed = settingsRepository.getStorageUsed(),
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiEffect.send(
                    SettingsUiEffect.ShowError(e.message ?: "Failed to load settings"),
                )
            }
        }
    }

    private fun changeQuality(quality: AudioQualityPreference) {
        viewModelScope.launch {
            settingsRepository.setAudioQuality(quality)
        }
    }

    private fun toggleWifiOnly() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is SettingsUiState.Loaded) {
                settingsRepository.setWifiOnly(!currentState.wifiOnly)
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            _uiEffect.send(SettingsUiEffect.NavigateToLogin)
        }
    }
}
