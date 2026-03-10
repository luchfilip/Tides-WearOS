package dev.tidesapp.wearos.player.data.api

import dev.tidesapp.wearos.player.data.dto.PlaybackInfoResponse
import dev.tidesapp.wearos.player.data.dto.TrackDetailResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TidesPlaybackApi {

    @GET("v1/tracks/{trackId}/playbackinfo")
    suspend fun getTrackPlaybackInfo(
        @Header("Authorization") token: String,
        @Path("trackId") trackId: String,
        @Query("audioquality") audioQuality: String = "HIGH",
        @Query("playbackmode") playbackMode: String = "STREAM",
        @Query("assetpresentation") assetPresentation: String = "FULL",
    ): PlaybackInfoResponse

    @GET("v1/tracks/{trackId}")
    suspend fun getTrack(
        @Header("Authorization") token: String,
        @Path("trackId") trackId: String,
    ): TrackDetailResponse
}
