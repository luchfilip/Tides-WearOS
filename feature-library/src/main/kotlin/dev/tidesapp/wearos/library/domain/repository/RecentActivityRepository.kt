package dev.tidesapp.wearos.library.domain.repository

import dev.tidesapp.wearos.core.domain.model.TrackItem

interface RecentActivityRepository {
    suspend fun getRecentTracks(): Result<List<TrackItem>>
}
