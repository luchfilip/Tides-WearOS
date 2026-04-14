package dev.tidesapp.wearos.library.ui.home

import app.cash.turbine.test
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.library.domain.repository.HomeRepository
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
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    private fun buildViewModel(
        repository: HomeRepository,
    ): HomeViewModel = HomeViewModel(repository)

    private val fakeSections = listOf(
        HomeFeedSection(
            title = "Recently Played",
            items = persistentListOf(
                HomeFeedItem.Album(
                    id = "album-1",
                    title = "Test Album",
                    imageUrl = null,
                    artistName = "Test Artist",
                ),
            ),
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Initial`() {
        viewModel = buildViewModel(FakeHomeRepository())
        assertEquals(HomeUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun `LoadHome transitions to Success with feed sections`() = runTest {
        viewModel = buildViewModel(FakeHomeRepository(Result.success(fakeSections)))

        viewModel.onEvent(HomeUiEvent.LoadHome)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        val sections = (state as HomeUiState.Success).feedSections
        assertEquals(1, sections.size)
        assertEquals("Recently Played", sections[0].title)
    }

    @Test
    fun `LoadHome failure transitions to Error`() = runTest {
        viewModel = buildViewModel(
            FakeHomeRepository(Result.failure(RuntimeException("Network error"))),
        )

        viewModel.onEvent(HomeUiEvent.LoadHome)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Error)
        assertEquals("Network error", (state as HomeUiState.Error).message)
    }

    @Test
    fun `FeedItemClicked with Album emits NavigateToAlbum effect`() = runTest {
        viewModel = buildViewModel(FakeHomeRepository(Result.success(fakeSections)))
        viewModel.onEvent(HomeUiEvent.LoadHome)
        advanceUntilIdle()

        val album = HomeFeedItem.Album(
            id = "album-1",
            title = "Test Album",
            imageUrl = null,
            artistName = "Test Artist",
        )

        viewModel.uiEffect.test {
            viewModel.onEvent(HomeUiEvent.FeedItemClicked(album))
            val effect = awaitItem()
            assertTrue(effect is HomeUiEffect.NavigateToAlbum)
            assertEquals("album-1", (effect as HomeUiEffect.NavigateToAlbum).albumId)
        }
    }

    @Test
    fun `FeedItemClicked with Playlist emits NavigateToPlaylist effect`() = runTest {
        viewModel = buildViewModel(FakeHomeRepository(Result.success(fakeSections)))
        viewModel.onEvent(HomeUiEvent.LoadHome)
        advanceUntilIdle()

        val playlist = HomeFeedItem.Playlist(
            id = "playlist-1",
            title = "Test Playlist",
            imageUrl = null,
            creator = "TIDAL",
        )

        viewModel.uiEffect.test {
            viewModel.onEvent(HomeUiEvent.FeedItemClicked(playlist))
            val effect = awaitItem()
            assertTrue(effect is HomeUiEffect.NavigateToPlaylist)
            assertEquals("playlist-1", (effect as HomeUiEffect.NavigateToPlaylist).playlistId)
        }
    }

    private class FakeHomeRepository(
        private val result: Result<List<HomeFeedSection>> = Result.success(emptyList()),
    ) : HomeRepository {
        override suspend fun getHomeFeed(forceRefresh: Boolean): Result<List<HomeFeedSection>> {
            return result
        }
    }
}
