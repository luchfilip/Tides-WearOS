package dev.tidesapp.wearos.player.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackInfoResponse(
    val trackId: Long = 0,
    val assetPresentation: String = "",
    val audioMode: String = "",
    val audioQuality: String = "",
    val manifestMimeType: String = "",
    val manifest: String = "",
)

@Serializable
data class BtsManifest(
    val mimeType: String = "",
    val codecs: String = "",
    val urls: List<String> = emptyList(),
)

@Serializable
data class TrackDetailResponse(
    val id: Long = 0,
    val title: String = "",
    val duration: Int = 0,
    val trackNumber: Int = 0,
    val artist: TrackArtistDto? = null,
    val artists: List<TrackArtistDto> = emptyList(),
    val album: TrackAlbumInfoDto? = null,
)

@Serializable
data class TrackArtistDto(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class TrackAlbumInfoDto(
    val id: Long = 0,
    val title: String = "",
    val cover: String? = null,
)
