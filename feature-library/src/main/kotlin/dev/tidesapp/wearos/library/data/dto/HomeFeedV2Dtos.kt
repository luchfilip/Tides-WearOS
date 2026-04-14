package dev.tidesapp.wearos.library.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for `GET v2/home/feed/STATIC` (phone-family endpoint).
 *
 * Ground-truth field documentation: `.docs/03-home-feed.md §2.2 – §2.5`.
 * Probe confirmation (TV device-code cid returning phone-variant body): `.docs/mobile-api-migration.md §10`.
 *
 * Design notes:
 * - The inner item `data` object is polymorphic across PLAYLIST/ALBUM/MIX/TRACK/ARTIST/DEEP_LINK.
 *   We deliberately do NOT use kotlinx.serialization class discriminators — they are strict and
 *   break when the server adds new item kinds. Instead [HomeFeedV2ItemEnvelopeDto.data] is parsed
 *   as a raw [JsonElement] and the mapper branches on the outer `type` string, then decodes the
 *   element into a typed payload via `Json.decodeFromJsonElement<T>(data)`.
 * - Every field carries a default so partial server responses never throw. `ignoreUnknownKeys`
 *   on the shared `Json` instance (see `NetworkModule.provideJson`) silently drops fields we
 *   don't model (e.g. `header` anchor on `GRID_CARD_WITH_CONTEXT`, the `icons` bucket, track/artist
 *   payload data, full album metadata, etc.).
 */
@Serializable
data class HomeFeedV2ResponseDto(
    val uuid: String = "",
    val page: HomeFeedV2PageDto = HomeFeedV2PageDto(),
    val header: HomeFeedV2HeaderDto? = null,
    val items: List<HomeFeedV2ModuleDto> = emptyList(),
)

@Serializable
data class HomeFeedV2PageDto(
    val cursor: String? = null,
)

@Serializable
data class HomeFeedV2HeaderDto(
    val vibes: HomeFeedV2VibesDto? = null,
)

@Serializable
data class HomeFeedV2VibesDto(
    val items: List<HomeFeedV2VibeDto> = emptyList(),
)

@Serializable
data class HomeFeedV2VibeDto(
    val name: String = "",
    val type: String = "",
)

@Serializable
data class HomeFeedV2ModuleDto(
    val type: String = "",
    val moduleId: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val icons: List<String> = emptyList(),
    val viewAll: String? = null,
    val items: List<HomeFeedV2ItemEnvelopeDto> = emptyList(),
)

@Serializable
data class HomeFeedV2ItemEnvelopeDto(
    val type: String = "",
    val data: JsonElement? = null,
)

// ---------------------------------------------------------------------------
// Typed payloads used only by HomeFeedV2Mapper after it knows the outer type.
// These mirror the minimum field set we surface in the domain layer. Additional
// server-side fields are silently dropped by the shared `Json` instance.
// ---------------------------------------------------------------------------

@Serializable
internal data class HomeFeedV2PlaylistPayload(
    val uuid: String = "",
    val title: String = "",
    val image: String? = null,
    val squareImage: String? = null,
    val promotedArtists: List<HomeFeedV2PromotedArtistDto> = emptyList(),
    val creator: HomeFeedV2CreatorDto? = null,
)

@Serializable
internal data class HomeFeedV2CreatorDto(
    val id: Long = 0,
    val name: String? = null,
    val type: String = "",
)

@Serializable
internal data class HomeFeedV2PromotedArtistDto(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
internal data class HomeFeedV2AlbumPayload(
    val id: Long = 0,
    val title: String = "",
    val cover: String? = null,
    val artists: List<HomeFeedV2AlbumArtistDto> = emptyList(),
)

@Serializable
internal data class HomeFeedV2AlbumArtistDto(
    val id: Long = 0,
    val name: String = "",
    val main: Boolean = false,
)

@Serializable
internal data class HomeFeedV2MixPayload(
    val id: String = "",
    val title: String = "",
    val subTitle: String? = null,
    val mixImages: List<HomeFeedV2MixImageDto> = emptyList(),
    val titleTextInfo: HomeFeedV2MixTextInfoDto? = null,
    val subtitleTextInfo: HomeFeedV2MixTextInfoDto? = null,
)

@Serializable
internal data class HomeFeedV2MixImageDto(
    val size: String = "",
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
internal data class HomeFeedV2MixTextInfoDto(
    val text: String? = null,
    val color: String? = null,
)
