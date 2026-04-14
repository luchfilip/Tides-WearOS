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
import com.google.common.util.concurrent.Futures
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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

        val resolver = TrackResolver(
            playbackApi = playbackApi,
            credentialsProvider = credentialsProvider,
            json = Json { ignoreUnknownKeys = true },
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(
                TrackResolvingCallback(
                    resolver = resolver,
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
 * Resolves a stub [MediaItem] into a playable one by fetching a TIDAL playback
 * URL just-in-time. Plain Kotlin, no Android deps beyond [Base64] which is
 * stubbed by Android's JVM unit-test runtime.
 */
internal class TrackResolver(
    private val playbackApi: TidesPlaybackApi,
    private val credentialsProvider: CredentialsProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun resolve(stub: MediaItem): MediaItem {
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

/**
 * [MediaSession.Callback] that resolves every incoming [MediaItem]'s playback
 * URI **in parallel** inside [onSetMediaItems] before returning the future.
 *
 * Why fully resolved and not lazy? Media3's `DefaultMediaSourceFactory` builds
 * a `MediaSource` for every item in the queue at `setMediaItems` time (not
 * lazily on-demand), and NPEs on any item with a null `localConfiguration`.
 * So we MUST NOT hand it stubs — lazy/listener-based resolution is a
 * non-starter with the default source factory. Parallel resolution keeps
 * startup at ~1s for a 20-track queue instead of the ~15-20s that sequential
 * resolution produced.
 *
 * OkHttp's default `maxRequestsPerHost = 5` naturally caps concurrency against
 * the TIDAL API host, so we don't need our own semaphore.
 *
 * Items that fail to resolve are dropped from the queue rather than left as
 * stubs (which would re-trigger the source-factory NPE). The originally
 * requested `startIndex` is re-mapped to the new position of the start item
 * inside the filtered list.
 */
internal class TrackResolvingCallback(
    private val resolver: TrackResolver,
    private val scope: CoroutineScope,
) : MediaSession.Callback {

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        // The app currently always enters via setMediaItems; keep this hook
        // contract-consistent by passing items through unchanged.
        return Futures.immediateFuture(mediaItems)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future: SettableFuture<MediaSession.MediaItemsWithStartPosition> =
            SettableFuture.create()

        if (mediaItems.isEmpty()) {
            future.set(
                MediaSession.MediaItemsWithStartPosition(
                    mediaItems,
                    if (startIndex == C.INDEX_UNSET) 0 else startIndex,
                    startPositionMs,
                ),
            )
            return future
        }

        val requestedStartIndex = if (startIndex == C.INDEX_UNSET) {
            0
        } else {
            startIndex.coerceIn(0, mediaItems.lastIndex)
        }

        scope.launch {
            // Resolve all items in parallel. Failures become nulls so we can
            // drop them without leaving stubs in the queue.
            val resolved: List<MediaItem?> = coroutineScope {
                mediaItems.map { stub ->
                    async { runCatching { resolver.resolve(stub) }.getOrNull() }
                }.awaitAll()
            }

            val playable = mutableListOf<MediaItem>()
            var newStartIndex = -1
            resolved.forEachIndexed { originalIdx, item ->
                if (item != null) {
                    if (originalIdx == requestedStartIndex) {
                        newStartIndex = playable.size
                    }
                    playable.add(item)
                }
            }

            // If the originally requested start item failed, the start index
            // collapses to the first successfully resolved item (or 0 if the
            // whole list failed — Media3 will then report an empty queue).
            if (newStartIndex < 0) newStartIndex = 0

            future.set(
                MediaSession.MediaItemsWithStartPosition(
                    playable.toList(),
                    newStartIndex,
                    startPositionMs,
                ),
            )
        }

        return future
    }
}
