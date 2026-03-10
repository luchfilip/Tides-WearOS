package dev.tidesapp.wearos.auth.ui.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tidesapp.wearos.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface LoginUiState {
    data object Initial : LoginUiState
    data object Loading : LoginUiState
    data class ShowingCode(
        val userCode: String,
        val verificationUri: String,
    ) : LoginUiState
    data object Polling : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@Immutable
sealed interface LoginUiEvent {
    data object StartLogin : LoginUiEvent
    data object RetryLogin : LoginUiEvent
}

@Immutable
sealed interface LoginUiEffect {
    data object NavigateToHome : LoginUiEffect
    data class ShowError(val message: String) : LoginUiEffect
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<LoginUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            LoginUiEvent.StartLogin -> startLogin()
            LoginUiEvent.RetryLogin -> startLogin()
        }
    }

    private fun startLogin() {
        viewModelScope.launch {
            when (_uiState.value) {
                is LoginUiState.Loading,
                is LoginUiState.ShowingCode,
                is LoginUiState.Polling,
                -> return@launch

                is LoginUiState.Initial,
                is LoginUiState.Success,
                is LoginUiState.Error,
                -> _uiState.value = LoginUiState.Loading
            }

            authRepository.initializeDeviceLogin()
                .onSuccess { deviceCodeInfo ->
                    _uiState.value = LoginUiState.ShowingCode(
                        userCode = deviceCodeInfo.userCode,
                        verificationUri = deviceCodeInfo.verificationUri,
                    )
                    pollForLogin(deviceCodeInfo.deviceCode)
                }
                .onFailure { error ->
                    _uiState.value = LoginUiState.Error(
                        error.message ?: "Failed to start login",
                    )
                }
        }
    }

    private fun pollForLogin(deviceCode: String) {
        viewModelScope.launch {
            authRepository.finalizeDeviceLogin(deviceCode)
                .onSuccess {
                    _uiState.value = LoginUiState.Success
                    _uiEffect.send(LoginUiEffect.NavigateToHome)
                }
                .onFailure { error ->
                    _uiState.value = LoginUiState.Error(
                        error.message ?: "Login failed",
                    )
                }
        }
    }
}
