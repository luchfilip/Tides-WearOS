package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.dto.V1ActivityEntryDto
import dev.tidesapp.wearos.library.data.dto.V1ActivityResponseDto
import dev.tidesapp.wearos.library.domain.repository.RecentActivityRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RecentActivityRepositoryImplTest {

    private lateinit var api: TidesLibraryApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var repository: RecentActivityRepository
    private val json = Json { ignoreUnknownKeys = true }

    private fun trackElement(id: Long, title: String, artist: String): JsonElement =
        json.parseToJsonElement(
            """
            {
              "id": $id,
              "title": "$title",
              "duration": 300,
              "trackNumber": 1,
              "volumeNumber": 1,
              "explicit": false,
              "artist": { "id": 1, "name": "$artist" },
              "album": { "id": 10, "title": "Album", "cover": null }
            }
            """.trimIndent(),
        )

    private fun playlistElement(): JsonElement =
        json.parseToJsonElement(
            """
            {
              "uuid": "aaaaaaaa-bbbb-cccc-dddd-000000000001",
              "title": "Sample Playlist",
              "numberOfTracks": 1
            }
            """.trimIndent(),
        )

    @Before
    fun setup() {
        api = mockk()
        credentialsProvider = mockkCredentials(token = "fake-jwt", userId = "testuser-1")
        repository = RecentActivityRepositoryImpl(api, credentialsProvider)
    }

    @Test
    fun `getRecentTracks decodes TRACK entries and drops others`() = runTest {
        coEvery {
            api.getUserActivity(
                token = "Bearer fake-jwt",
                userId = "testuser-1",
                offset = 0,
                limit = 50,
            )
        } returns V1ActivityResponseDto(
            items = listOf(
                V1ActivityEntryDto(
                    activityType = "ADDED_TO_FAVORITES",
                    type = "TRACK",
                    item = trackElement(1, "First", "Artist A"),
                ),
                V1ActivityEntryDto(
                    activityType = "UPDATED",
                    type = "PLAYLIST",
                    item = playlistElement(),
                ),
                V1ActivityEntryDto(
                    activityType = "ADDED_TO_FAVORITES",
                    type = "ALBUM",
                    item = playlistElement(),
                ),
                V1ActivityEntryDto(
                    activityType = "ADDED_TO_FAVORITES",
                    type = "TRACK",
                    item = trackElement(2, "Second", "Artist B"),
                ),
            ),
        )

        val result = repository.getRecentTracks()

        assertTrue(result.isSuccess)
        val tracks = result.getOrNull()!!
        assertEquals(2, tracks.size)
        assertEquals("1", tracks[0].id)
        assertEquals("First", tracks[0].title)
        assertEquals("Artist A", tracks[0].artistName)
        assertEquals("2", tracks[1].id)
    }

    @Test
    fun `getRecentTracks with empty items returns empty success`() = runTest {
        coEvery { api.getUserActivity(any(), any(), any(), any()) } returns
            V1ActivityResponseDto(items = emptyList())

        val result = repository.getRecentTracks()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }

    @Test
    fun `getRecentTracks fails when token is null`() = runTest {
        credentialsProvider = mockkCredentials(token = null, userId = "testuser-1")
        repository = RecentActivityRepositoryImpl(api, credentialsProvider)

        val result = repository.getRecentTracks()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.getUserActivity(any(), any(), any(), any()) }
    }

    @Test
    fun `getRecentTracks fails when userId is null`() = runTest {
        credentialsProvider = mockkCredentials(token = "fake-jwt", userId = null)
        repository = RecentActivityRepositoryImpl(api, credentialsProvider)

        val result = repository.getRecentTracks()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.getUserActivity(any(), any(), any(), any()) }
    }

    @Test
    fun `getRecentTracks wraps API exceptions as failure`() = runTest {
        coEvery { api.getUserActivity(any(), any(), any(), any()) } throws IOException("boom")

        val result = repository.getRecentTracks()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    private fun mockkCredentials(token: String?, userId: String?): CredentialsProvider = mockk {
        coEvery { getCredentials(any()) } returns mockk {
            every { successData } returns mockk {
                every { this@mockk.token } returns token
                every { this@mockk.userId } returns userId
            }
        }
    }
}
