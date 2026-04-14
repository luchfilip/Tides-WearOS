package dev.tidesapp.wearos.player.playback

import android.content.Context
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.player.data.PlayerRepositoryImpl
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [PlaybackController]'s pure queue-building logic.
 *
 * We do NOT exercise the [androidx.media3.session.MediaController] drive path
 * here — that requires a live [androidx.media3.session.SessionToken] / running
 * [dev.tidesapp.wearos.player.service.WearMusicService], which is integration
 * territory. The `setMediaItems(list, index, 0L)` orchestration is covered by
 * hand-verified on-device smoke tests.
 */
class PlaybackControllerTest {

    private lateinit var controller: PlaybackController

    @Before
    fun setup() {
        val context: Context = mockk(relaxed = true)
        val playerRepository: PlayerRepositoryImpl = mockk(relaxed = true)
        controller = PlaybackController(
            playerRepository = playerRepository,
            context = context,
        )
    }

    @Test
    fun `buildStubMediaItems preserves track order and uses TrackItem id as mediaId`() {
        val tracks = trackList(3)

        val (items, safeIndex) = controller.buildStubMediaItems(tracks, startIndex = 1)

        assertEquals(3, items.size)
        assertEquals(1, safeIndex)
        assertEquals("track-0", items[0].mediaId)
        assertEquals("track-1", items[1].mediaId)
        assertEquals("track-2", items[2].mediaId)
    }

    @Test
    fun `buildStubMediaItems attaches title artist and album metadata to each stub`() {
        val tracks = trackList(2)

        val (items, _) = controller.buildStubMediaItems(tracks, startIndex = 0)

        assertEquals("Track 0", items[0].mediaMetadata.title)
        assertEquals("Artist 0", items[0].mediaMetadata.artist)
        assertEquals("Album 0", items[0].mediaMetadata.albumTitle)
        assertEquals("Track 1", items[1].mediaMetadata.title)
    }

    @Test
    fun `buildStubMediaItems leaves stubs URI-less so onAddMediaItems can resolve`() {
        val tracks = trackList(1)

        val (items, _) = controller.buildStubMediaItems(tracks, startIndex = 0)

        // A stub carries no playback URI — the service-side resolver supplies it.
        assertNull(items[0].localConfiguration)
    }

    @Test
    fun `buildStubMediaItems coerces negative startIndex to zero`() {
        val tracks = trackList(5)

        val (_, safeIndex) = controller.buildStubMediaItems(tracks, startIndex = -3)

        assertEquals(0, safeIndex)
    }

    @Test
    fun `buildStubMediaItems coerces startIndex greater than lastIndex to lastIndex`() {
        val tracks = trackList(4)

        val (_, safeIndex) = controller.buildStubMediaItems(tracks, startIndex = 99)

        assertEquals(3, safeIndex)
    }

    @Test
    fun `buildStubMediaItems throws on empty list`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            controller.buildStubMediaItems(emptyList(), startIndex = 0)
        }
        assertEquals("buildStubMediaItems called with empty list", error.message)
    }

    @Test
    fun `playTracks fails with IllegalArgument Result on empty list`() = runTest {
        val result = controller.playTracks(emptyList(), startIndex = 0)

        assertEquals(true, result.isFailure)
        assertEquals(
            IllegalArgumentException::class.java,
            result.exceptionOrNull()!!.javaClass,
        )
    }

    private fun trackList(count: Int): List<TrackItem> = List(count) { index ->
        TrackItem(
            id = "track-$index",
            title = "Track $index",
            artistName = "Artist $index",
            albumTitle = "Album $index",
            duration = 180 + index,
            trackNumber = index + 1,
            // null imageUrl => tidalArtworkUri(...) short-circuits to null,
            // avoiding an android.net.Uri.parse call inside the stub builder.
            imageUrl = null,
        )
    }
}
