package dev.tidesapp.wearos.library.ui.tracks

import app.cash.turbine.test
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.TrackFavoritesRepository
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
class TracksViewModelTest {

    private lateinit var repository: TrackFavoritesRepository
    private lateinit var playbackControl: PlaybackControl
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
    fun `init auto-loads tracks and enters Success state`() = runTest {
        val tracks = createTestTracks(3)
        coEvery { repository.getUserFavoriteTracks(false) } returns Result.success(tracks)

        val viewModel = TracksViewModel(repository, playbackControl)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TracksUiState.Success)
        assertEquals(3, (state as TracksUiState.Success).tracks.size)
    }

    @Test
    fun `init failure shows Error state`() = runTest {
        coEvery { repository.getUserFavoriteTracks(false) } returns
            Result.failure(RuntimeException("network down"))

        val viewModel = TracksViewModel(repository, playbackControl)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TracksUiState.Error)
        assertEquals("network down", (state as TracksUiState.Error).message)
    }

    @Test
    fun `TrackClicked emits NavigateToNowPlaying effect and plays from that index`() = runTest {
        val tracks = createTestTracks(3)
        coEvery { repository.getUserFavoriteTracks(false) } returns Result.success(tracks)

        val viewModel = TracksViewModel(repository, playbackControl)
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(TracksUiEvent.TrackClicked(2))
            val effect = awaitItem()
            assertTrue(effect is TracksUiEffect.NavigateToNowPlaying)
        }
        coVerify(exactly = 1) {
            playbackControl.playTracks(match { it.size == 3 }, 2)
        }
    }

    @Test
    fun `Retry reloads tracks with forceRefresh`() = runTest {
        val first = createTestTracks(1)
        val second = createTestTracks(2)
        coEvery { repository.getUserFavoriteTracks(false) } returns Result.success(first)
        coEvery { repository.getUserFavoriteTracks(true) } returns Result.success(second)

        val viewModel = TracksViewModel(repository, playbackControl)
        advanceUntilIdle()
        viewModel.onEvent(TracksUiEvent.Retry)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TracksUiState.Success)
        assertEquals(2, (state as TracksUiState.Success).tracks.size)
        coVerify(exactly = 1) { repository.getUserFavoriteTracks(false) }
        coVerify(exactly = 1) { repository.getUserFavoriteTracks(true) }
    }

    private fun createTestTracks(count: Int): List<TrackItem> = List(count) { index ->
        TrackItem(
            id = "track-$index",
            title = "Track $index",
            artistName = "Artist",
            albumTitle = "Album",
            duration = 200,
            trackNumber = index + 1,
            imageUrl = null,
        )
    }
}
