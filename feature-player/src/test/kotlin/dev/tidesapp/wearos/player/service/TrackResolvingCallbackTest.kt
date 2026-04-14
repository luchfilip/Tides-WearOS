package dev.tidesapp.wearos.player.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TrackResolvingCallback]. The key invariant under test:
 * `onSetMediaItems` resolves EVERY item in parallel and returns them all with
 * the original start index. Items that fail to resolve are dropped from the
 * queue (never left as stubs — that would re-trigger Media3's
 * `DefaultMediaSourceFactory` NPE on null localConfiguration).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackResolvingCallbackTest {

    private val mediaSession: MediaSession = mockk(relaxed = true)
    private val controllerInfo: MediaSession.ControllerInfo = mockk(relaxed = true)

    @Before
    fun setup() {
        // android.net.Uri.parse returns null under isReturnDefaultValues,
        // which prevents MediaItem.Builder from attaching a localConfiguration
        // when resolved() builds an item with a URI. Stub it to echo input.
        mockkStatic(Uri::class)
        val uriSlot = slot<String>()
        every { Uri.parse(capture(uriSlot)) } answers {
            val captured = uriSlot.captured
            mockk<Uri>(relaxed = true).also { every { it.toString() } returns captured }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun stub(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()

    private fun resolved(id: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://cdn.tidal/$id.m4a")
        .build()

    @Test
    fun `onSetMediaItems resolves every item in parallel and preserves start index`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val stubs = (0 until 5).map { stub("track-$it") }
        stubs.forEachIndexed { i, s ->
            coEvery { resolver.resolve(s) } returns resolved("track-$i")
        }

        val callback = TrackResolvingCallback(
            resolver = resolver,
            scope = CoroutineScope(dispatcher),
        )

        val future = callback.onSetMediaItems(
            mediaSession = mediaSession,
            controller = controllerInfo,
            mediaItems = stubs,
            startIndex = 2,
            startPositionMs = 12_345L,
        )
        advanceUntilIdle()

        assertTrue(future.isDone)
        val result = future.get()
        assertEquals(2, result.startIndex)
        assertEquals(12_345L, result.startPositionMs)
        assertEquals(5, result.mediaItems.size)

        // Every item has a URI — no stubs in the queue.
        result.mediaItems.forEachIndexed { i, item ->
            assertNotNull("item $i should be resolved", item.localConfiguration)
            assertEquals(
                "https://cdn.tidal/track-$i.m4a",
                item.localConfiguration!!.uri.toString(),
            )
        }

        coVerify(exactly = 5) { resolver.resolve(any()) }
    }

    @Test
    fun `onSetMediaItems drops failed items and remaps start index`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val stubs = listOf(stub("a"), stub("b"), stub("c"), stub("d"))
        // Drop index 1. Start was 2 → new start should be 1 (one item removed before it).
        coEvery { resolver.resolve(stubs[0]) } returns resolved("a")
        coEvery { resolver.resolve(stubs[1]) } throws RuntimeException("410 gone")
        coEvery { resolver.resolve(stubs[2]) } returns resolved("c")
        coEvery { resolver.resolve(stubs[3]) } returns resolved("d")

        val callback = TrackResolvingCallback(
            resolver = resolver,
            scope = CoroutineScope(dispatcher),
        )

        val future = callback.onSetMediaItems(
            mediaSession, controllerInfo, stubs, startIndex = 2, startPositionMs = 0L,
        )
        advanceUntilIdle()

        val result = future.get()
        assertEquals(3, result.mediaItems.size)
        assertEquals(1, result.startIndex)
        assertEquals("c", result.mediaItems[result.startIndex].mediaId)
        // Every surviving item has a URI.
        result.mediaItems.forEach { assertNotNull(it.localConfiguration) }
    }

    @Test
    fun `onSetMediaItems collapses start index when the start item itself fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val stubs = listOf(stub("a"), stub("b"))
        coEvery { resolver.resolve(stubs[0]) } returns resolved("a")
        coEvery { resolver.resolve(stubs[1]) } throws RuntimeException("boom")

        val callback = TrackResolvingCallback(
            resolver = resolver,
            scope = CoroutineScope(dispatcher),
        )

        val future = callback.onSetMediaItems(
            mediaSession, controllerInfo, stubs, startIndex = 1, startPositionMs = 0L,
        )
        advanceUntilIdle()

        val result = future.get()
        assertEquals(1, result.mediaItems.size)
        assertEquals("a", result.mediaItems[0].mediaId)
        assertEquals(0, result.startIndex)
    }

    @Test
    fun `onSetMediaItems coerces INDEX_UNSET to zero`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val stubs = listOf(stub("first"), stub("second"))
        coEvery { resolver.resolve(stubs[0]) } returns resolved("first")
        coEvery { resolver.resolve(stubs[1]) } returns resolved("second")

        val callback = TrackResolvingCallback(
            resolver = resolver,
            scope = CoroutineScope(dispatcher),
        )

        val future = callback.onSetMediaItems(
            mediaSession, controllerInfo, stubs,
            startIndex = C.INDEX_UNSET,
            startPositionMs = 0L,
        )
        advanceUntilIdle()

        val result = future.get()
        assertEquals(0, result.startIndex)
        assertEquals(2, result.mediaItems.size)
    }

    @Test
    fun `onSetMediaItems handles empty list without launching coroutine`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val callback = TrackResolvingCallback(
            resolver = resolver,
            scope = CoroutineScope(dispatcher),
        )

        val future = callback.onSetMediaItems(
            mediaSession, controllerInfo, emptyList(),
            startIndex = C.INDEX_UNSET,
            startPositionMs = 0L,
        )
        advanceUntilIdle()

        assertTrue(future.isDone)
        val result = future.get()
        assertTrue(result.mediaItems.isEmpty())
        assertEquals(0, result.startIndex)
        coVerify(exactly = 0) { resolver.resolve(any()) }
    }

    @Test
    fun `onAddMediaItems returns items unchanged without resolving`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val stubs = listOf(stub("x"), stub("y"))

        val callback = TrackResolvingCallback(
            resolver = resolver,
            scope = CoroutineScope(dispatcher),
        )

        val future = callback.onAddMediaItems(mediaSession, controllerInfo, stubs)
        advanceUntilIdle()

        val result = future.get()
        assertEquals(stubs, result)
        coVerify(exactly = 0) { resolver.resolve(any()) }
    }
}
