package dev.tidesapp.wearos.library.data.dto

import kotlinx.serialization.Serializable

/**
 * DTO for `GET v2/{viewAllPath}` — the generic "See all" endpoint driven by the
 * opaque `viewAll` relative URL carried on [HomeFeedV2ModuleDto].
 *
 * The response reuses the same polymorphic item envelope as the home feed modules
 * ([HomeFeedV2ItemEnvelopeDto]), so [HomeFeedV2Mapper] can share its item-decoding
 * logic. Every field defaults so partial server responses never throw.
 */
@Serializable
data class ViewAllResponseDto(
    val title: String = "",
    val subtitle: String? = null,
    val itemLayout: String = "",
    val items: List<HomeFeedV2ItemEnvelopeDto> = emptyList(),
)
