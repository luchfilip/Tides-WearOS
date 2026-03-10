package dev.tidesapp.wearos.auth.di

import android.content.Context
import com.tidal.sdk.auth.Auth
import com.tidal.sdk.auth.CredentialsProvider
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.network.NetworkLogLevel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TidalSdkModule {

    @Provides
    @Singleton
    fun provideTidalAuth(
        @ApplicationContext context: Context,
        @Named("clientId") clientId: String,
        @Named("clientSecret") clientSecret: String,
    ): TidalAuth {
        val config = AuthConfig(
            clientId = clientId,
            clientSecret = clientSecret,
            credentialsKey = "credentialsKey",
            scopes = setOf("r_usr", "w_usr"),
            enableCertificatePinning = false,
            logLevel = NetworkLogLevel.NONE,
        )
        return TidalAuth.getInstance(config, context)
    }

    @Provides
    @Singleton
    fun provideAuth(tidalAuth: TidalAuth): Auth = tidalAuth.auth

    @Provides
    @Singleton
    fun provideCredentialsProvider(tidalAuth: TidalAuth): CredentialsProvider =
        tidalAuth.credentialsProvider
}
