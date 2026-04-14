package dev.tidesapp.wearos.library.domain.repository

import dev.tidesapp.wearos.core.domain.model.TrackItem

interface TrackFavoritesRepository {
    suspend fun getUserFavoriteTracks(forceRefresh: Boolean = false): Result<List<TrackItem>>
}
