package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.ViewAllPage
import dev.tidesapp.wearos.core.time.TimeOffsetFormatter
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.domain.mapper.HomeFeedV2Mapper
import dev.tidesapp.wearos.library.domain.repository.ViewAllRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewAllRepositoryImpl @Inject constructor(
    private val api: TidesLibraryApi,
    private val credentialsProvider: CredentialsProvider,
    private val mapper: HomeFeedV2Mapper,
    private val timeOffsetFormatter: TimeOffsetFormatter,
) : ViewAllRepository {

    private suspend fun getBearerToken(): Result<String> {
        val result = credentialsProvider.getCredentials(null)
        val token = result.successData?.token
            ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
        return Result.success("Bearer $token")
    }

    override suspend fun getViewAll(viewAllPath: String): Result<ViewAllPage> {
        return try {
            val token = getBearerToken().getOrElse { return Result.failure(it) }
            // The server returns `viewAll` as a relative path WITHOUT the `/v2/` prefix
            // (e.g. "home/pages/POPULAR_PLAYLISTS/view-all"). We prepend "v2/" here so the
            // call site resolves against the base URL correctly. Retrofit's @Url preserves
            // any existing query segment (e.g. "...view-all?itemId=abc") and merges any
            // additional @Query params on top.
            val url = "v2/" + viewAllPath.trimStart('/')
            val response = api.getViewAll(
                token = token,
                url = url,
                refreshId = timeOffsetFormatter.refreshId(),
                timeOffset = timeOffsetFormatter.timeOffset(),
            )
            Result.success(mapper.mapViewAll(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
