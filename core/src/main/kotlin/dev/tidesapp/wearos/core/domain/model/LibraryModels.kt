package dev.tidesapp.wearos.core.domain.model

import kotlinx.collections.immutable.ImmutableList

data class AlbumItem(
    val id: String,
    val title: String,
    val artistName: String,
    val imageUrl: String?,
    val releaseDate: String?,
    val numberOfTracks: Int,
)

data class TrackItem(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val duration: Int,
    val trackNumber: Int,
    val imageUrl: String?,
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val numberOfTracks: Int,
    val creator: String,
)

data class ArtistItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
)

data class SearchResult(
    val albums: ImmutableList<AlbumItem>,
    val tracks: ImmutableList<TrackItem>,
    val playlists: ImmutableList<PlaylistItem>,
    val artists: ImmutableList<ArtistItem>,
)

sealed interface HomeFeedItem {
    val id: String
    val title: String
    val imageUrl: String?

    data class Album(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val artistName: String,
    ) : HomeFeedItem

    data class Playlist(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val creator: String,
    ) : HomeFeedItem

    data class Mix(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val subTitle: String?,
    ) : HomeFeedItem
}

data class HomeFeedSection(
    val title: String,
    val items: ImmutableList<HomeFeedItem>,
    val viewAllPath: String? = null,
)

data class ViewAllPage(
    val title: String,
    val subtitle: String?,
    val items: ImmutableList<HomeFeedItem>,
)
