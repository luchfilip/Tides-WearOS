package dev.tidesapp.wearos.settings.di

import dev.tidesapp.wearos.settings.data.repository.SettingsRepositoryImpl
import dev.tidesapp.wearos.settings.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
