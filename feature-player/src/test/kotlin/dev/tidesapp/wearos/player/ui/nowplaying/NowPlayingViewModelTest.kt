package dev.tidesapp.wearos.player.ui.nowplaying

import app.cash.turbine.test
import dev.tidesapp.wearos.player.domain.model.NowPlayingInfo
import dev.tidesapp.wearos.player.domain.repository.PlayerRepository
import dev.tidesapp.wearos.player.domain.repository.PlayerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class NowPlayingViewModelTest {

    private lateinit var playerRepository: PlayerRepository
    private val playerStateFlow = MutableStateFlow(PlayerState())
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        playerRepository = mockk(relaxed = true)
        every { playerRepository.playerState } returns playerStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Initial when no track`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)
        advanceUntilIdle()

        assertEquals(NowPlayingUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun `state becomes Playing when track is set`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)

        viewModel.uiState.test {
            assertEquals(NowPlayingUiState.Initial, awaitItem())

            playerStateFlow.value = PlayerState(
                track = createTestTrack(),
                isPlaying = true,
                currentPositionMs = 30_000,
                durationMs = 180_000,
            )

            val state = awaitItem()
            assertTrue(state is NowPlayingUiState.Playing)
            val playing = state as NowPlayingUiState.Playing
            assertEquals("Test Track", playing.trackTitle)
            assertEquals("Test Artist", playing.artistName)
            assertEquals(true, playing.isPlaying)
            assertEquals(30_000L, playing.progressMs)
            assertEquals(180_000L, playing.durationMs)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PlayPause event calls playerRepository playPause`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)
        advanceUntilIdle()

        viewModel.onEvent(NowPlayingUiEvent.PlayPause)

        verify(exactly = 1) { playerRepository.playPause() }
    }

    @Test
    fun `SkipNext event calls playerRepository skipNext`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)
        advanceUntilIdle()

        viewModel.onEvent(NowPlayingUiEvent.SkipNext)

        verify(exactly = 1) { playerRepository.skipNext() }
    }

    @Test
    fun `SkipPrevious event calls playerRepository skipPrevious`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)
        advanceUntilIdle()

        viewModel.onEvent(NowPlayingUiEvent.SkipPrevious)

        verify(exactly = 1) { playerRepository.skipPrevious() }
    }

    @Test
    fun `SeekTo event calls playerRepository seekTo`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)
        advanceUntilIdle()

        viewModel.onEvent(NowPlayingUiEvent.SeekTo(60_000))

        verify(exactly = 1) { playerRepository.seekTo(60_000) }
    }

    @Test
    fun `error state sends ShowError effect via Channel`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)
        viewModel.onEvent(NowPlayingUiEvent.ObservePlayerState)
        advanceUntilIdle()

        viewModel.uiEffect.test {
            playerStateFlow.value = PlayerState(error = "Playback failed")
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is NowPlayingUiEffect.ShowError)
            assertEquals("Playback failed", (effect as NowPlayingUiEffect.ShowError).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error state maps to Error UiState`() = runTest {
        val viewModel = NowPlayingViewModel(playerRepository)

        viewModel.uiState.test {
            assertEquals(NowPlayingUiState.Initial, awaitItem())

            playerStateFlow.value = PlayerState(error = "Network error")

            val state = awaitItem()
            assertTrue(state is NowPlayingUiState.Error)
            assertEquals("Network error", (state as NowPlayingUiState.Error).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createTestTrack() = NowPlayingInfo(
        trackId = "track-1",
        title = "Test Track",
        artistName = "Test Artist",
        albumTitle = "Test Album",
        imageUrl = "https://example.com/art.jpg",
        durationMs = 180_000,
    )
}
