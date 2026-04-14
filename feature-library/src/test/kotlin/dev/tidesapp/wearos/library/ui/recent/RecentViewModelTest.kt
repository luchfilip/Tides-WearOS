package dev.tidesapp.wearos.library.ui.recent

import app.cash.turbine.test
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.core.domain.playback.PlaybackControl
import dev.tidesapp.wearos.library.domain.repository.RecentActivityRepository
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
class RecentViewModelTest {

    private lateinit var repository: RecentActivityRepository
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
        val tracks = createTestTracks(2)
        coEvery { repository.getRecentTracks() } returns Result.success(tracks)

        val viewModel = RecentViewModel(repository, playbackControl)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RecentUiState.Success)
        assertEquals(2, (state as RecentUiState.Success).tracks.size)
    }

    @Test
    fun `init failure shows Error state`() = runTest {
        coEvery { repository.getRecentTracks() } returns
            Result.failure(RuntimeException("offline"))

        val viewModel = RecentViewModel(repository, playbackControl)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RecentUiState.Error)
        assertEquals("offline", (state as RecentUiState.Error).message)
    }

    @Test
    fun `TrackClicked emits NavigateToNowPlaying effect with correct index`() = runTest {
        val tracks = createTestTracks(3)
        coEvery { repository.getRecentTracks() } returns Result.success(tracks)

        val viewModel = RecentViewModel(repository, playbackControl)
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onEvent(RecentUiEvent.TrackClicked(0))
            val effect = awaitItem()
            assertTrue(effect is RecentUiEffect.NavigateToNowPlaying)
        }
        coVerify(exactly = 1) {
            playbackControl.playTracks(match { it.size == 3 }, 0)
        }
    }

    @Test
    fun `Retry reloads tracks`() = runTest {
        val first = createTestTracks(1)
        val second = createTestTracks(4)
        coEvery { repository.getRecentTracks() } returnsMany listOf(
            Result.success(first),
            Result.success(second),
        )

        val viewModel = RecentViewModel(repository, playbackControl)
        advanceUntilIdle()
        viewModel.onEvent(RecentUiEvent.Retry)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RecentUiState.Success)
        assertEquals(4, (state as RecentUiState.Success).tracks.size)
        coVerify(exactly = 2) { repository.getRecentTracks() }
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
