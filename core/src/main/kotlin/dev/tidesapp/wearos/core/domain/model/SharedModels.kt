package dev.tidesapp.wearos.core.domain.model

enum class AudioQualityPreference {
    LOW,
    HIGH,
    LOSSLESS;

    companion object {
        val DEFAULT = HIGH
    }
}

enum class StreamingMode {
    STREAMING,
    OFFLINE;
}

enum class NetworkState {
    WIFI,
    CELLULAR,
    OFFLINE;
}

data class PlaybackContext(
    val trackId: String,
    val sourceType: PlaybackSourceType,
    val sourceId: String,
)

enum class PlaybackSourceType {
    ALBUM,
    PLAYLIST,
    SEARCH,
    FAVORITES,
}
