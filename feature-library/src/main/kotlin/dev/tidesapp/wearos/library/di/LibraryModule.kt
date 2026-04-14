package dev.tidesapp.wearos.library.di

import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.repository.AlbumRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.HomeRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.MixRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.PlaylistRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.RecentActivityRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.SearchRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.TrackFavoritesRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.ViewAllRepositoryImpl
import dev.tidesapp.wearos.library.domain.repository.AlbumRepository
import dev.tidesapp.wearos.library.domain.repository.HomeRepository
import dev.tidesapp.wearos.library.domain.repository.MixRepository
import dev.tidesapp.wearos.library.domain.repository.PlaylistRepository
import dev.tidesapp.wearos.library.domain.repository.RecentActivityRepository
import dev.tidesapp.wearos.library.domain.repository.SearchRepository
import dev.tidesapp.wearos.library.domain.repository.TrackFavoritesRepository
import dev.tidesapp.wearos.library.domain.repository.ViewAllRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryRepositoryModule {

    @Binds
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

    @Binds
    abstract fun bindMixRepository(impl: MixRepositoryImpl): MixRepository

    @Binds
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    abstract fun bindRecentActivityRepository(
        impl: RecentActivityRepositoryImpl,
    ): RecentActivityRepository

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    abstract fun bindTrackFavoritesRepository(
        impl: TrackFavoritesRepositoryImpl,
    ): TrackFavoritesRepository

    @Binds
    abstract fun bindViewAllRepository(impl: ViewAllRepositoryImpl): ViewAllRepository

    companion object {
        @Provides
        @Singleton
        fun provideTidesLibraryApi(retrofit: Retrofit): TidesLibraryApi {
            return retrofit.create(TidesLibraryApi::class.java)
        }
    }
}
