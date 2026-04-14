package dev.tidesapp.wearos.library.data.api

import dev.tidesapp.wearos.library.data.dto.AlbumDataDto
import dev.tidesapp.wearos.library.data.dto.CollectionAlbumsResponseDto
import dev.tidesapp.wearos.library.data.dto.CollectionPlaylistsResponseDto
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2ResponseDto
import dev.tidesapp.wearos.library.data.dto.HomePageResponseDto
import dev.tidesapp.wearos.library.data.dto.PlaylistDataDto
import dev.tidesapp.wearos.library.data.dto.SearchResponseDto
import dev.tidesapp.wearos.library.data.dto.V1TrackListResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TidesLibraryApi {

    @GET("v2/my-collection/albums/folders")
    suspend fun getUserAlbums(
        @Header("Authorization") token: String,
        @Query("folderId") folderId: String = "root",
        @Query("limit") limit: Int = 50,
        @Query("order") order: String = "DATE",
        @Query("orderDirection") orderDirection: String = "DESC",
        @Query("cursor") cursor: String = "",
    ): CollectionAlbumsResponseDto

    @GET("v1/albums/{id}")
    suspend fun getAlbum(
        @Header("Authorization") token: String,
        @Path("id") albumId: String,
    ): AlbumDataDto

    @GET("v1/albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Header("Authorization") token: String,
        @Path("id") albumId: String,
    ): V1TrackListResponseDto

    @GET("v1/playlists/{id}")
    suspend fun getPlaylist(
        @Header("Authorization") token: String,
        @Path("id") playlistId: String,
    ): PlaylistDataDto

    @GET("v2/my-collection/playlists/folders")
    suspend fun getUserPlaylists(
        @Header("Authorization") token: String,
        @Query("folderId") folderId: String = "root",
        @Query("limit") limit: Int = 50,
        @Query("order") order: String = "DATE",
        @Query("orderDirection") orderDirection: String = "DESC",
        @Query("cursor") cursor: String = "",
    ): CollectionPlaylistsResponseDto

    @GET("v1/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") token: String,
        @Path("id") playlistId: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50,
    ): V1TrackListResponseDto

    @GET("v1/pages/home")
    suspend fun getHomePage(
        @Header("Authorization") token: String,
    ): HomePageResponseDto

    @GET("v2/home/feed/STATIC")
    suspend fun getHomeFeedV2(
        @Header("Authorization") token: String,
        @Query("refreshId") refreshId: Long,
        @Query("timeOffset") timeOffset: String,
        @Query("limit") limit: Int = 20,
    ): HomeFeedV2ResponseDto

    @GET("v2/search")
    suspend fun search(
        @Header("Authorization") token: String,
        @Query("query") query: String,
        @Query("types") types: String = "ALL,TOP,ALBUMS,TRACKS,ARTISTS,PLAYLISTS",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("includeUserPlaylists") includeUserPlaylists: Boolean = true,
        @Query("includeDidYouMean") includeDidYouMean: Boolean = true,
    ): SearchResponseDto
}
