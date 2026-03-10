package dev.tidesapp.wearos.settings.domain.repository

import dev.tidesapp.wearos.core.domain.model.AudioQualityPreference
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getAudioQuality(): Flow<AudioQualityPreference>
    suspend fun setAudioQuality(quality: AudioQualityPreference)
    fun isWifiOnly(): Flow<Boolean>
    suspend fun setWifiOnly(wifiOnly: Boolean)
    suspend fun getStorageUsed(): String
}
