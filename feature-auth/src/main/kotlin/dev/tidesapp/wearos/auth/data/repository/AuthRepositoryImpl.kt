package dev.tidesapp.wearos.auth.data.repository

import com.tidal.sdk.auth.Auth
import com.tidal.sdk.auth.CredentialsProvider
import com.tidal.sdk.auth.model.AuthResult
import dev.tidesapp.wearos.auth.domain.model.DeviceCodeInfo
import dev.tidesapp.wearos.auth.domain.repository.AuthRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: Auth,
    private val credentialsProvider: CredentialsProvider,
) : AuthRepository {

    private val mutex = Mutex()

    override suspend fun initializeDeviceLogin(): Result<DeviceCodeInfo> {
        return mutex.withLock {
            try {
                when (val result = auth.initializeDeviceLogin()) {
                    is AuthResult.Success -> {
                        val response = result.data
                            ?: return@withLock Result.failure(
                                RuntimeException("Empty device authorization response"),
                            )
                        Result.success(
                            DeviceCodeInfo(
                                deviceCode = response.deviceCode,
                                userCode = response.userCode,
                                verificationUri = response.verificationUri,
                                expiresIn = response.expiresIn,
                                interval = response.interval,
                            ),
                        )
                    }
                    is AuthResult.Failure -> {
                        Result.failure(
                            RuntimeException(
                                result.message?.toString() ?: "Failed to initialize device login",
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun finalizeDeviceLogin(deviceCode: String): Result<Unit> {
        return try {
            when (val result = auth.finalizeDeviceLogin(deviceCode)) {
                is AuthResult.Success -> Result.success(Unit)
                is AuthResult.Failure -> {
                    Result.failure(
                        RuntimeException(
                            result.message?.toString() ?: "Device login failed",
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        return try {
            val result = credentialsProvider.getCredentials(null)
            val credentials = result.successData
            // Client credentials (no user) also return a token — check userId to confirm user login
            credentials?.token != null && !credentials.userId.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun logout() {
        auth.logout()
    }
}
