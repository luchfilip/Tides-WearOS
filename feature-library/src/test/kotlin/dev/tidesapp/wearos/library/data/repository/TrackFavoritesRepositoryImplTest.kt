package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.dto.ArtistBriefDto
import dev.tidesapp.wearos.library.data.dto.TrackAlbumDto
import dev.tidesapp.wearos.library.data.dto.TrackDataDto
import dev.tidesapp.wearos.library.data.dto.V1FavoriteTrackEntryDto
import dev.tidesapp.wearos.library.data.dto.V1FavoriteTracksResponseDto
import dev.tidesapp.wearos.library.domain.repository.TrackFavoritesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class TrackFavoritesRepositoryImplTest {

    private lateinit var api: TidesLibraryApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var repository: TrackFavoritesRepository

    private val sampleResponse = V1FavoriteTracksResponseDto(
        limit = 9999,
        offset = 0,
        totalNumberOfItems = 2,
        items = listOf(
            V1FavoriteTrackEntryDto(
                created = "2026-03-16T17:17:01.464+0000",
                item = TrackDataDto(
                    id = 134013828,
                    title = "Jaga",
                    duration = 354,
                    trackNumber = 1,
                    volumeNumber = 1,
                    artist = ArtistBriefDto(id = 4641377, name = "HNNY"),
                    album = TrackAlbumDto(
                        id = 134013827,
                        title = "Montara",
                        cover = "f83a63eb-fdbb-4e56-8a61-60f93c922ccc",
                    ),
                ),
            ),
            V1FavoriteTrackEntryDto(
                created = "2026-03-16T17:17:02.111+0000",
                item = TrackDataDto(
                    id = 55555,
                    title = "Second",
                    duration = 200,
                    trackNumber = 2,
                    artist = ArtistBriefDto(id = 1, name = "Other"),
                ),
            ),
        ),
    )

    @Before
    fun setup() {
        api = mockk()
        credentialsProvider = mockkCredentials(token = "fake-jwt", userId = "testuser-1")
        repository = TrackFavoritesRepositoryImpl(api, credentialsProvider)
    }

    @Test
    fun `getUserFavoriteTracks fetches and maps items`() = runTest {
        coEvery {
            api.getUserFavoriteTracks(
                token = "Bearer fake-jwt",
                userId = "testuser-1",
                limit = 9999,
            )
        } returns sampleResponse

        val result = repository.getUserFavoriteTracks()

        assertTrue(result.isSuccess)
        val tracks = result.getOrNull()!!
        assertEquals(2, tracks.size)
        assertEquals("134013828", tracks[0].id)
        assertEquals("Jaga", tracks[0].title)
        assertEquals("HNNY", tracks[0].artistName)
        assertEquals("Montara", tracks[0].albumTitle)
        coVerify(exactly = 1) {
            api.getUserFavoriteTracks(any(), any(), any())
        }
    }

    @Test
    fun `getUserFavoriteTracks returns cached data on second call`() = runTest {
        coEvery { api.getUserFavoriteTracks(any(), any(), any()) } returns sampleResponse

        repository.getUserFavoriteTracks()
        val result = repository.getUserFavoriteTracks()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        coVerify(exactly = 1) { api.getUserFavoriteTracks(any(), any(), any()) }
    }

    @Test
    fun `getUserFavoriteTracks with forceRefresh hits API again`() = runTest {
        coEvery { api.getUserFavoriteTracks(any(), any(), any()) } returns sampleResponse

        repository.getUserFavoriteTracks()
        val result = repository.getUserFavoriteTracks(forceRefresh = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { api.getUserFavoriteTracks(any(), any(), any()) }
    }

    @Test
    fun `getUserFavoriteTracks fails when token is null`() = runTest {
        credentialsProvider = mockkCredentials(token = null, userId = "testuser-1")
        repository = TrackFavoritesRepositoryImpl(api, credentialsProvider)

        val result = repository.getUserFavoriteTracks()

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        coVerify(exactly = 0) { api.getUserFavoriteTracks(any(), any(), any()) }
    }

    @Test
    fun `getUserFavoriteTracks fails when userId is null`() = runTest {
        credentialsProvider = mockkCredentials(token = "fake-jwt", userId = null)
        repository = TrackFavoritesRepositoryImpl(api, credentialsProvider)

        val result = repository.getUserFavoriteTracks()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.getUserFavoriteTracks(any(), any(), any()) }
    }

    @Test
    fun `getUserFavoriteTracks wraps API exceptions as failure`() = runTest {
        coEvery { api.getUserFavoriteTracks(any(), any(), any()) } throws IOException("boom")

        val result = repository.getUserFavoriteTracks()

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
