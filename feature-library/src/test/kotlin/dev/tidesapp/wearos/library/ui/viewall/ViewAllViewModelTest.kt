package dev.tidesapp.wearos.library.ui.viewall

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.ViewAllPage
import dev.tidesapp.wearos.library.domain.repository.ViewAllRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
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
class ViewAllViewModelTest {

    private lateinit var repository: ViewAllRepository
    private val testDispatcher = StandardTestDispatcher()

    // Path is URL-encoded as it would be coming off the nav route template.
    private val encodedPath = "home%2Fpages%2FPOPULAR_PLAYLISTS%2Fview-all"
    private val decodedPath = "home/pages/POPULAR_PLAYLISTS/view-all"

    private val savedStateHandle = SavedStateHandle(
        mapOf(
            ViewAllViewModel.ARG_PATH to encodedPath,
            ViewAllViewModel.ARG_TITLE to "Popular%20playlists",
        ),
    )

    private val samplePage = ViewAllPage(
        title = "Popular playlists",
        subtitle = null,
        items = persistentListOf(
            HomeFeedItem.Playlist(
                id = "p-1",
                title = "Deep Focus",
                imageUrl = null,
                creator = "TIDAL",
            ),
            HomeFeedItem.Album(
                id = "a-1",
                title = "Sample Album",
                imageUrl = null,
                artistName = "Sample Artist",
            ),
            HomeFeedItem.Mix(
                id = "m-1",
                title = "Daily Mix 1",
                imageUrl = null,
                subTitle = "For you",
            ),
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init triggers auto-load using decoded path from SavedStateHandle`() = runTest {
        coEvery { repository.getViewAll(decodedPath) } returns Result.success(samplePage)

        val viewModel = ViewAllViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ViewAllUiState.Success)
        coVerify(exactly = 1) { repository.getViewAll(decodedPath) }
    }

    @Test
    fun `load success transitions to Success with the mapped page`() = runTest {
        coEvery { repository.getViewAll(decodedPath) } returns Result.success(samplePage)

        val viewModel = ViewAllViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ViewAllUiState.Success
        assertEquals("Popular playlists", state.page.title)
        assertEquals(3, state.page.items.size)
    }

    @Test
    fun `load failure transitions to Error with the exception message`() = runTest {
        coEvery { repository.getViewAll(decodedPath) } returns
            Result.failure(RuntimeException("Network error"))

        val viewModel = ViewAllViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ViewAllUiState.Error)
        assertEquals("Network error", (state as ViewAllUiState.Error).message)
        assertEquals("Popular playlists", state.headerTitle)
    }

    @Test
    fun `ItemClicked with Album emits NavigateToAlbum effect`() = runTest {
        coEvery { repository.getViewAll(decodedPath) } returns Result.success(samplePage)
        val viewModel = ViewAllViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        val album = HomeFeedItem.Album(
            id = "a-42",
            title = "Test Album",
            imageUrl = null,
            artistName = "Test Artist",
        )

        viewModel.uiEffect.test {
            viewModel.onEvent(ViewAllUiEvent.ItemClicked(album))
            val effect = awaitItem()
            assertTrue(effect is ViewAllUiEffect.NavigateToAlbum)
            assertEquals("a-42", (effect as ViewAllUiEffect.NavigateToAlbum).albumId)
        }
    }

    @Test
    fun `ItemClicked with Mix emits NavigateToMix effect with header metadata`() = runTest {
        coEvery { repository.getViewAll(decodedPath) } returns Result.success(samplePage)
        val viewModel = ViewAllViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        val mix = HomeFeedItem.Mix(
            id = "mix-abc",
            title = "Track Radio",
            imageUrl = "https://images.tidal.com/mix.jpg",
            subTitle = "What Else Is There?",
        )

        viewModel.uiEffect.test {
            viewModel.onEvent(ViewAllUiEvent.ItemClicked(mix))
            val effect = awaitItem() as ViewAllUiEffect.NavigateToMix
            assertEquals("mix-abc", effect.mixId)
            assertEquals("Track Radio", effect.title)
            assertEquals("What Else Is There?", effect.subTitle)
            assertEquals("https://images.tidal.com/mix.jpg", effect.imageUrl)
        }
    }
}
