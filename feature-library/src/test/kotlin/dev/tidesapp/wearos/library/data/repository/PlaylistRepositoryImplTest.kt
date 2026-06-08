package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.dto.ArtistBriefDto
import dev.tidesapp.wearos.library.data.dto.CollectionPlaylistEntryDto
import dev.tidesapp.wearos.library.data.dto.CollectionPlaylistsResponseDto
import dev.tidesapp.wearos.library.data.dto.PlaylistCreatorDto
import dev.tidesapp.wearos.library.data.dto.PlaylistDataDto
import dev.tidesapp.wearos.library.data.dto.TrackDataDto
import dev.tidesapp.wearos.library.data.dto.V1TrackListResponseDto
import dev.tidesapp.wearos.library.domain.repository.PlaylistRepository
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

class PlaylistRepositoryImplTest {

    private lateinit var api: TidesLibraryApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var repository: PlaylistRepository

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
        repository = PlaylistRepositoryImpl(api, credentialsProvider)
    }

    @Test
    fun `getUserPlaylists fetches on first API call`() = runTest {
        val response = createPlaylistsResponse(2)
        coEvery { api.getUserPlaylists(any()) } returns response

        val result = repository.getUserPlaylists()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        assertEquals("Playlist 0", result.getOrNull()?.first()?.title)
        coVerify(exactly = 1) { api.getUserPlaylists(any()) }
    }

    @Test
    fun `getUserPlaylists returns cached data on second call`() = runTest {
        val response = createPlaylistsResponse(3)
        coEvery { api.getUserPlaylists(any()) } returns response

        repository.getUserPlaylists()
        val result = repository.getUserPlaylists()

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
        coVerify(exactly = 1) { api.getUserPlaylists(any()) }
    }

    @Test
    fun `getUserPlaylists refreshes with forceRefresh`() = runTest {
        val response = createPlaylistsResponse(1)
        coEvery { api.getUserPlaylists(any()) } returns response

        repository.getUserPlaylists()
        val result = repository.getUserPlaylists(forceRefresh = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { api.getUserPlaylists(any()) }
    }

    @Test
    fun `getUserPlaylists returns failure when API throws exception`() = runTest {
        val exception = RuntimeException("Network error")
        coEvery { api.getUserPlaylists(any()) } throws exception

        val result = repository.getUserPlaylists()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `getUserPlaylists makes a single API request on concurrent calls`() = runTest {
        val response = createPlaylistsResponse(2)
        coEvery { api.getUserPlaylists(any()) } returns response

        val results = List(10) {
            async { repository.getUserPlaylists() }
        }.awaitAll()

        assertEquals(10, results.size)
        results.forEach { assertTrue(it.isSuccess) }
        coVerify(exactly = 1) { api.getUserPlaylists(any()) }
    }

    @Test
    fun `getPlaylistTracks returns tracks successfully`() = runTest {
        val response = V1TrackListResponseDto(
            limit = 50,
            offset = 0,
            totalNumberOfItems = 1,
            items = listOf(
                TrackDataDto(
                    id = 1,
                    title = "Track 1",
                    duration = 180,
                    trackNumber = 1,
                    artist = ArtistBriefDto(id = 1, name = "Artist"),
                ),
            ),
        )
        coEvery { api.getPlaylistTracks(any(), any()) } returns response

        val result = repository.getPlaylistTracks("playlist-1")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Track 1", result.getOrNull()?.first()?.title)
    }

    @Test
    fun `getPlaylistTracks paginates until all tracks fetched`() = runTest {
        coEvery {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 0, limit = any())
        } returns createTracksResponse(offset = 0, count = 50, total = 120)
        coEvery {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 50, limit = any())
        } returns createTracksResponse(offset = 50, count = 50, total = 120)
        coEvery {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 100, limit = any())
        } returns createTracksResponse(offset = 100, count = 20, total = 120)

        val result = repository.getPlaylistTracks("playlist-1")

        assertTrue(result.isSuccess)
        assertEquals(120, result.getOrNull()?.size)
        coVerify(exactly = 1) {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 0, limit = any())
        }
        coVerify(exactly = 1) {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 50, limit = any())
        }
        coVerify(exactly = 1) {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 100, limit = any())
        }
    }

    @Test
    fun `getPlaylistTracks fetches single page when total is at or below 50`() = runTest {
        coEvery {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 0, limit = any())
        } returns createTracksResponse(offset = 0, count = 10, total = 10)

        val result = repository.getPlaylistTracks("playlist-1")

        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.size)
        coVerify(exactly = 1) {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 0, limit = any())
        }
        coVerify(exactly = 0) {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 50, limit = any())
        }
    }

    @Test
    fun `getPlaylistTracks stops on empty page`() = runTest {
        coEvery {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 0, limit = any())
        } returns createTracksResponse(offset = 0, count = 50, total = 500)
        coEvery {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 50, limit = any())
        } returns createTracksResponse(offset = 50, count = 0, total = 500)

        val result = repository.getPlaylistTracks("playlist-1")

        assertTrue(result.isSuccess)
        assertEquals(50, result.getOrNull()?.size)
        coVerify(exactly = 1) {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 50, limit = any())
        }
        coVerify(exactly = 0) {
            api.getPlaylistTracks(token = any(), playlistId = any(), offset = 100, limit = any())
        }
    }

    @Test
    fun `getUserPlaylists follows cursor across pages`() = runTest {
        coEvery {
            api.getUserPlaylists(token = any(), cursor = "")
        } returns createPlaylistsResponse(50, startIndex = 0, cursor = "c1")
        coEvery {
            api.getUserPlaylists(token = any(), cursor = "c1")
        } returns createPlaylistsResponse(50, startIndex = 50, cursor = "c2")
        coEvery {
            api.getUserPlaylists(token = any(), cursor = "c2")
        } returns createPlaylistsResponse(10, startIndex = 100, cursor = null)

        val result = repository.getUserPlaylists()

        assertTrue(result.isSuccess)
        assertEquals(110, result.getOrNull()?.size)
        coVerify(exactly = 1) { api.getUserPlaylists(token = any(), cursor = "") }
        coVerify(exactly = 1) { api.getUserPlaylists(token = any(), cursor = "c1") }
        coVerify(exactly = 1) { api.getUserPlaylists(token = any(), cursor = "c2") }
    }

    @Test
    fun `getUserPlaylists includes emoji-only titled playlist`() = runTest {
        val emojiResponse = CollectionPlaylistsResponseDto(
            items = listOf(
                CollectionPlaylistEntryDto(
                    itemType = "PLAYLIST",
                    data = PlaylistDataDto(
                        uuid = "playlist-emoji",
                        title = "😀🎵🔥",
                        creator = PlaylistCreatorDto(name = "User"),
                    ),
                ),
            ),
            totalNumberOfItems = 1,
            cursor = null,
        )
        coEvery { api.getUserPlaylists(any()) } returns emojiResponse

        val result = repository.getUserPlaylists()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("😀🎵🔥", result.getOrNull()?.first()?.title)
    }

    private fun createTracksResponse(offset: Int, count: Int, total: Int) = V1TrackListResponseDto(
        limit = 50,
        offset = offset,
        totalNumberOfItems = total,
        items = List(count) { i ->
            TrackDataDto(
                id = (offset + i).toLong(),
                title = "Track ${offset + i}",
                duration = 180,
                trackNumber = offset + i,
                artist = ArtistBriefDto(id = 1, name = "Artist"),
            )
        },
    )

    private fun createPlaylistsResponse(
        count: Int,
        startIndex: Int = 0,
        cursor: String? = null,
    ) = CollectionPlaylistsResponseDto(
        items = List(count) { i ->
            val index = startIndex + i
            CollectionPlaylistEntryDto(
                itemType = "PLAYLIST",
                data = PlaylistDataDto(
                    uuid = "playlist-$index",
                    title = "Playlist $index",
                    description = "Description $index",
                    squareImage = "aabbccdd-1122-3344-5566-77889900${index.toString().padStart(4, '0')}",
                    numberOfTracks = 15,
                    creator = PlaylistCreatorDto(name = "User"),
                ),
            )
        },
        totalNumberOfItems = count,
        cursor = cursor,
    )
}
