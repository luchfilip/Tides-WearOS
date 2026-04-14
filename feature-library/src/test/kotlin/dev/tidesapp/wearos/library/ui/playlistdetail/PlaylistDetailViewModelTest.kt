package dev.tidesapp.wearos.library.ui.playlistdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.tidesapp.wearos.core.domain.model.PlaylistItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.mockk
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
class PlaylistDetailViewModelTest {

    private lateinit var repository: PlaylistRepository
    private lateinit var playbackControl: PlaybackControl
    private lateinit var viewModel: PlaylistDetailViewModel
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
        viewModel = PlaylistDetailViewModel(repository, playbackControl, SavedStateHandle())
        assertEquals(PlaylistDetailUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun `LoadPlaylistDetail successfully loads playlist and tracks`() = runTest {
        val playlists = listOf(createTestPlaylist())
        val tracks = createTestTracks(3)
        coEvery { repository.getUserPlaylists(false) } returns Result.success(playlists)
        coEvery { repository.getPlaylistTracks("playlist-1") } returns Result.success(tracks)

        viewModel = PlaylistDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(PlaylistDetailUiEvent.LoadPlaylistDetail("playlist-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlaylistDetailUiState.Success)
        val successState = state as PlaylistDetailUiState.Success
        assertEquals("Test Playlist", successState.playlist.title)
        assertEquals(3, successState.tracks.size)
    }

    @Test
    fun `LoadPlaylistDetail failure shows Error state`() = runTest {
        coEvery { repository.getUserPlaylists(false) } returns Result.failure(
            RuntimeException("Not found")
        )
        coEvery { repository.getPlaylistTracks("playlist-1") } returns Result.failure(
            RuntimeException("Not found")
        )

        viewModel = PlaylistDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(PlaylistDetailUiEvent.LoadPlaylistDetail("playlist-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlaylistDetailUiState.Error)
        assertEquals("Not found", (state as PlaylistDetailUiState.Error).message)
    }

    @Test
    fun `PlayTrack emits NavigateToNowPlaying effect`() = runTest {
        val playlists = listOf(createTestPlaylist())
        val tracks = createTestTracks(2)
        coEvery { repository.getUserPlaylists(false) } returns Result.success(playlists)
        coEvery { repository.getPlaylistTracks("playlist-1") } returns Result.success(tracks)

        viewModel = PlaylistDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(PlaylistDetailUiEvent.LoadPlaylistDetail("playlist-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(PlaylistDetailUiEvent.PlayTrack(tracks[1]))
            val effect = awaitItem()
            assertTrue(effect is PlaylistDetailUiEffect.NavigateToNowPlaying)
        }
    }

    @Test
    fun `PlayAll emits NavigateToNowPlaying with first track`() = runTest {
        val playlists = listOf(createTestPlaylist())
        val tracks = createTestTracks(3)
        coEvery { repository.getUserPlaylists(false) } returns Result.success(playlists)
        coEvery { repository.getPlaylistTracks("playlist-1") } returns Result.success(tracks)

        viewModel = PlaylistDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(PlaylistDetailUiEvent.LoadPlaylistDetail("playlist-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(PlaylistDetailUiEvent.PlayAll)
            val effect = awaitItem()
            assertTrue(effect is PlaylistDetailUiEffect.NavigateToNowPlaying)
        }
    }

    @Test
    fun `playlist not found in list shows Error`() = runTest {
        val playlists = listOf(createTestPlaylist())
        val tracks = createTestTracks(2)
        coEvery { repository.getUserPlaylists(false) } returns Result.success(playlists)
        coEvery { repository.getPlaylistTracks("nonexistent") } returns Result.success(tracks)

        viewModel = PlaylistDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(PlaylistDetailUiEvent.LoadPlaylistDetail("nonexistent"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlaylistDetailUiState.Error)
        assertEquals("Failed to load playlist", (state as PlaylistDetailUiState.Error).message)
    }

    private fun createTestPlaylist() = PlaylistItem(
        id = "playlist-1",
        title = "Test Playlist",
        description = "A test playlist",
        imageUrl = "https://img.tidal.com/playlist-1",
        numberOfTracks = 10,
        creator = "Test Creator",
    )

    private fun createTestTracks(count: Int): List<TrackItem> {
        return List(count) { index ->
            TrackItem(
                id = "track-$index",
                title = "Track $index",
                artistName = "Test Artist",
                albumTitle = "Test Album",
                duration = 200 + index * 30,
                trackNumber = index + 1,
                imageUrl = "https://img.tidal.com/track-$index",
            )
        }
    }
}
