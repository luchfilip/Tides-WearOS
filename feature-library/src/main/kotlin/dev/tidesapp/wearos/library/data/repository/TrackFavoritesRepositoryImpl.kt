package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.domain.mapper.toDomain
import dev.tidesapp.wearos.library.domain.repository.TrackFavoritesRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackFavoritesRepositoryImpl @Inject constructor(
    private val api: TidesLibraryApi,
    private val credentialsProvider: CredentialsProvider,
) : TrackFavoritesRepository {

    private var cachedFavorites: List<TrackItem>? = null
    private val mutex = Mutex()

    private suspend fun getAuth(): Result<Auth> {
        val credentials = credentialsProvider.getCredentials(null).successData
            ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
        val token = credentials.token
            ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
        val userId = credentials.userId
            ?: return Result.failure(RuntimeException("No user id on credentials"))
        if (userId.isBlank()) {
            return Result.failure(RuntimeException("No user id on credentials"))
        }
        return Result.success(Auth(bearer = "Bearer $token", userId = userId))
    }

    override suspend fun getUserFavoriteTracks(forceRefresh: Boolean): Result<List<TrackItem>> {
        return mutex.withLock {
            val cached = cachedFavorites
            if (!forceRefresh && cached != null) {
                return@withLock Result.success(cached)
            }
            try {
                val auth = getAuth().getOrElse { return@withLock Result.failure(it) }
                val response = api.getUserFavoriteTracks(
                    token = auth.bearer,
                    userId = auth.userId,
                )
                val tracks = response.items.mapNotNull { it.item?.toDomain() }
                cachedFavorites = tracks
                Result.success(tracks)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private data class Auth(val bearer: String, val userId: String)
}
