package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.ViewAllPage
import dev.tidesapp.wearos.core.time.TimeOffsetFormatter
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.dto.ViewAllResponseDto
import dev.tidesapp.wearos.library.domain.mapper.HomeFeedV2Mapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ViewAllRepositoryImplTest {

    private lateinit var api: TidesLibraryApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var mapper: HomeFeedV2Mapper
    private lateinit var timeOffsetFormatter: TimeOffsetFormatter
    private lateinit var repository: ViewAllRepositoryImpl

    private val fixedRefreshId: Long = 1_728_921_296_000L
    private val fixedTimeOffset: String = "-04:00"

    private val dto: ViewAllResponseDto = ViewAllResponseDto(
        title = "Popular playlists",
        subtitle = null,
        itemLayout = "GRID_CARD",
        items = emptyList(),
    )

    private val mappedPage = ViewAllPage(
        title = "Popular playlists",
        subtitle = null,
        items = persistentListOf(
            HomeFeedItem.Playlist(
                id = "p-1",
                title = "Deep Focus",
                imageUrl = null,
                creator = "TIDAL",
            ),
        ),
    )

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
        mapper = mockk {
            every { mapViewAll(any()) } returns mappedPage
        }
        timeOffsetFormatter = mockk {
            every { refreshId() } returns fixedRefreshId
            every { timeOffset() } returns fixedTimeOffset
        }
        repository = ViewAllRepositoryImpl(api, credentialsProvider, mapper, timeOffsetFormatter)
    }

    @Test
    fun `getViewAll prepends v2 prefix and passes refreshId and timeOffset with offset 0 limit 50`() =
        runTest {
            val urlSlot = slot<String>()
            coEvery {
                api.getViewAll(
                    token = "Bearer fake-jwt-token",
                    url = capture(urlSlot),
                    refreshId = fixedRefreshId,
                    timeOffset = fixedTimeOffset,
                    offset = 0,
                    limit = 50,
                )
            } returns dto

            val result = repository.getViewAll("home/pages/POPULAR_PLAYLISTS/view-all?itemId=abc")

            assertTrue(result.isSuccess)
            assertEquals(mappedPage, result.getOrNull())
            // Retrofit's @Url will carry the existing `?itemId=abc` through and append the
            // additional @Query params (refreshId, timeOffset, offset, limit) to it.
            assertEquals(
                "v2/home/pages/POPULAR_PLAYLISTS/view-all?itemId=abc",
                urlSlot.captured,
            )
            coVerify(exactly = 1) { mapper.mapViewAll(dto) }
        }

    @Test
    fun `getViewAll returns failure and skips API when credentials token is null`() = runTest {
        credentialsProvider = mockk {
            coEvery { getCredentials(any()) } returns mockk {
                every { successData } returns mockk {
                    every { token } returns null
                }
            }
        }
        repository = ViewAllRepositoryImpl(api, credentialsProvider, mapper, timeOffsetFormatter)

        val result = repository.getViewAll("home/pages/POPULAR_PLAYLISTS/view-all")

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertNotNull(err)
        assertEquals("Failed to obtain credentials", err!!.message)
        coVerify(exactly = 0) { api.getViewAll(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { mapper.mapViewAll(any()) }
    }

    @Test
    fun `getViewAll wraps API exceptions as failure`() = runTest {
        coEvery {
            api.getViewAll(any(), any(), any(), any(), any(), any())
        } throws IOException("boom")

        val result = repository.getViewAll("home/pages/POPULAR_PLAYLISTS/view-all")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        coVerify(exactly = 0) { mapper.mapViewAll(any()) }
    }

    @Test
    fun `getViewAll returns Success with empty items when server returns empty list`() = runTest {
        val emptyDto = ViewAllResponseDto(
            title = "Popular playlists",
            subtitle = null,
            itemLayout = "GRID_CARD",
            items = emptyList(),
        )
        val emptyPage = ViewAllPage(
            title = "Popular playlists",
            subtitle = null,
            items = persistentListOf(),
        )
        every { mapper.mapViewAll(emptyDto) } returns emptyPage
        coEvery {
            api.getViewAll(any(), any(), any(), any(), any(), any())
        } returns emptyDto

        val result = repository.getViewAll("home/pages/POPULAR_PLAYLISTS/view-all")

        assertTrue(result.isSuccess)
        assertEquals(emptyPage, result.getOrNull())
        assertTrue(result.getOrNull()!!.items.isEmpty())
    }
}
