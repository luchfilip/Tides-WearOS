package dev.tidesapp.wearos.settings.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tidesapp.wearos.core.domain.model.AudioQualityPreference
import dev.tidesapp.wearos.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }

    override fun getAudioQuality(): Flow<AudioQualityPreference> =
        dataStore.data.map { preferences ->
            val name = preferences[Keys.AUDIO_QUALITY]
            name?.let {
                try {
                    AudioQualityPreference.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    AudioQualityPreference.DEFAULT
                }
            } ?: AudioQualityPreference.DEFAULT
        }

    override suspend fun setAudioQuality(quality: AudioQualityPreference) {
        dataStore.edit { preferences ->
            preferences[Keys.AUDIO_QUALITY] = quality.name
        }
    }

    override fun isWifiOnly(): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[Keys.WIFI_ONLY] ?: false
        }

    override suspend fun setWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.WIFI_ONLY] = wifiOnly
        }
    }

    override suspend fun getStorageUsed(): String = "0 MB"
}
