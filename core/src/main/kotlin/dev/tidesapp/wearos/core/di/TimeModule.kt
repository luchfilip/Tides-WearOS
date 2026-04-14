package dev.tidesapp.wearos.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Production [Clock] + [ZoneId] bindings. [dev.tidesapp.wearos.core.time.TimeOffsetFormatter]
 * (used by `HomeRepositoryImpl`) depends on these transitively. Lives in `core` so
 * `feature-library` (which depends on `core`) can consume them without cross-module DI
 * contortions.
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
