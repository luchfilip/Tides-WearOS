package dev.tidesapp.wearos.library.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// =================================================================
// Collection DTOs — cursor-based pagination (/v2/my-collection/*)
// =================================================================

// Each entry in /v2/my-collection/{type}/folders items[] is an envelope:
// { trn, itemType: "ALBUM"|"PLAYLIST"|"FOLDER", addedAt, lastModifiedAt, name,
//   parent, data: { ...full content object... } }
@Serializable
data class CollectionAlbumEntryDto(
    val itemType: String = "",
    val data: AlbumDataDto? = null,
)

@Serializable
data class CollectionPlaylistEntryDto(
    val itemType: String = "",
    val data: PlaylistDataDto? = null,
)

@Serializable
data class CollectionAlbumsResponseDto(
    val lastModifiedAt: String? = null,
    val items: List<CollectionAlbumEntryDto> = emptyList(),
    val totalNumberOfItems: Int = 0,
    val cursor: String? = null,
)

@Serializable
data class CollectionPlaylistsResponseDto(
    val lastModifiedAt: String? = null,
    val items: List<CollectionPlaylistEntryDto> = emptyList(),
    val totalNumberOfItems: Int = 0,
    val cursor: String? = null,
)

// =================================================================
// v1 track list DTO — offset/limit pagination (/v1/albums/{id}/tracks, /v1/playlists/{id}/tracks)
// =================================================================

@Serializable
data class V1TrackListResponseDto(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<TrackDataDto> = emptyList(),
)

// =================================================================
// v1 mix items DTO — /v1/mixes/{id}/items
//
// This endpoint wraps each track in a `{ item: Track, type: "track" }`
// envelope, unlike /v1/playlists/{id}/tracks which returns tracks directly
// under `items[]`. The inner `item` object has the same shape as TrackDataDto,
// so we reuse that.
// =================================================================

@Serializable
data class V1MixItemsResponseDto(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<V1MixItemEnvelopeDto> = emptyList(),
)

@Serializable
data class V1MixItemEnvelopeDto(
    val item: TrackDataDto? = null,
    val type: String = "",
)

// =================================================================
// v1 user favorite tracks DTO — /v1/users/{userId}/favorites/tracks
//
// Each entry wraps a Track in `{ created: timestamp, item: Track }`. The inner
// `item` has the same shape as TrackDataDto (with extra fields that
// ignoreUnknownKeys will drop).
// =================================================================

@Serializable
data class V1FavoriteTracksResponseDto(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<V1FavoriteTrackEntryDto> = emptyList(),
)

@Serializable
data class V1FavoriteTrackEntryDto(
    val created: String = "",
    val item: TrackDataDto? = null,
)

// =================================================================
// v1 user activity DTO — /v1/users/{userId}/activity
//
// Each entry's `item` shape depends on the top-level `type` discriminator
// (TRACK | PLAYLIST | ALBUM | ...). We decode `item` as a raw JsonElement and
// let the repo layer filter to TRACK rows before deserializing the payload.
// =================================================================

@Serializable
data class V1ActivityResponseDto(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<V1ActivityEntryDto> = emptyList(),
)

@Serializable
data class V1ActivityEntryDto(
    val activityType: String = "",
    val type: String = "",
    val item: JsonElement? = null,
)

// =================================================================
// Real API data DTOs — matching actual Tidal API response schemas
// =================================================================

@Serializable
data class AlbumDataDto(
    val id: Long = 0,
    val title: String = "",
    val artists: List<ArtistBriefDto> = emptyList(),
    val cover: String? = null,
    val vibrantColor: String? = null,
    val releaseDate: String? = null,
    val numberOfTracks: Int = 0,
    val duration: Int = 0,
    val explicit: Boolean = false,
)

@Serializable
data class TrackDataDto(
    val id: Long = 0,
    val title: String = "",
    val duration: Int = 0,
    val trackNumber: Int = 0,
    val volumeNumber: Int = 0,
    val explicit: Boolean = false,
    val artist: ArtistBriefDto? = null,
    val artists: List<ArtistBriefDto> = emptyList(),
    val album: TrackAlbumDto? = null,
)

@Serializable
data class TrackAlbumDto(
    val id: Long = 0,
    val title: String = "",
    val cover: String? = null,
    val releaseDate: String? = null,
)

@Serializable
data class PlaylistDataDto(
    val uuid: String = "",
    val title: String = "",
    val description: String? = null,
    val image: String? = null,
    val squareImage: String? = null,
    val numberOfTracks: Int = 0,
    val duration: Int = 0,
    val creator: PlaylistCreatorDto? = null,
    val type: String? = null,
)

@Serializable
data class PlaylistCreatorDto(
    val id: Long = 0,
    val name: String? = null,
    val type: String? = null,
)

@Serializable
data class ArtistBriefDto(
    val id: Long = 0,
    val name: String = "",
    val picture: String? = null,
    val main: Boolean? = null,
)

// =================================================================
// Search DTOs — offset/limit pagination (/v2/search)
// =================================================================

@Serializable
data class SearchResponseDto(
    val albums: SearchAlbumsSection? = null,
    val tracks: SearchTracksSection? = null,
    val playlists: SearchPlaylistsSection? = null,
    val artists: SearchArtistsSection? = null,
)

@Serializable
data class SearchAlbumsSection(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<AlbumDataDto> = emptyList(),
)

@Serializable
data class SearchTracksSection(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<TrackDataDto> = emptyList(),
)

@Serializable
data class SearchPlaylistsSection(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<PlaylistDataDto> = emptyList(),
)

@Serializable
data class SearchArtistsSection(
    val limit: Int = 0,
    val offset: Int = 0,
    val totalNumberOfItems: Int = 0,
    val items: List<ArtistBriefDto> = emptyList(),
)
