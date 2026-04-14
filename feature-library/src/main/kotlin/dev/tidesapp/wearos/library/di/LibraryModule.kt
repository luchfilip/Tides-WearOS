package dev.tidesapp.wearos.library.di

import dev.tidesapp.wearos.library.data.api.ProbeApi
import dev.tidesapp.wearos.library.data.api.TidesLibraryApi
import dev.tidesapp.wearos.library.data.repository.AlbumRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.HomeRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.PlaylistRepositoryImpl
import dev.tidesapp.wearos.library.data.repository.SearchRepositoryImpl
import dev.tidesapp.wearos.library.domain.repository.AlbumRepository
import dev.tidesapp.wearos.library.domain.repository.HomeRepository
import dev.tidesapp.wearos.library.domain.repository.PlaylistRepository
import dev.tidesapp.wearos.library.domain.repository.SearchRepository
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
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    companion object {
        @Provides
        @Singleton
        fun provideTidesLibraryApi(retrofit: Retrofit): TidesLibraryApi {
            return retrofit.create(TidesLibraryApi::class.java)
        }

        // THROWAWAY: delete in TIDES-M2E after home v2 migration completes.
        @Provides
        @Singleton
        fun provideProbeApi(retrofit: Retrofit): ProbeApi {
            return retrofit.create(ProbeApi::class.java)
        }
    }
}
