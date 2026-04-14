package dev.tidesapp.wearos.player.service

import android.content.Intent
import android.util.Base64
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.tidal.sdk.auth.CredentialsProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.tidesapp.wearos.core.di.IoDispatcher
import dev.tidesapp.wearos.player.R
import dev.tidesapp.wearos.player.data.api.TidesPlaybackApi
import dev.tidesapp.wearos.player.data.dto.BtsManifest
import dev.tidesapp.wearos.player.playback.MusicServiceController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WearMusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var serviceController: MusicServiceController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callbackScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WearMusicServiceEntryPoint::class.java,
        )
        val playbackApi = entryPoint.tidesPlaybackApi()
        val credentialsProvider = entryPoint.credentialsProvider()
        val ioDispatcher = entryPoint.ioDispatcher()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer

        val resolverScope = CoroutineScope(SupervisorJob() + ioDispatcher)
        callbackScope = resolverScope

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(
                TrackResolvingCallback(
                    playbackApi = playbackApi,
                    credentialsProvider = credentialsProvider,
                    scope = resolverScope,
                ),
            )
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.player_notification_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build(),
        )

        serviceController = MusicServiceController(serviceScope)
        serviceController.setStopCallback { stopSelf() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceController.onTaskRemoved()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceController.onDestroy()
        callbackScope?.cancel()
        callbackScope = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        serviceScope.cancel()
        super.onDestroy()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WearMusicServiceEntryPoint {
        fun tidesPlaybackApi(): TidesPlaybackApi
        fun credentialsProvider(): CredentialsProvider

        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "dev.tidesapp.wearos.player.playback"
        const val NOTIFICATION_ID = 1001
    }
}

/**
 * [MediaSession.Callback] that resolves each incoming stub [MediaItem]'s
 * playback URI just-in-time by calling [TidesPlaybackApi.getTrackPlaybackInfo].
 *
 * Stubs are built by the app side (`PlaybackController.playTracks`) and carry
 * only the TIDAL `mediaId` + display metadata. Resolution happens once per
 * item, asynchronously, off the main thread. If a given track fails to
 * resolve (401, 404, network), we return the untouched stub so Media3 skips
 * it rather than failing the entire queue-add.
 */
private class TrackResolvingCallback(
    private val playbackApi: TidesPlaybackApi,
    private val credentialsProvider: CredentialsProvider,
    private val scope: CoroutineScope,
) : MediaSession.Callback {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        val future: SettableFuture<List<MediaItem>> = SettableFuture.create()
        scope.launch {
            val resolved = mediaItems.map { stub ->
                runCatching { resolve(stub) }.getOrElse { stub }
            }
            future.set(resolved)
        }
        return future
    }

    private suspend fun resolve(stub: MediaItem): MediaItem {
        val trackId = stub.mediaId
        if (trackId.isBlank()) return stub

        val token = getBearerToken()
        val playbackInfo = playbackApi.getTrackPlaybackInfo(token, trackId)
        val isDash = playbackInfo.manifestMimeType.contains("dash", ignoreCase = true)

        val builder = stub.buildUpon()
        return if (isDash) {
            // DASH: pass the raw base64 MPD via data URI; ExoPlayer parses it
            // and pulls the CDN segment URLs from there.
            builder
                .setUri("data:application/dash+xml;base64,${playbackInfo.manifest}")
                .setMimeType("application/dash+xml")
                .build()
        } else {
            val audioUrl = decodeManifest(
                manifest = playbackInfo.manifest,
                mimeType = playbackInfo.manifestMimeType,
            )
            builder.setUri(audioUrl).build()
        }
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
