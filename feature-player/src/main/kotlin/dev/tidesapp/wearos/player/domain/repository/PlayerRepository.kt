package dev.tidesapp.wearos.player.domain.repository

import dev.tidesapp.wearos.player.domain.model.NowPlayingInfo
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val track: NowPlayingInfo? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
)

interface PlayerRepository {
    val playerState: StateFlow<PlayerState>
    fun playPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
}
