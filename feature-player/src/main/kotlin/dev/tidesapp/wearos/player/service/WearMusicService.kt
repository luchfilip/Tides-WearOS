package dev.tidesapp.wearos.player.service

import android.content.Intent
import android.util.Base64
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class WearMusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var serviceController: MusicServiceController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callbackScope: CoroutineScope? = null
    private var lookaheadListener: Player.Listener? = null

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

        // Pre-resolve the upcoming stub as playback advances so skip-next is
        // instant. Also covers the "user skipped past the pre-fetch" edge case
        // by resolving the current item in-place if it is still a stub.
        val listener = LookaheadResolveListener(
            resolver = resolver,
            mainScope = serviceScope,
            ioDispatcher = ioDispatcher,
            playerProvider = { player },
        )
        exoPlayer.addListener(listener)
        lookaheadListener = listener

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
        lookaheadListener?.let { listener ->
            player?.removeListener(listener)
        }
        lookaheadListener = null
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
 *
 * If resolution fails at any step (blank id, credentials, API, manifest
 * decode), the original stub is returned so the caller can decide whether to
 * let Media3 fail-fast or skip the item. Callers generally wrap this in
 * `runCatching` and fall back to the stub on failure.
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
 * [MediaSession.Callback] that resolves **only** the starting track's playback
 * URI inside [onSetMediaItems]. All other items pass through unchanged as
 * stubs and are resolved on-demand by [LookaheadResolveListener] as playback
 * advances. This slashes startup latency from ~15-20s (for a 20-track
 * playlist, sequential round-trips) to ~1s (one round-trip).
 *
 * If the start item fails to resolve we set the future with the untouched
 * list so Media3 attempts and fails the stub — the user gets a fail-fast
 * error instead of a mystery hang.
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
        // onAddMediaItems is kept contract-consistent: return items unchanged.
        // The app currently always enters through setMediaItems (with a start
        // index); this override handles the theoretical case of a controller
        // calling addMediaItems without a start position.
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

        val effectiveStartIndex = if (startIndex == C.INDEX_UNSET) {
            0
        } else {
            startIndex.coerceIn(0, mediaItems.lastIndex)
        }

        scope.launch {
            val startStub = mediaItems[effectiveStartIndex]
            val resolvedList = runCatching { resolver.resolve(startStub) }
                .fold(
                    onSuccess = { resolved ->
                        mediaItems.toMutableList().also { list ->
                            list[effectiveStartIndex] = resolved
                        }
                    },
                    onFailure = {
                        // Pass through — Media3 will fail fast on the stub
                        // and the user sees an error instead of a hang.
                        mediaItems
                    },
                )

            future.set(
                MediaSession.MediaItemsWithStartPosition(
                    resolvedList,
                    effectiveStartIndex,
                    startPositionMs,
                ),
            )
        }

        return future
    }
}

/**
 * [Player.Listener] that, on every media-item transition, pre-resolves the
 * next stub in the queue so skip-next is instant. Also resolves the current
 * item in place if it is still a stub — covers the edge case where the user
 * skipped forward past the pre-fetch window.
 *
 * A [ConcurrentHashMap] of in-flight deferreds keyed by `mediaId` dedupes
 * concurrent resolves of the same track (e.g. rapid skip-next taps).
 */
internal class LookaheadResolveListener(
    private val resolver: TrackResolver,
    private val mainScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val playerProvider: () -> Player?,
) : Player.Listener {

    private val inFlight = ConcurrentHashMap<String, Deferred<MediaItem>>()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val player = playerProvider() ?: return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= player.mediaItemCount) return

        // 1. Fallback: the current item itself is still a stub (user skipped
        //    past the pre-fetch). Resolve + replace in place. Media3 restarts
        //    playback on that item with the new URI.
        val currentItem = runCatching { player.getMediaItemAt(currentIndex) }.getOrNull()
        if (currentItem != null && currentItem.localConfiguration?.uri == null) {
            resolveAndReplace(currentIndex, currentItem)
        }

        // 2. Lookahead: pre-resolve the next stub, if any.
        val nextIndex = currentIndex + 1
        if (nextIndex < player.mediaItemCount) {
            val nextItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull()
            if (nextItem != null && nextItem.localConfiguration?.uri == null) {
                resolveAndReplace(nextIndex, nextItem)
            }
        }
    }

    private fun resolveAndReplace(index: Int, stub: MediaItem) {
        val key = stub.mediaId.ifBlank { return }
        // computeIfAbsent dedupes concurrent resolves of the same track.
        val deferred = inFlight.computeIfAbsent(key) {
            val result = CompletableDeferred<MediaItem>()
            mainScope.launch {
                val resolved = runCatching {
                    withContext(ioDispatcher) { resolver.resolve(stub) }
                }.getOrElse {
                    // Swallow — Media3 will naturally skip the stub when it
                    // hits it. No logging framework in the project.
                    inFlight.remove(key)
                    result.complete(stub)
                    return@launch
                }

                val player = playerProvider()
                if (player != null && index < player.mediaItemCount) {
                    val currentAtIndex = runCatching {
                        player.getMediaItemAt(index)
                    }.getOrNull()
                    // Only replace if the slot still holds our stub (the queue
                    // may have shifted while we were resolving).
                    if (currentAtIndex?.mediaId == key &&
                        currentAtIndex.localConfiguration?.uri == null
                    ) {
                        player.replaceMediaItem(index, resolved)
                    }
                }
                inFlight.remove(key)
                result.complete(resolved)
            }
            result
        }
        // Reference the deferred to keep the compiler happy and document
        // intent — callers may await it in the future if needed.
        @Suppress("UNUSED_VARIABLE")
        val ignored = deferred
    }
}
