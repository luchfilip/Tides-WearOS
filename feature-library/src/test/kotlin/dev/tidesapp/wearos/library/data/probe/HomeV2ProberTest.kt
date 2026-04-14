package dev.tidesapp.wearos.library.data.probe

// Note: timeOffset / refreshId formatting tests live in
// core/.../time/TimeOffsetFormatterTest — they moved out of here in TIDES-M2C
// when the formatter was extracted into a shared helper. This test now mocks
// the formatter and focuses on probe orchestration only.

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.time.TimeOffsetFormatter
import dev.tidesapp.wearos.library.data.api.ProbeApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeV2ProberTest {

    private lateinit var probeApi: ProbeApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var timeOffsetFormatter: TimeOffsetFormatter
    private lateinit var prober: HomeV2Prober

    // Fixed values so we can assert the exact refreshId / timeOffset passed to the probes.
    private val expectedRefreshId: Long = 1728921296000L
    private val expectedTimeOffset: String = "-04:00"

    @Before
    fun setup() {
        probeApi = mockk()
        credentialsProvider = mockk {
            coEvery { getCredentials(any()) } returns mockk {
                every { successData } returns mockk {
                    every { token } returns "test-jwt-token"
                }
            }
        }
        timeOffsetFormatter = mockk {
            every { refreshId() } returns expectedRefreshId
            every { timeOffset() } returns expectedTimeOffset
        }
        prober = HomeV2Prober(probeApi, credentialsProvider, timeOffsetFormatter)
    }

    @Test
    fun `runAll returns 200 happy-path results for probes 1 and 2 when playlistUuid is null`() = runTest {
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } returns okResponse(
            body = "{\"uuid\":\"abc\"}",
            headers = Headers.headersOf(
                "tidal-correlation-id", "corr-1",
                "Content-Type", "application/json",
            ),
        )
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns okResponse(
            body = "{\"items\":[]}",
            headers = Headers.headersOf("tidal-request-id", "req-2"),
        )

        val results = prober.runAll(playlistUuid = null)

        assertEquals(2, results.size)

        val home = results[0]
        assertEquals("v2/home/feed/STATIC", home.endpoint)
        assertEquals(200, home.status)
        assertNull(home.error)
        assertEquals("{\"uuid\":\"abc\"}", home.bodyPreview)
        assertEquals(mapOf("tidal-correlation-id" to "corr-1"), home.tidalHeaders)

        val viewAll = results[1]
        assertEquals("v2/home/pages/POPULAR_PLAYLISTS/view-all", viewAll.endpoint)
        assertEquals(200, viewAll.status)
        assertNull(viewAll.error)
        assertEquals(mapOf("tidal-request-id" to "req-2"), viewAll.tidalHeaders)

        coVerify(exactly = 0) { probeApi.probeUserPlaylist(any(), any()) }
    }

    @Test
    fun `runAll sends Bearer token and correct refreshId and timeOffset to both probes`() = runTest {
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } returns okResponse("{}")
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns okResponse("{}")

        prober.runAll()

        // Both probes in a single refresh cycle MUST share the same refreshId —
        // .docs/03-home-feed.md §2.1: "the app reuses the exact same refreshId
        // for every home-related call in the same refresh".
        coVerify(exactly = 1) {
            probeApi.probeHomeV2(
                token = "Bearer test-jwt-token",
                refreshId = expectedRefreshId,
                timeOffset = expectedTimeOffset,
                limit = 20,
            )
        }
        coVerify(exactly = 1) {
            probeApi.probeViewAll(
                token = "Bearer test-jwt-token",
                section = "POPULAR_PLAYLISTS",
                refreshId = expectedRefreshId,
                timeOffset = expectedTimeOffset,
                offset = 0,
                limit = 50,
            )
        }
    }

    @Test
    fun `runAll includes probe 3 when playlistUuid is provided`() = runTest {
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } returns okResponse("{}")
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns okResponse("{}")
        coEvery { probeApi.probeUserPlaylist(any(), any()) } returns okResponse("{\"id\":\"pl-1\"}")

        val results = prober.runAll(playlistUuid = "pl-1")

        assertEquals(3, results.size)
        assertEquals("v2/user-playlists/pl-1", results[2].endpoint)
        assertEquals(200, results[2].status)
        coVerify(exactly = 1) {
            probeApi.probeUserPlaylist(token = "Bearer test-jwt-token", uuid = "pl-1")
        }
    }

    @Test
    fun `runAll skips probe 3 when playlistUuid is null`() = runTest {
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } returns okResponse("{}")
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns okResponse("{}")

        val results = prober.runAll(playlistUuid = null)

        assertEquals(2, results.size)
        coVerify(exactly = 0) { probeApi.probeUserPlaylist(any(), any()) }
    }

    @Test
    fun `runAll surfaces 401 as a status without throwing`() = runTest {
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } returns errorResponse(401, "unauthorized")
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns okResponse("{}")

        val results = prober.runAll()

        assertEquals(2, results.size)
        assertEquals(401, results[0].status)
        assertNull(results[0].error)
        // response.body() is null on error responses (errorBody() holds the payload).
        // Prober must not crash and just produces an empty bodyPreview.
        assertEquals("", results[0].bodyPreview)
    }

    @Test
    fun `runAll surfaces 403 as a status without throwing`() = runTest {
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } returns okResponse("{}")
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns errorResponse(403, "forbidden")

        val results = prober.runAll()

        assertEquals(2, results.size)
        assertEquals(200, results[0].status)
        assertEquals(403, results[1].status)
        assertNull(results[1].error)
    }

    @Test
    fun `runAll isolates per-probe network exceptions and completes remaining probes`() = runTest {
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } throws IOException("connection reset")
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns okResponse("{\"ok\":true}")

        val results = prober.runAll()

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("v2/home/feed/STATIC", first.endpoint)
        assertEquals(0, first.status)
        assertNotNull(first.error)
        val errorMsg = first.error!!
        assertTrue(errorMsg.contains("IOException"))
        assertTrue(errorMsg.contains("connection reset"))

        // Second probe must still fire.
        assertEquals(200, results[1].status)
        assertNull(results[1].error)
    }

    @Test
    fun `runAll returns a single no-credentials result and skips all probes when token is null`() = runTest {
        credentialsProvider = mockk {
            coEvery { getCredentials(any()) } returns mockk {
                every { successData } returns mockk {
                    every { token } returns null
                }
            }
        }
        prober = HomeV2Prober(probeApi, credentialsProvider, timeOffsetFormatter)

        val results = prober.runAll(playlistUuid = "pl-1")

        assertEquals(1, results.size)
        assertEquals("no credentials", results[0].error)
        coVerify(exactly = 0) { probeApi.probeHomeV2(any(), any(), any(), any()) }
        coVerify(exactly = 0) { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { probeApi.probeUserPlaylist(any(), any()) }
    }

    @Test
    fun `runAll truncates body preview to 2048 chars`() = runTest {
        val big = "x".repeat(5000)
        coEvery { probeApi.probeHomeV2(any(), any(), any(), any()) } returns okResponse(big)
        coEvery { probeApi.probeViewAll(any(), any(), any(), any(), any(), any()) } returns okResponse("{}")

        val results = prober.runAll()

        assertEquals(2048, results[0].bodyPreview.length)
    }

    // ---- helpers ----

    private fun okResponse(
        body: String,
        headers: Headers = Headers.headersOf(),
    ): Response<ResponseBody> {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return Response.success(responseBody, headers)
    }

    private fun errorResponse(code: Int, body: String): Response<ResponseBody> {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return Response.error(code, responseBody)
    }
}
