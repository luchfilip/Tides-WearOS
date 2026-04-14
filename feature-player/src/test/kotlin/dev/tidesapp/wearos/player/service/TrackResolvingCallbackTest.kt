package dev.tidesapp.wearos.player.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TrackResolvingCallback]. The key invariant under test is:
 * `onSetMediaItems` resolves ONLY the start item, leaving all other items as
 * stubs (which [LookaheadResolveListener] picks up as playback progresses).
 * This is what drops playback-startup latency from ~15-20s to ~1s.
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
    fun `onSetMediaItems resolves only the startIndex item and leaves others as stubs`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val resolver: TrackResolver = mockk()
            val stubs = (0 until 5).map { stub("track-$it") }
            coEvery { resolver.resolve(stubs[2]) } returns resolved("track-2")

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

            // The start item was resolved — now has a URI.
            assertNotNull(result.mediaItems[2].localConfiguration)
            assertEquals(
                "https://cdn.tidal/track-2.m4a",
                result.mediaItems[2].localConfiguration!!.uri.toString(),
            )

            // All other items passed through untouched (still stubs, no URI).
            listOf(0, 1, 3, 4).forEach { i ->
                assertNull(
                    "item $i should still be a stub",
                    result.mediaItems[i].localConfiguration,
                )
                assertSame(stubs[i], result.mediaItems[i])
            }

            // Crucially, only one resolve() call — not five.
            coVerify(exactly = 1) { resolver.resolve(any()) }
        }

    @Test
    fun `onSetMediaItems returns untouched list when start item resolve fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val stubs = listOf(stub("a"), stub("b"))
        coEvery { resolver.resolve(any()) } throws RuntimeException("boom")

        val callback = TrackResolvingCallback(
            resolver = resolver,
            scope = CoroutineScope(dispatcher),
        )

        val future = callback.onSetMediaItems(
            mediaSession, controllerInfo, stubs, startIndex = 0, startPositionMs = 0L,
        )
        advanceUntilIdle()

        val result = future.get()
        assertEquals(2, result.mediaItems.size)
        // Untouched — Media3 will fail-fast on the stub; user sees error,
        // not a mystery hang.
        assertNull(result.mediaItems[0].localConfiguration)
        assertNull(result.mediaItems[1].localConfiguration)
    }

    @Test
    fun `onSetMediaItems coerces INDEX_UNSET to zero and resolves first item`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver: TrackResolver = mockk()
        val stubs = listOf(stub("first"), stub("second"))
        coEvery { resolver.resolve(stubs[0]) } returns resolved("first")

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
        assertNotNull(result.mediaItems[0].localConfiguration)
        assertNull(result.mediaItems[1].localConfiguration)
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
