package dev.tidesapp.wearos.library.ui.albumdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.AlbumRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
class AlbumDetailViewModelTest {

    private lateinit var repository: AlbumRepository
    private lateinit var playbackControl: PlaybackControl
    private lateinit var viewModel: AlbumDetailViewModel
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
        viewModel = AlbumDetailViewModel(repository, playbackControl, SavedStateHandle())
        assertEquals(AlbumDetailUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun `LoadAlbumDetail successfully loads album and tracks`() = runTest {
        val album = createTestAlbum()
        val tracks = createTestTracks(3)
        coEvery { repository.getAlbumDetail("album-1") } returns Result.success(album)
        coEvery { repository.getAlbumTracks("album-1") } returns Result.success(tracks)

        viewModel = AlbumDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(AlbumDetailUiEvent.LoadAlbumDetail("album-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AlbumDetailUiState.Success)
        val successState = state as AlbumDetailUiState.Success
        assertEquals("Test Album", successState.album.title)
        assertEquals(3, successState.tracks.size)
    }

    @Test
    fun `LoadAlbumDetail failure shows Error state`() = runTest {
        coEvery { repository.getAlbumDetail("album-1") } returns Result.failure(
            RuntimeException("Not found")
        )
        coEvery { repository.getAlbumTracks("album-1") } returns Result.failure(
            RuntimeException("Not found")
        )

        viewModel = AlbumDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(AlbumDetailUiEvent.LoadAlbumDetail("album-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AlbumDetailUiState.Error)
        assertEquals("Not found", (state as AlbumDetailUiState.Error).message)
    }

    @Test
    fun `PlayTrack plays full queue at tapped track's index and navigates`() = runTest {
        val album = createTestAlbum()
        val tracks = createTestTracks(3)
        coEvery { repository.getAlbumDetail("album-1") } returns Result.success(album)
        coEvery { repository.getAlbumTracks("album-1") } returns Result.success(tracks)

        viewModel = AlbumDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(AlbumDetailUiEvent.LoadAlbumDetail("album-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(AlbumDetailUiEvent.PlayTrack(tracks[2]))
            val effect = awaitItem()
            assertTrue(effect is AlbumDetailUiEffect.NavigateToNowPlaying)
        }
        coVerify(exactly = 1) { playbackControl.playTracks(tracks, 2) }
    }

    @Test
    fun `PlayAll plays full queue from index 0 and navigates`() = runTest {
        val album = createTestAlbum()
        val tracks = createTestTracks(3)
        coEvery { repository.getAlbumDetail("album-1") } returns Result.success(album)
        coEvery { repository.getAlbumTracks("album-1") } returns Result.success(tracks)

        viewModel = AlbumDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(AlbumDetailUiEvent.LoadAlbumDetail("album-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(AlbumDetailUiEvent.PlayAll)
            val effect = awaitItem()
            assertTrue(effect is AlbumDetailUiEffect.NavigateToNowPlaying)
        }
        coVerify(exactly = 1) { playbackControl.playTracks(tracks, 0) }
    }

    @Test
    fun `PlayTrack emits ShowError when playback fails`() = runTest {
        val album = createTestAlbum()
        val tracks = createTestTracks(2)
        coEvery { repository.getAlbumDetail("album-1") } returns Result.success(album)
        coEvery { repository.getAlbumTracks("album-1") } returns Result.success(tracks)
        coEvery { playbackControl.playTracks(any(), any()) } returns
            Result.failure(RuntimeException("network down"))

        viewModel = AlbumDetailViewModel(repository, playbackControl, SavedStateHandle())
        viewModel.onEvent(AlbumDetailUiEvent.LoadAlbumDetail("album-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(AlbumDetailUiEvent.PlayTrack(tracks[0]))
            val effect = awaitItem()
            assertTrue(effect is AlbumDetailUiEffect.ShowError)
            assertEquals("network down", (effect as AlbumDetailUiEffect.ShowError).message)
        }
    }

    private fun createTestAlbum() = AlbumItem(
        id = "album-1",
        title = "Test Album",
        artistName = "Test Artist",
        imageUrl = "https://img.tidal.com/album-1",
        releaseDate = "2024-01-01",
        numberOfTracks = 10,
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
