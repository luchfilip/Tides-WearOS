package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.core.time.TimeOffsetFormatter
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.domain.mapper.HomeFeedV2Mapper
import dev.tidesapp.wearos.library.domain.repository.HomeRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val api: TidesLibraryApi,
    private val credentialsProvider: CredentialsProvider,
    private val mapper: HomeFeedV2Mapper,
    private val timeOffsetFormatter: TimeOffsetFormatter,
) : HomeRepository {

    private var cachedSections: List<HomeFeedSection>? = null
    private val mutex = Mutex()

    private suspend fun getBearerToken(): Result<String> {
        val result = credentialsProvider.getCredentials(null)
        val token = result.successData?.token
            ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
        return Result.success("Bearer $token")
    }

    override suspend fun getHomeFeed(forceRefresh: Boolean): Result<List<HomeFeedSection>> {
        return mutex.withLock {
            val cached = cachedSections
            if (!forceRefresh && cached != null) {
                return@withLock Result.success(cached)
            }

            try {
                val token = getBearerToken().getOrElse { return@withLock Result.failure(it) }
                val response = api.getHomeFeedV2(
                    token = token,
                    refreshId = timeOffsetFormatter.refreshId(),
                    timeOffset = timeOffsetFormatter.timeOffset(),
                    limit = 20,
                )
                val sections = mapper.map(response)
                cachedSections = sections
                Result.success(sections)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
