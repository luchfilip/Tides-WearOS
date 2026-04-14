package dev.tidesapp.wearos.di

import dev.tidesapp.wearos.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    @Named("apiBaseUrl")
    fun provideApiBaseUrl(): String = BuildConfig.TIDAL_API_BASE_URL

    @Provides
    @Named("authBaseUrl")
    fun provideAuthBaseUrl(): String = BuildConfig.TIDAL_AUTH_BASE_URL

    @Provides
    @Named("clientId")
    fun provideClientId(): String = BuildConfig.TIDAL_CLIENT_ID

    @Provides
    @Named("clientSecret")
    fun provideClientSecret(): String = BuildConfig.TIDAL_CLIENT_SECRET

    @Provides
    @Named("clientVersion")
    fun provideClientVersion(): String = BuildConfig.TIDAL_CLIENT_VERSION
}
