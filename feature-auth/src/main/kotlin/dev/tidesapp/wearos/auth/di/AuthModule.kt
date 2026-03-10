package dev.tidesapp.wearos.auth.di

import dev.tidesapp.wearos.auth.data.repository.AuthRepositoryImpl
import dev.tidesapp.wearos.auth.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
