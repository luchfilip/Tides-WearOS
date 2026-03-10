package dev.tidesapp.wearos.player.domain.model

data class NowPlayingInfo(
    val trackId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val imageUrl: String?,
    val durationMs: Long,
)
