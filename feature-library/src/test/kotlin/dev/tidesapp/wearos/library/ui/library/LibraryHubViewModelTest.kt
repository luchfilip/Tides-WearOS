package dev.tidesapp.wearos.library.ui.library

import app.cash.turbine.test
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
class LibraryHubViewModelTest {

    private lateinit var viewModel: LibraryHubViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LibraryHubViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state exposes all items in documented order`() = runTest {
        val state = viewModel.uiState.value
        assertTrue(state is LibraryHubUiState.Loaded)
        val items = (state as LibraryHubUiState.Loaded).items
        assertEquals(5, items.size)
        assertEquals(LibraryItem.Playlists, items[0])
        assertEquals(LibraryItem.Albums, items[1])
        assertEquals(LibraryItem.Tracks, items[2])
        assertEquals(LibraryItem.Recent, items[3])
        assertEquals(LibraryItem.Settings, items[4])
    }

    @Test
    fun `clicking Playlists emits NavigateToPlaylists`() = runTest {
        viewModel.uiEffect.test {
            viewModel.onEvent(LibraryHubUiEvent.ItemClicked(LibraryItem.Playlists))
            advanceUntilIdle()
            assertEquals(LibraryHubUiEffect.NavigateToPlaylists, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clicking Albums emits NavigateToAlbums`() = runTest {
        viewModel.uiEffect.test {
            viewModel.onEvent(LibraryHubUiEvent.ItemClicked(LibraryItem.Albums))
            advanceUntilIdle()
            assertEquals(LibraryHubUiEffect.NavigateToAlbums, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clicking Tracks emits NavigateToTracks`() = runTest {
        viewModel.uiEffect.test {
            viewModel.onEvent(LibraryHubUiEvent.ItemClicked(LibraryItem.Tracks))
            advanceUntilIdle()
            assertEquals(LibraryHubUiEffect.NavigateToTracks, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clicking Recent emits NavigateToRecent`() = runTest {
        viewModel.uiEffect.test {
            viewModel.onEvent(LibraryHubUiEvent.ItemClicked(LibraryItem.Recent))
            advanceUntilIdle()
            assertEquals(LibraryHubUiEffect.NavigateToRecent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clicking Settings emits NavigateToSettings`() = runTest {
        viewModel.uiEffect.test {
            viewModel.onEvent(LibraryHubUiEvent.ItemClicked(LibraryItem.Settings))
            advanceUntilIdle()
            assertEquals(LibraryHubUiEffect.NavigateToSettings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
