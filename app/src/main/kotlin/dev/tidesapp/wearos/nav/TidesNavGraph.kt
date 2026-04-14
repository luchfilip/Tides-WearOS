package dev.tidesapp.wearos.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.tidesapp.wearos.auth.ui.login.LoginScreen
import dev.tidesapp.wearos.library.ui.albumdetail.AlbumDetailScreen
import dev.tidesapp.wearos.library.ui.albums.AlbumsScreen
import dev.tidesapp.wearos.library.ui.home.HomeScreen
import dev.tidesapp.wearos.library.ui.mixdetail.MixDetailScreen
import dev.tidesapp.wearos.library.ui.playlistdetail.PlaylistDetailScreen
import dev.tidesapp.wearos.library.ui.playlists.PlaylistsScreen
import dev.tidesapp.wearos.library.ui.search.SearchScreen
import dev.tidesapp.wearos.library.ui.viewall.ViewAllScreen
import dev.tidesapp.wearos.player.ui.nowplaying.NowPlayingScreen
import dev.tidesapp.wearos.settings.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

// Type-safe route objects (kept for future Wear Compose Navigation type-safe support)
@Serializable object LoginRoute
@Serializable object LibraryHomeRoute
@Serializable data class AlbumDetailRoute(val albumId: String)
@Serializable data class PlaylistDetailRoute(val playlistId: String)
@Serializable data class MixDetailRoute(val mixId: String)
@Serializable data class ViewAllRoute(val path: String, val title: String)
@Serializable object SearchRoute
@Serializable object NowPlayingRoute
@Serializable object SettingsRoute

object Routes {
    const val LOGIN = "login"
    const val LIBRARY_HOME = "library_home"
    const val ALBUMS = "albums"
    const val PLAYLISTS = "playlists"
    const val ALBUM_DETAIL = "album_detail/{albumId}"
    const val PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
    const val MIX_DETAIL = "mix_detail/{mixId}?title={title}&subTitle={subTitle}&imageUrl={imageUrl}"
    const val VIEW_ALL = "view_all?path={path}&title={title}"
    const val SEARCH = "search"
    const val NOW_PLAYING = "now_playing"
    const val SETTINGS = "settings"

    fun albumDetail(albumId: String) = "album_detail/$albumId"
    fun playlistDetail(playlistId: String) = "playlist_detail/$playlistId"
    fun mixDetail(mixId: String, title: String, subTitle: String?, imageUrl: String?): String {
        val encodedTitle = Uri.encode(title)
        val encodedSubtitle = Uri.encode(subTitle.orEmpty())
        val encodedImage = Uri.encode(imageUrl.orEmpty())
        return "mix_detail/$mixId?title=$encodedTitle&subTitle=$encodedSubtitle&imageUrl=$encodedImage"
    }
    fun viewAll(path: String, title: String): String {
        val encodedPath = Uri.encode(path)
        val encodedTitle = Uri.encode(title)
        return "view_all?path=$encodedPath&title=$encodedTitle"
    }
}

@Composable
fun TidesNavGraph() {
    val navController = rememberSwipeDismissableNavController()
    val navGraphViewModel: NavGraphViewModel = hiltViewModel()
    val navGraphUiState by navGraphViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (navGraphUiState is NavGraphUiState.Initial) {
            navGraphViewModel.onEvent(NavGraphUiEvent.CheckAuthState)
        }
    }

    LaunchedEffect(Unit) {
        navGraphViewModel.uiEffect.collect { effect ->
            when (effect) {
                NavGraphUiEffect.NavigateToHome -> {
                    navController.navigate(Routes.LIBRARY_HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
                NavGraphUiEffect.NavigateToLogin -> {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.LIBRARY_HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LIBRARY_HOME) {
            HomeScreen(
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Routes.albumDetail(albumId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                },
                onNavigateToMix = { mixId, title, subTitle, imageUrl ->
                    navController.navigate(Routes.mixDetail(mixId, title, subTitle, imageUrl))
                },
                onNavigateToViewAll = { path, title ->
                    navController.navigate(Routes.viewAll(path, title))
                },
                onNavigateToNowPlaying = {
                    navController.navigate(Routes.NOW_PLAYING)
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.SEARCH)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(Routes.ALBUMS) {
            AlbumsScreen(
                onNavigateToAlbumDetail = { albumId ->
                    navController.navigate(Routes.albumDetail(albumId))
                },
            )
        }

        composable(Routes.PLAYLISTS) {
            PlaylistsScreen(
                onNavigateToPlaylistDetail = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                },
            )
        }

        composable(
            route = Routes.ALBUM_DETAIL,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId").orEmpty()
            AlbumDetailScreen(
                albumId = albumId,
                onNavigateToNowPlaying = {
                    navController.navigate(Routes.NOW_PLAYING)
                },
            )
        }

        composable(
            route = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
            PlaylistDetailScreen(
                playlistId = playlistId,
                onNavigateToNowPlaying = {
                    navController.navigate(Routes.NOW_PLAYING)
                },
            )
        }

        composable(
            route = Routes.MIX_DETAIL,
            arguments = listOf(
                navArgument("mixId") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("subTitle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("imageUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            MixDetailScreen(
                onNavigateToNowPlaying = {
                    navController.navigate(Routes.NOW_PLAYING)
                },
            )
        }

        composable(
            route = Routes.VIEW_ALL,
            arguments = listOf(
                navArgument("path") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ViewAllScreen(
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Routes.albumDetail(albumId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                },
                onNavigateToMix = { mixId, title, subTitle, imageUrl ->
                    navController.navigate(Routes.mixDetail(mixId, title, subTitle, imageUrl))
                },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onNavigateToAlbumDetail = { albumId ->
                    navController.navigate(Routes.albumDetail(albumId))
                },
                onNavigateToPlaylistDetail = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                },
                onNavigateToNowPlaying = {
                    navController.navigate(Routes.NOW_PLAYING)
                },
            )
        }

        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

