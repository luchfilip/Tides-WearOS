package dev.tidesapp.wearos.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Production [Clock] + [ZoneId] bindings. Both [dev.tidesapp.wearos.core.time.TimeOffsetFormatter]
 * (production, used by `HomeRepositoryImpl`) and the throwaway `HomeV2Prober` depend on
 * these transitively. Lives in `core` so `feature-library` (which depends on `core`)
 * can consume them without cross-module DI contortions.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()
}
