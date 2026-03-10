package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.AlbumItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.domain.mapper.toDomain
import dev.tidesapp.wearos.library.domain.repository.AlbumRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val api: TidesLibraryApi,
    private val credentialsProvider: CredentialsProvider,
) : AlbumRepository {

    private var cachedAlbums: List<AlbumItem>? = null
    private val mutex = Mutex()

    private suspend fun getBearerToken(): Result<String> {
        val result = credentialsProvider.getCredentials(null)
        val token = result.successData?.token
            ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
        return Result.success("Bearer $token")
    }

    override suspend fun getUserAlbums(forceRefresh: Boolean): Result<List<AlbumItem>> {
        return mutex.withLock {
            val cached = cachedAlbums
            if (!forceRefresh && cached != null) {
                return@withLock Result.success(cached)
            }

            try {
                val token = getBearerToken().getOrElse { return@withLock Result.failure(it) }
                val response = api.getUserAlbums(token = token)
                val albums = response.items.map { it.toDomain() }
                cachedAlbums = albums
                Result.success(albums)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getAlbumDetail(id: String): Result<AlbumItem> {
        return try {
            val token = getBearerToken().getOrElse { return Result.failure(it) }
            val response = api.getAlbum(token = token, albumId = id)
            Result.success(response.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAlbumTracks(id: String): Result<List<TrackItem>> {
        return try {
            val token = getBearerToken().getOrElse { return Result.failure(it) }
            val response = api.getAlbumTracks(token = token, albumId = id)
            Result.success(response.items.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
