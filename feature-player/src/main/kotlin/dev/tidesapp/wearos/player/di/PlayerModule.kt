package dev.tidesapp.wearos.player.di

import dev.tidesapp.wearos.player.data.PlayerRepositoryImpl
import dev.tidesapp.wearos.player.data.api.TidesPlaybackApi
import dev.tidesapp.wearos.player.domain.repository.PlayerRepository
import dev.tidesapp.wearos.player.playback.StreamingPrivilegeSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerBindingsModule {

    @Binds
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository
}

@Module
@InstallIn(SingletonComponent::class)
object PlayerProvidesModule {

    @Provides
    @Singleton
    fun provideStreamingPrivilegeSource(): StreamingPrivilegeSource {
        return StreamingPrivilegeSource {
            Result.success(Unit)
        }
    }

    @Provides
    @Singleton
    fun provideTidesPlaybackApi(retrofit: Retrofit): TidesPlaybackApi {
        return retrofit.create(TidesPlaybackApi::class.java)
    }
}
