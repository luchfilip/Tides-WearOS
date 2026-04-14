package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.core.time.TimeOffsetFormatter
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2ResponseDto
import dev.tidesapp.wearos.library.domain.mapper.HomeFeedV2Mapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HomeRepositoryImplTest {

    private lateinit var api: TidesLibraryApi
    private lateinit var credentialsProvider: CredentialsProvider
    private lateinit var mapper: HomeFeedV2Mapper
    private lateinit var timeOffsetFormatter: TimeOffsetFormatter
    private lateinit var repository: HomeRepositoryImpl

    private val fixedRefreshId: Long = 1_728_921_296_000L
    private val fixedTimeOffset: String = "-04:00"

    private val dto: HomeFeedV2ResponseDto = mockk(relaxed = true)
    private val mappedSections: List<HomeFeedSection> = listOf(
        HomeFeedSection(
            title = "Custom mixes",
            items = persistentListOf(
                HomeFeedItem.Mix(
                    id = "mix-1",
                    title = "Daily Discovery",
                    imageUrl = "https://example/mix.jpg",
                    subTitle = "Your mix",
                ),
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
            every { map(any()) } returns mappedSections
        }
        timeOffsetFormatter = mockk {
            every { refreshId() } returns fixedRefreshId
            every { timeOffset() } returns fixedTimeOffset
        }
        repository = HomeRepositoryImpl(api, credentialsProvider, mapper, timeOffsetFormatter)
    }

    @Test
    fun `getHomeFeed fetches via v2 endpoint with bearer token and maps the response on cold cache`() = runTest {
        coEvery {
            api.getHomeFeedV2(
                token = "Bearer fake-jwt-token",
                refreshId = fixedRefreshId,
                timeOffset = fixedTimeOffset,
                limit = 20,
            )
        } returns dto

        val result = repository.getHomeFeed(forceRefresh = false)

        assertTrue(result.isSuccess)
        assertEquals(mappedSections, result.getOrNull())
        coVerify(exactly = 1) {
            api.getHomeFeedV2(
                token = "Bearer fake-jwt-token",
                refreshId = fixedRefreshId,
                timeOffset = fixedTimeOffset,
                limit = 20,
            )
        }
        coVerify(exactly = 1) { mapper.map(dto) }
    }

    @Test
    fun `getHomeFeed returns cached sections on second call without hitting the API`() = runTest {
        coEvery { api.getHomeFeedV2(any(), any(), any(), any()) } returns dto

        val first = repository.getHomeFeed(forceRefresh = false)
        val second = repository.getHomeFeed(forceRefresh = false)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals(mappedSections, second.getOrNull())
        coVerify(exactly = 1) { api.getHomeFeedV2(any(), any(), any(), any()) }
        coVerify(exactly = 1) { mapper.map(any()) }
    }

    @Test
    fun `getHomeFeed with forceRefresh bypasses cache and calls the API again`() = runTest {
        coEvery { api.getHomeFeedV2(any(), any(), any(), any()) } returns dto

        repository.getHomeFeed(forceRefresh = false)
        val refreshed = repository.getHomeFeed(forceRefresh = true)

        assertTrue(refreshed.isSuccess)
        coVerify(exactly = 2) { api.getHomeFeedV2(any(), any(), any(), any()) }
        coVerify(exactly = 2) { mapper.map(any()) }
    }

    @Test
    fun `getHomeFeed returns failure and skips API when credentials token is null`() = runTest {
        credentialsProvider = mockk {
            coEvery { getCredentials(any()) } returns mockk {
                every { successData } returns mockk {
                    every { token } returns null
                }
            }
        }
        repository = HomeRepositoryImpl(api, credentialsProvider, mapper, timeOffsetFormatter)

        val result = repository.getHomeFeed(forceRefresh = false)

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertNotNull(err)
        assertEquals("Failed to obtain credentials", err!!.message)
        coVerify(exactly = 0) { api.getHomeFeedV2(any(), any(), any(), any()) }
        coVerify(exactly = 0) { mapper.map(any()) }
    }

    @Test
    fun `getHomeFeed wraps API exceptions as failure and leaves cache untouched so next call retries`() = runTest {
        coEvery {
            api.getHomeFeedV2(any(), any(), any(), any())
        } throws IOException("boom") andThen dto

        val failed = repository.getHomeFeed(forceRefresh = false)
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull() is IOException)

        // Cache must be untouched — subsequent call retries and succeeds.
        val retried = repository.getHomeFeed(forceRefresh = false)
        assertTrue(retried.isSuccess)
        assertEquals(mappedSections, retried.getOrNull())

        coVerify(exactly = 2) { api.getHomeFeedV2(any(), any(), any(), any()) }
    }
}
