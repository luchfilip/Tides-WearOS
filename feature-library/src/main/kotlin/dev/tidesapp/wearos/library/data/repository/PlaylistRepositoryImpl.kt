package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.PlaylistItem
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.domain.mapper.toDomain
import dev.tidesapp.wearos.library.domain.repository.PlaylistRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val api: TidesLibraryApi,
    private val credentialsProvider: CredentialsProvider,
) : PlaylistRepository {

    private var cachedPlaylists: List<PlaylistItem>? = null
    private val mutex = Mutex()

    private suspend fun getBearerToken(): Result<String> {
        val result = credentialsProvider.getCredentials(null)
        val token = result.successData?.token
            ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
        return Result.success("Bearer $token")
    }

    override suspend fun getUserPlaylists(forceRefresh: Boolean): Result<List<PlaylistItem>> {
        return mutex.withLock {
            val cached = cachedPlaylists
            if (!forceRefresh && cached != null) {
                return@withLock Result.success(cached)
            }

            try {
                val token = getBearerToken().getOrElse { return@withLock Result.failure(it) }
                val response = api.getUserPlaylists(token = token)
                val playlists = response.items.map { it.toDomain() }
                cachedPlaylists = playlists
                Result.success(playlists)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getPlaylist(id: String): Result<PlaylistItem> {
        return try {
            val token = getBearerToken().getOrElse { return Result.failure(it) }
            val response = api.getPlaylist(token = token, playlistId = id)
            Result.success(response.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPlaylistTracks(id: String): Result<List<TrackItem>> {
        return try {
            val token = getBearerToken().getOrElse { return Result.failure(it) }
            val response = api.getPlaylistTracks(token = token, playlistId = id)
            Result.success(response.items.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
