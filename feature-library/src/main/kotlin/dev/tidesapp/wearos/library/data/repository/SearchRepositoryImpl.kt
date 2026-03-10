package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.SearchResult
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.domain.mapper.toDomain
import dev.tidesapp.wearos.library.domain.repository.SearchRepository
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val api: TidesLibraryApi,
    private val credentialsProvider: CredentialsProvider,
) : SearchRepository {

    private suspend fun getBearerToken(): Result<String> {
        val result = credentialsProvider.getCredentials(null)
        val token = result.successData?.token
            ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
        return Result.success("Bearer $token")
    }

    override suspend fun search(query: String): Result<SearchResult> {
        return try {
            val token = getBearerToken().getOrElse { return Result.failure(it) }
            val response = api.search(token = token, query = query)
            val result = SearchResult(
                albums = (response.albums?.items?.map { it.toDomain() } ?: emptyList()).toImmutableList(),
                tracks = (response.tracks?.items?.map { it.toDomain() } ?: emptyList()).toImmutableList(),
                playlists = (response.playlists?.items?.map { it.toDomain() } ?: emptyList()).toImmutableList(),
                artists = (response.artists?.items?.map { it.toDomain() } ?: emptyList()).toImmutableList(),
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
