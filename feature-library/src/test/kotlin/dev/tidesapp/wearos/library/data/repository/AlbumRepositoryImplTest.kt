package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.dto.AlbumDataDto
import dev.tidesapp.wearos.library.data.dto.ArtistBriefDto
import dev.tidesapp.wearos.library.data.dto.CollectionAlbumEntryDto
import dev.tidesapp.wearos.library.data.dto.CollectionAlbumsResponseDto
import dev.tidesapp.wearos.library.data.dto.TrackAlbumDto
import dev.tidesapp.wearos.library.data.dto.TrackDataDto
import dev.tidesapp.wearos.library.data.dto.V1TrackListResponseDto
import dev.tidesapp.wearos.library.domain.repository.AlbumRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlbumRepositoryImplTest {

    private lateinit var api: TidesLibraryApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var repository: AlbumRepository

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
        repository = AlbumRepositoryImpl(api, credentialsProvider)
    }

    @Test
    fun `getUserAlbums fetches on first API call`() = runTest {
        val response = createCollectionResponse(1)
        coEvery { api.getUserAlbums(any()) } returns response

        val result = repository.getUserAlbums()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Album 0", result.getOrNull()?.first()?.title)
        coVerify(exactly = 1) { api.getUserAlbums(any()) }
    }

    @Test
    fun `getUserAlbums returns cached data on second call`() = runTest {
        val response = createCollectionResponse(2)
        coEvery { api.getUserAlbums(any()) } returns response

        repository.getUserAlbums()
        val result = repository.getUserAlbums()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        coVerify(exactly = 1) { api.getUserAlbums(any()) }
    }

    @Test
    fun `getUserAlbums refreshes with forceRefresh`() = runTest {
        val response = createCollectionResponse(1)
        coEvery { api.getUserAlbums(any()) } returns response

        repository.getUserAlbums()
        val result = repository.getUserAlbums(forceRefresh = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { api.getUserAlbums(any()) }
    }

    @Test
    fun `getUserAlbums returns failure when API throws exception`() = runTest {
        val exception = RuntimeException("Network error")
        coEvery { api.getUserAlbums(any()) } throws exception

        val result = repository.getUserAlbums()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `getUserAlbums makes a single API request on concurrent calls`() = runTest {
        val response = createCollectionResponse(3)
        coEvery { api.getUserAlbums(any()) } returns response

        val results = List(10) {
            async { repository.getUserAlbums() }
        }.awaitAll()

        assertEquals(10, results.size)
        results.forEach { assertTrue(it.isSuccess) }
        coVerify(exactly = 1) { api.getUserAlbums(any()) }
    }

    @Test
    fun `getUserAlbums follows cursor across pages`() = runTest {
        val page1 = createCollectionResponse(50, cursor = "c1")
        val page2 = createCollectionResponse(50, cursor = "c2")
        val page3 = createCollectionResponse(7, cursor = null)
        coEvery { api.getUserAlbums(token = any(), cursor = "") } returns page1
        coEvery { api.getUserAlbums(token = any(), cursor = "c1") } returns page2
        coEvery { api.getUserAlbums(token = any(), cursor = "c2") } returns page3

        val result = repository.getUserAlbums()

        assertTrue(result.isSuccess)
        assertEquals(107, result.getOrNull()?.size)
        coVerify(exactly = 1) { api.getUserAlbums(token = any(), cursor = "") }
        coVerify(exactly = 1) { api.getUserAlbums(token = any(), cursor = "c1") }
        coVerify(exactly = 1) { api.getUserAlbums(token = any(), cursor = "c2") }
    }

    @Test
    fun `getUserAlbums fetches single page when cursor null`() = runTest {
        val response = createCollectionResponse(10, cursor = null)
        coEvery { api.getUserAlbums(token = any(), cursor = any()) } returns response

        val result = repository.getUserAlbums()

        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.size)
        coVerify(exactly = 1) { api.getUserAlbums(token = any(), cursor = any()) }
    }

    @Test
    fun `getAlbumDetail returns album successfully`() = runTest {
        val response = AlbumDataDto(
            id = 123,
            title = "Test Album",
            artists = listOf(ArtistBriefDto(id = 1, name = "Artist")),
            cover = "aabbccdd-1122-3344-5566-778899000123",
            releaseDate = "2024-01-01",
            numberOfTracks = 10,
        )
        coEvery { api.getAlbum(any(), any()) } returns response

        val result = repository.getAlbumDetail("123")

        assertTrue(result.isSuccess)
        assertEquals("Test Album", result.getOrNull()?.title)
        assertEquals("123", result.getOrNull()?.id)
    }

    @Test
    fun `getAlbumTracks returns tracks successfully`() = runTest {
        val response = V1TrackListResponseDto(
            limit = 100,
            offset = 0,
            totalNumberOfItems = 2,
            items = listOf(
                createTrackData(1, "Track 1", 1),
                createTrackData(2, "Track 2", 2),
            ),
        )
        coEvery { api.getAlbumTracks(any(), any()) } returns response

        val result = repository.getAlbumTracks("123")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        assertEquals("Track 1", result.getOrNull()?.first()?.title)
    }

    private fun createCollectionResponse(count: Int, cursor: String? = null) = CollectionAlbumsResponseDto(
        items = List(count) { index ->
            CollectionAlbumEntryDto(
                itemType = "ALBUM",
                data = AlbumDataDto(
                    id = index.toLong(),
                    title = "Album $index",
                    artists = listOf(ArtistBriefDto(id = 1, name = "Artist")),
                    cover = "aabbccdd-1122-3344-5566-77889900${index.toString().padStart(4, '0')}",
                    releaseDate = "2024-01-01",
                    numberOfTracks = 10,
                ),
            )
        },
        totalNumberOfItems = count,
        cursor = cursor,
    )

    private fun createTrackData(id: Long, title: String, trackNumber: Int) = TrackDataDto(
        id = id,
        title = title,
        duration = 210,
        trackNumber = trackNumber,
        artist = ArtistBriefDto(id = 1, name = "Artist"),
        album = TrackAlbumDto(id = 123, title = "Album", cover = "aabbccdd-1122-3344-5566-778899000123"),
    )
}
