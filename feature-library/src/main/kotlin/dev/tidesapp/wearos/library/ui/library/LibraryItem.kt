package dev.tidesapp.wearos.library.ui.library

sealed interface LibraryItem {
    val label: String

    data object Playlists : LibraryItem {
        override val label: String = "My Playlists"
    }

    data object Albums : LibraryItem {
        override val label: String = "My Albums"
    }

    data object Tracks : LibraryItem {
        override val label: String = "My Tracks"
    }

    data object Recent : LibraryItem {
        override val label: String = "Recent"
    }

    data object Settings : LibraryItem {
        override val label: String = "Settings"
    }
}
