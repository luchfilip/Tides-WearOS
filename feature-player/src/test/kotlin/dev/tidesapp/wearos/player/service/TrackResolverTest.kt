package dev.tidesapp.wearos.player.service

import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.player.data.api.TidesPlaybackApi
import dev.tidesapp.wearos.player.data.dto.PlaybackInfoResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TrackResolver] — the plain-Kotlin extraction of the
 * stub -> playable-MediaItem conversion that used to live inline in
 * [TrackResolvingCallback]. Tests run on JVM with `isReturnDefaultValues`
 * enabled so Media3's `MediaItem.Builder` works without an Android runtime.
 */
class TrackResolverTest {

    private lateinit var api: TidesPlaybackApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var resolver: TrackResolver

    @Before
    fun setup() {
        api = mockk()
        credentialsProvider = mockk {
            coEvery { getCredentials(any()) } returns mockk {
                every { successData } returns mockk {
                    every { token } returns "fake-jwt-token"
                }
            }
        }
        resolver = TrackResolver(
            playbackApi = api,
            credentialsProvider = credentialsProvider,
        )

        // android.util.Base64 is not in the JVM runtime; stub it so BTS path
        // decodes deterministically. DASH tests don't hit this.
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            // Echo input bytes — the BTS test will pass a pre-constructed
            // JSON string so the round-trip lands on real JSON.
            firstArg<String>().toByteArray(Charsets.UTF_8)
        }

        // android.net.Uri.parse returns null under isReturnDefaultValues,
        // which makes MediaItem.Builder drop the localConfiguration. Mock it
        // to return a Uri whose toString() echoes the input — that's all
        // MediaItem.LocalConfiguration cares about for these assertions.
        mockkStatic(Uri::class)
        val uriSlot = slot<String>()
        every { Uri.parse(capture(uriSlot)) } answers {
            val captured = uriSlot.captured
            mockk<Uri>(relaxed = true).also { every { it.toString() } returns captured }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun `resolve DASH track sets data URI and dash mime on builder`() = runTest {
        val stub = MediaItem.Builder().setMediaId("track-42").build()
        coEvery { api.getTrackPlaybackInfo(any(), eq("track-42")) } returns
            PlaybackInfoResponse(
                trackId = 42L,
                manifestMimeType = "application/dash+xml",
                manifest = "BASE64MPD==",
            )

        val resolved = resolver.resolve(stub)

        assertEquals("track-42", resolved.mediaId)
        assertNotNull(resolved.localConfiguration)
        assertEquals(
            "data:application/dash+xml;base64,BASE64MPD==",
            resolved.localConfiguration!!.uri.toString(),
        )
        assertEquals("application/dash+xml", resolved.localConfiguration!!.mimeType)
    }

    @Test
    fun `resolve BTS track picks first URL from decoded JSON manifest`() = runTest {
        val stub = MediaItem.Builder().setMediaId("track-7").build()
        // The stubbed Base64.decode echoes bytes back, so manifest already
        // holds the plain JSON string.
        val btsJson = """{"mimeType":"audio/mp4","codecs":"mp4a","urls":["https://cdn.tidal/audio.m4a"]}"""
        coEvery { api.getTrackPlaybackInfo(any(), eq("track-7")) } returns
            PlaybackInfoResponse(
                trackId = 7L,
                manifestMimeType = "application/vnd.tidal.bts",
                manifest = btsJson,
            )

        val resolved = resolver.resolve(stub)

        assertNotNull(resolved.localConfiguration)
        assertEquals(
            "https://cdn.tidal/audio.m4a",
            resolved.localConfiguration!!.uri.toString(),
        )
    }

    @Test
    fun `resolve returns untouched stub when mediaId is blank`() = runTest {
        val stub = MediaItem.Builder().setMediaId("").build()

        val resolved = resolver.resolve(stub)

        assertEquals(stub, resolved)
        assertNull(resolved.localConfiguration)
    }

    @Test(expected = RuntimeException::class)
    fun `resolve propagates API exceptions for caller to handle`() = runTest {
        val stub = MediaItem.Builder().setMediaId("track-boom").build()
        coEvery { api.getTrackPlaybackInfo(any(), eq("track-boom")) } throws
            RuntimeException("401 Unauthorized")

        resolver.resolve(stub)
    }

    @Test(expected = RuntimeException::class)
    fun `resolve throws when credentials provider returns no success data`() = runTest {
        val failingCredentials: CredentialsProvider = mockk {
            coEvery { getCredentials(any()) } returns mockk {
                every { successData } returns null
            }
        }
        val failingResolver = TrackResolver(
            playbackApi = api,
            credentialsProvider = failingCredentials,
        )
        val stub = MediaItem.Builder().setMediaId("track-99").build()

        failingResolver.resolve(stub)
    }
}
