package dev.tidesapp.wearos.library.domain.mapper

import dev.tidesapp.wearos.core.domain.model.HomeFeedItem
import dev.tidesapp.wearos.core.domain.model.HomeFeedSection
import dev.tidesapp.wearos.core.domain.model.ViewAllPage
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2AlbumPayload
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2ItemEnvelopeDto
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2MixPayload
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2ModuleDto
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2PlaylistPayload
import dev.tidesapp.wearos.library.data.dto.HomeFeedV2ResponseDto
import dev.tidesapp.wearos.library.data.dto.ViewAllResponseDto
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

/**
 * Maps the `GET v2/home/feed/STATIC` response into the existing domain
 * [HomeFeedSection] / [HomeFeedItem] shapes.
 *
 * Ground-truth field docs: `.docs/03-home-feed.md §2.2 – §2.5`.
 *
 * Behaviour parity with v1 [HomeFeedMapper]:
 * - Modules with blank `title` are dropped.
 * - Modules whose items are all filtered out are dropped.
 * - Each section is truncated to [MAX_ITEMS_PER_SECTION] before mapping.
 *
 * v2-specific behaviour:
 * - `ARTIST_LIST` modules are dropped (no `HomeFeedItem.Artist` in domain yet).
 * - Modules with an unknown `type` are dropped (not thrown).
 * - Inner items of kind `ARTIST`, `TRACK`, `DEEP_LINK` are dropped.
 * - The context `header` anchor on `GRID_CARD_WITH_CONTEXT` is ignored; only the `items[]` carousel is surfaced.
 */
class HomeFeedV2Mapper @Inject constructor(
    private val json: Json,
) {

    fun map(response: HomeFeedV2ResponseDto): List<HomeFeedSection> =
        response.items.mapNotNull { it.toSection() }

    /**
     * Maps a `GET v2/{viewAllPath}` response into a [ViewAllPage]. Unlike [map], there is
     * no per-section truncation: view-all is the destination that shows the full item list
     * (typically up to `limit=50`). Unsupported item kinds are dropped silently, same as
     * the home feed mapper.
     *
     * Ground-truth field documentation: `.docs/03-home-feed.md §4`.
     */
    fun mapViewAll(response: ViewAllResponseDto): ViewAllPage =
        ViewAllPage(
            title = response.title,
            subtitle = response.subtitle,
            items = response.items.toHomeFeedItems().toImmutableList(),
        )

    private fun HomeFeedV2ModuleDto.toSection(): HomeFeedSection? {
        if (title.isBlank()) return null
        if (!type.isSupportedModuleType()) return null
        val feedItems = items
            .take(MAX_ITEMS_PER_SECTION)
            .toHomeFeedItems()
        if (feedItems.isEmpty()) return null
        return HomeFeedSection(
            title = title,
            items = feedItems.toImmutableList(),
            viewAllPath = viewAll?.takeIf { it.isNotBlank() },
        )
    }

    /** Shared item-decoding pipeline used by both [map] and [mapViewAll]. */
    private fun List<HomeFeedV2ItemEnvelopeDto>.toHomeFeedItems(): List<HomeFeedItem> =
        mapNotNull { it.toHomeFeedItem() }

    private fun String.isSupportedModuleType(): Boolean = when (this) {
        MODULE_SHORTCUT_LIST,
        MODULE_GRID_CARD,
        MODULE_COMPACT_GRID_CARD,
        MODULE_GRID_CARD_WITH_CONTEXT -> true
        else -> false // ARTIST_LIST, and any future/unknown module type.
    }

    private fun HomeFeedV2ItemEnvelopeDto.toHomeFeedItem(): HomeFeedItem? {
        val payload = data ?: return null
        return when (type) {
            ITEM_PLAYLIST -> decode<HomeFeedV2PlaylistPayload>(payload)?.toDomain()
            ITEM_ALBUM -> decode<HomeFeedV2AlbumPayload>(payload)?.toDomain()
            ITEM_MIX -> decode<HomeFeedV2MixPayload>(payload)?.toDomain()
            else -> null // ARTIST, TRACK, DEEP_LINK, future kinds.
        }
    }

    private inline fun <reified T> decode(element: JsonElement): T? =
        try {
            json.decodeFromJsonElement<T>(element)
        } catch (_: IllegalArgumentException) {
            // Server-side shape drift: drop the entry rather than fail the whole feed.
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        }

    private fun HomeFeedV2PlaylistPayload.toDomain(): HomeFeedItem.Playlist? {
        if (uuid.isBlank()) return null
        val creatorName = creator?.name
            ?.takeIf { it.isNotBlank() }
            ?: promotedArtists.firstOrNull()?.name
            ?: "TIDAL"
        return HomeFeedItem.Playlist(
            id = uuid,
            title = title,
            imageUrl = tidalImageUrl(squareImage ?: image),
            creator = creatorName,
        )
    }

    private fun HomeFeedV2AlbumPayload.toDomain(): HomeFeedItem.Album? {
        if (id == 0L) return null
        val artistName = artists.firstOrNull { it.main }?.name
            ?: artists.firstOrNull()?.name
            ?: "Unknown Artist"
        return HomeFeedItem.Album(
            id = id.toString(),
            title = title,
            imageUrl = tidalImageUrl(cover),
            artistName = artistName,
        )
    }

    private fun HomeFeedV2MixPayload.toDomain(): HomeFeedItem.Mix? {
        if (id.isBlank()) return null
        // Real v2 MIX payloads carry the display text under titleTextInfo/subtitleTextInfo
        // (see .docs/03-home-feed.md §2.5.2). Fall back to the flat title/subTitle fields for
        // defensive parsing in case the server ever inlines them.
        val displayTitle = titleTextInfo?.text?.takeIf { it.isNotBlank() }
            ?: title.takeIf { it.isNotBlank() }
            ?: return null
        val displaySubtitle = subtitleTextInfo?.text?.takeIf { it.isNotBlank() }
            ?: subTitle
        // mixImages is an array of {size,url,width,height}. Prefer SMALL then MEDIUM to match
        // the v1 mapper behaviour and the watch's rendering budget.
        val imageUrl = mixImages.firstOrNull { it.size == MIX_IMAGE_SIZE_SMALL }?.url
            ?: mixImages.firstOrNull { it.size == MIX_IMAGE_SIZE_MEDIUM }?.url
            ?: mixImages.firstOrNull()?.url
        return HomeFeedItem.Mix(
            id = id,
            title = displayTitle,
            imageUrl = imageUrl,
            subTitle = displaySubtitle,
        )
    }

    private companion object {
        const val MAX_ITEMS_PER_SECTION = 5

        const val MODULE_SHORTCUT_LIST = "SHORTCUT_LIST"
        const val MODULE_GRID_CARD = "GRID_CARD"
        const val MODULE_COMPACT_GRID_CARD = "COMPACT_GRID_CARD"
        const val MODULE_GRID_CARD_WITH_CONTEXT = "GRID_CARD_WITH_CONTEXT"

        const val ITEM_PLAYLIST = "PLAYLIST"
        const val ITEM_ALBUM = "ALBUM"
        const val ITEM_MIX = "MIX"

        const val MIX_IMAGE_SIZE_SMALL = "SMALL"
        const val MIX_IMAGE_SIZE_MEDIUM = "MEDIUM"
    }
}
