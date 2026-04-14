package dev.tidesapp.wearos.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.di.IoDispatcher
import dev.tidesapp.wearos.player.data.PlayerRepositoryImpl
import dev.tidesapp.wearos.player.data.api.TidesPlaybackApi
import dev.tidesapp.wearos.player.data.dto.BtsManifest
import dev.tidesapp.wearos.player.service.WearMusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlaybackController @Inject constructor(
    private val playbackApi: TidesPlaybackApi,
    private val credentialsProvider: CredentialsProvider,
    private val playerRepository: PlayerRepositoryImpl,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var mediaController: MediaController? = null
    private val controllerMutex = Mutex()

    suspend fun playTrack(trackId: String) {
        val mediaItem = withContext(ioDispatcher) {
            val token = getBearerToken()

            val playbackInfo = playbackApi.getTrackPlaybackInfo(token, trackId)
            val trackDetail = try {
                playbackApi.getTrack(token, trackId)
            } catch (_: Exception) {
                null
            }

            val artworkUri = trackDetail?.album?.cover?.let { cover ->
                val path = cover.replace("-", "/")
                Uri.parse("https://resources.tidal.com/images/$path/320x320.jpg")
            }

            val isDash = playbackInfo.manifestMimeType.contains("dash", ignoreCase = true)

            val mediaItemBuilder = MediaItem.Builder()
                .setMediaId(trackId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(trackDetail?.title ?: "Track $trackId")
                        .setArtist(
                            trackDetail?.artist?.name
                                ?: trackDetail?.artists?.firstOrNull()?.name
                                ?: "",
                        )
                        .setAlbumTitle(trackDetail?.album?.title ?: "")
                        .setArtworkUri(artworkUri)
                        .build(),
                )

            if (isDash) {
                // DASH: pass raw base64 MPD via data URI, ExoPlayer parses it for CDN segment URLs
                mediaItemBuilder
                    .setUri("data:application/dash+xml;base64,${playbackInfo.manifest}")
                    .setMimeType("application/dash+xml")
            } else {
                // BTS/EMU: manifest JSON contains direct audio URLs
                val audioUrl = decodeManifest(playbackInfo.manifest, playbackInfo.manifestMimeType)
                mediaItemBuilder.setUri(audioUrl)
            }

            mediaItemBuilder.build()
        }

        withContext(Dispatchers.Main) {
            val controller = ensureController()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    /**
     * Returns the cached [MediaController], building it lazily on first call.
     *
     * Binding a [MediaController] to the [WearMusicService] session is what causes Media3 to
     * promote the service to foreground and invoke the notification provider. Must be called
     * from the main thread — Media3 requires [MediaController.Builder] to be used there.
     */
    internal suspend fun ensureController(): MediaController {
        mediaController?.let { return it }
        return controllerMutex.withLock {
            mediaController?.let { return@withLock it }
            val controller = buildController()
            mediaController = controller
            playerRepository.setPlayer(controller)
            controller
        }
    }

    private suspend fun buildController(): MediaController =
        suspendCancellableCoroutine { cont ->
            val sessionToken = SessionToken(
                context,
                ComponentName(context, WearMusicService::class.java),
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Throwable) {
                        cont.resumeWithException(e)
                    }
                },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation { future.cancel(true) }
        }

    private suspend fun getBearerToken(): String {
        val result = credentialsProvider.getCredentials(null)
        val token = result.successData?.token
            ?: throw RuntimeException("Failed to obtain credentials")
        return "Bearer $token"
    }

    private fun decodeManifest(manifest: String, mimeType: String): String {
        val decoded = String(Base64.decode(manifest, Base64.DEFAULT))
        return when {
            mimeType.contains("bts", ignoreCase = true) ||
                mimeType.contains("emu", ignoreCase = true) -> {
                val bts = json.decodeFromString<BtsManifest>(decoded)
                bts.urls.firstOrNull()
                    ?: throw RuntimeException("No audio URL in manifest")
            }
            else -> {
                try {
                    val bts = json.decodeFromString<BtsManifest>(decoded)
                    bts.urls.firstOrNull() ?: decoded
                } catch (_: Exception) {
                    decoded
                }
            }
        }
    }
}
