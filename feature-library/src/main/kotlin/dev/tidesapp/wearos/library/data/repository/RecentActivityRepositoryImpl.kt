package dev.tidesapp.wearos.library.data.repository

import com.tidal.sdk.auth.CredentialsProvider
import dev.tidesapp.wearos.core.domain.model.TrackItem
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.dto.TrackDataDto
import dev.tidesapp.wearos.library.domain.mapper.toDomain
import dev.tidesapp.wearos.library.domain.repository.RecentActivityRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentActivityRepositoryImpl @Inject constructor(
    private val api: TidesLibraryApi,
    private val credentialsProvider: CredentialsProvider,
) : RecentActivityRepository {

    // Activity entries arrive as polymorphic JSON keyed off the top-level `type`
    // discriminator. We decode TRACK payloads on-demand with a local Json that
    // tolerates the extra fields the favorites view drops.
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getRecentTracks(): Result<List<TrackItem>> {
        return try {
            val credentials = credentialsProvider.getCredentials(null).successData
                ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
            val token = credentials.token
                ?: return Result.failure(RuntimeException("Failed to obtain credentials"))
            val userId = credentials.userId
            if (userId.isNullOrBlank()) {
                return Result.failure(RuntimeException("No user id on credentials"))
            }

            val response = api.getUserActivity(
                token = "Bearer $token",
                userId = userId,
            )
            val tracks = response.items
                .asSequence()
                .filter { it.type == "TRACK" && it.item != null }
                .mapNotNull { entry ->
                    runCatching { json.decodeFromJsonElement(TrackDataDto.serializer(), entry.item!!) }
                        .getOrNull()
                }
                .map { it.toDomain() }
                .toList()
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
