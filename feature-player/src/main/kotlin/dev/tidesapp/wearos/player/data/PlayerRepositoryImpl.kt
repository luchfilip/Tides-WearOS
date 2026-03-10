package dev.tidesapp.wearos.player.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.tidesapp.wearos.player.domain.model.NowPlayingInfo
import dev.tidesapp.wearos.player.domain.repository.PlayerRepository
import dev.tidesapp.wearos.player.domain.repository.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepositoryImpl @Inject constructor() : PlayerRepository {

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var player: Player? = null
    private val _playerAvailable = MutableStateFlow<Player?>(null)

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playerState.value = _playerState.value.copy(
                error = error.message ?: "Playback error",
            )
        }
    }

    fun setPlayer(player: Player) {
        this.player?.removeListener(playerListener)
        this.player = player
        player.addListener(playerListener)
        _playerAvailable.value = player
        updateState()
    }

    fun clearPlayer() {
        player?.removeListener(playerListener)
        player = null
        _playerAvailable.value = null
        _playerState.value = PlayerState()
    }

    suspend fun awaitPlayer(): Player {
        return _playerAvailable.filterNotNull().first()
    }

    override fun playPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    override fun skipNext() {
        player?.seekToNextMediaItem()
    }

    override fun skipPrevious() {
        player?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    private fun updateState() {
        val p = player ?: return
        val mediaItem = p.currentMediaItem
        _playerState.value = PlayerState(
            track = mediaItem?.let {
                NowPlayingInfo(
                    trackId = it.mediaId,
                    title = it.mediaMetadata.title?.toString() ?: "",
                    artistName = it.mediaMetadata.artist?.toString() ?: "",
                    albumTitle = it.mediaMetadata.albumTitle?.toString() ?: "",
                    imageUrl = it.mediaMetadata.artworkUri?.toString(),
                    durationMs = p.duration.coerceAtLeast(0),
                )
            },
            isPlaying = p.isPlaying,
            currentPositionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.coerceAtLeast(0),
        )
    }
}
