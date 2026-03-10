package dev.tidesapp.wearos.auth.domain.repository

import dev.tidesapp.wearos.auth.domain.model.DeviceCodeInfo

interface AuthRepository {
    suspend fun initializeDeviceLogin(): Result<DeviceCodeInfo>
    suspend fun finalizeDeviceLogin(deviceCode: String): Result<Unit>
    suspend fun isLoggedIn(): Boolean
    suspend fun logout()
}
