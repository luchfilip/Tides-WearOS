package dev.tidesapp.wearos.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.player.data.PlayerRepositoryImpl
import dev.tidesapp.wearos.player.service.WearMusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlaybackController @Inject constructor(
    private val playerRepository: PlayerRepositoryImpl,
    @ApplicationContext private val context: Context,
) : PlaybackControl {

    @Volatile
    private var mediaController: MediaController? = null
    private val controllerMutex = Mutex()

    override suspend fun playTracks(
        tracks: List<TrackItem>,
        startIndex: Int,
    ): Result<Unit> = runCatching {
        require(tracks.isNotEmpty()) { "playTracks called with empty list" }
        val (mediaItems, safeIndex) = buildStubMediaItems(tracks, startIndex)
        withContext(Dispatchers.Main) {
            val controller = ensureController()
            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    /**
     * Pure builder for the stub [MediaItem] queue. Extracted so it can be
     * unit-tested without instantiating a real [MediaController] (which in
     * turn requires a running [WearMusicService] + [SessionToken]).
     */
    internal fun buildStubMediaItems(
        tracks: List<TrackItem>,
        startIndex: Int,
    ): Pair<List<MediaItem>, Int> {
        require(tracks.isNotEmpty()) { "buildStubMediaItems called with empty list" }
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val mediaItems = tracks.map { it.toStubMediaItem() }
        return mediaItems to safeIndex
    }

    /**
     * Returns the cached [MediaController], building it lazily on first call.
     *
     * Binding a [MediaController] to the [WearMusicService] session is what
     * causes Media3 to promote the service to foreground and invoke the
     * notification provider. Must be called from the main thread — Media3
     * requires [MediaController.Builder] to be used there.
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
}

/**
 * Build a URI-less [MediaItem] carrying only the track id + display metadata.
 * The service-side [androidx.media3.session.MediaSession.Callback.onAddMediaItems]
 * hook is responsible for resolving the playback URI just-in-time.
 */
internal fun TrackItem.toStubMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistName)
                .setAlbumTitle(albumTitle)
                .setArtworkUri(tidalArtworkUri(imageUrl))
                .build(),
        )
        .build()

/**
 * Compose a TIDAL `resources.tidal.com` CDN URL from the cover id contained
 * in [imageUrl]. Accepts either a full https URL (passed through) or a bare
 * `uuid` / `uuid-with-dashes` cover id (rewritten into the 320x320 asset).
 */
private fun tidalArtworkUri(imageUrl: String?): Uri? {
    if (imageUrl.isNullOrBlank()) return null
    if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
        return Uri.parse(imageUrl)
    }
    val path = imageUrl.replace("-", "/")
    return Uri.parse("https://resources.tidal.com/images/$path/320x320.jpg")
}
