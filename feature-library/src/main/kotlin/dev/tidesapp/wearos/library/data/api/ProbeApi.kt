// THROWAWAY: delete in TIDES-M2E after home v2 migration completes.
package dev.tidesapp.wearos.library.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Throwaway probe interface for the v2 home feed migration (TIDES-M2A).
 *
 * All three methods return [Response]<[ResponseBody]> rather than typed DTOs so the
 * probe can inspect raw status, headers, and body before committing to a DTO shape.
 *
 * Ref: .docs/mobile-api-migration.md §4.2
 */
interface ProbeApi {

    @GET("v2/home/feed/STATIC")
    suspend fun probeHomeV2(
        @Header("Authorization") token: String,
        @Query("refreshId") refreshId: Long,
        @Query("timeOffset") timeOffset: String,
        @Query("limit") limit: Int = 20,
    ): Response<ResponseBody>

    @GET("v2/home/pages/{section}/view-all")
    suspend fun probeViewAll(
        @Header("Authorization") token: String,
        @Path("section") section: String,
        @Query("refreshId") refreshId: Long,
        @Query("timeOffset") timeOffset: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50,
    ): Response<ResponseBody>

    @GET("v2/user-playlists/{uuid}")
    suspend fun probeUserPlaylist(
        @Header("Authorization") token: String,
        @Path("uuid") uuid: String,
    ): Response<ResponseBody>
}
