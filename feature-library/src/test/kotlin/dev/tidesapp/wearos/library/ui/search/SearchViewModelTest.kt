package dev.tidesapp.wearos.library.ui.search

import app.cash.turbine.test
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.ArtistItem
import dev.tidesapp.wearos.core.domain.model.PlaylistItem
import dev.tidesapp.wearos.core.domain.model.SearchResult
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.SearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var repository: SearchRepository
    private lateinit var playbackControl: PlaybackControl
    private lateinit var viewModel: SearchViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        playbackControl = mockk {
            coEvery { playTracks(any(), any()) } returns Result.success(Unit)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Initial`() {
        viewModel = SearchViewModel(repository, playbackControl)
        assertEquals(SearchUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun `Search successfully returns results`() = runTest {
        val searchResult = createTestSearchResult()
        coEvery { repository.search("test") } returns Result.success(searchResult)

        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search("test"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Success)
        val successState = state as SearchUiState.Success
        assertEquals(2, successState.result.albums.size)
        assertEquals(1, successState.result.tracks.size)
    }

    @Test
    fun `Search with blank query does not call API`() = runTest {
        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search(""))
        advanceUntilIdle()

        assertEquals(SearchUiState.Initial, viewModel.uiState.value)
        coVerify(exactly = 0) { repository.search(any()) }
    }

    @Test
    fun `Search with whitespace-only query does not call API`() = runTest {
        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search("   "))
        advanceUntilIdle()

        assertEquals(SearchUiState.Initial, viewModel.uiState.value)
        coVerify(exactly = 0) { repository.search(any()) }
    }

    @Test
    fun `Search failure shows Error state`() = runTest {
        coEvery { repository.search("test") } returns Result.failure(
            RuntimeException("Search failed")
        )

        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search("test"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Error)
        assertEquals("Search failed", (state as SearchUiState.Error).message)
    }

    @Test
    fun `Search with no results shows Empty state`() = runTest {
        val emptyResult = SearchResult(
            albums = persistentListOf(),
            tracks = persistentListOf(),
            playlists = persistentListOf(),
            artists = persistentListOf(),
        )
        coEvery { repository.search("xyz") } returns Result.success(emptyResult)

        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search("xyz"))
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `Album ResultClicked emits NavigateToAlbumDetail effect`() = runTest {
        val searchResult = createTestSearchResult()
        coEvery { repository.search("test") } returns Result.success(searchResult)

        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search("test"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(
                SearchUiEvent.ResultClicked(SearchResultType.Album("album-0"))
            )
            val effect = awaitItem()
            assertTrue(effect is SearchUiEffect.NavigateToAlbumDetail)
            assertEquals("album-0", (effect as SearchUiEffect.NavigateToAlbumDetail).albumId)
        }
    }

    @Test
    fun `Track ResultClicked plays queue and emits NavigateToNowPlaying`() = runTest {
        val searchResult = createTestSearchResult()
        coEvery { repository.search("test") } returns Result.success(searchResult)

        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search("test"))
        advanceUntilIdle()

        val tappedTrack = searchResult.tracks.first()
        viewModel.uiEffect.test {
            viewModel.onEvent(SearchUiEvent.ResultClicked(SearchResultType.Track(tappedTrack)))
            val effect = awaitItem()
            assertTrue(effect is SearchUiEffect.NavigateToNowPlaying)
        }
        coVerify(exactly = 1) { playbackControl.playTracks(listOf(tappedTrack), 0) }
    }

    @Test
    fun `Playlist ResultClicked emits NavigateToPlaylistDetail effect`() = runTest {
        viewModel = SearchViewModel(repository, playbackControl)

        viewModel.uiEffect.test {
            viewModel.onEvent(
                SearchUiEvent.ResultClicked(SearchResultType.Playlist("playlist-0"))
            )
            val effect = awaitItem()
            assertTrue(effect is SearchUiEffect.NavigateToPlaylistDetail)
            assertEquals(
                "playlist-0",
                (effect as SearchUiEffect.NavigateToPlaylistDetail).playlistId
            )
        }
    }

    @Test
    fun `ClearSearch resets to Initial state`() = runTest {
        val searchResult = createTestSearchResult()
        coEvery { repository.search("test") } returns Result.success(searchResult)

        viewModel = SearchViewModel(repository, playbackControl)
        viewModel.onEvent(SearchUiEvent.Search("test"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SearchUiState.Success)

        viewModel.onEvent(SearchUiEvent.ClearSearch)
        assertEquals(SearchUiState.Initial, viewModel.uiState.value)
    }

    private fun createTestSearchResult(): SearchResult {
        val albums = List(2) { index ->
            AlbumItem(
                id = "album-$index",
                title = "Album $index",
                artistName = "Artist $index",
                imageUrl = "https://img.tidal.com/album-$index",
                releaseDate = "2024-01-01",
                numberOfTracks = 10,
            )
        }
        val tracks = List(1) { index ->
            TrackItem(
                id = "track-$index",
                title = "Track $index",
                artistName = "Artist $index",
                albumTitle = "Album $index",
                duration = 200,
                trackNumber = 1,
                imageUrl = "https://img.tidal.com/track-$index",
            )
        }
        return SearchResult(
            albums = albums.toImmutableList(),
            tracks = tracks.toImmutableList(),
            playlists = persistentListOf(),
            artists = persistentListOf(),
        )
    }
}
