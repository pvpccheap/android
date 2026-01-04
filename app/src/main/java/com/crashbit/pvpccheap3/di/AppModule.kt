package com.crashbit.pvpccheap3.di

import android.content.Context
import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.local.ScheduleCache
import com.crashbit.pvpccheap3.data.local.TokenManager
import com.crashbit.pvpccheap3.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context
    ): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideScheduleCache(
        @ApplicationContext context: Context
    ): ScheduleCache {
        return ScheduleCache(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        api: PvpcApi,
        tokenManager: TokenManager
    ): AuthRepository {
        return AuthRepository(api, tokenManager)
    }

    @Provides
    @Singleton
    fun provideGoogleHomeRepository(
        @ApplicationContext context: Context
    ): GoogleHomeRepository {
        return GoogleHomeRepository(context)
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(
        api: PvpcApi,
        googleHomeRepository: GoogleHomeRepository
    ): DeviceRepository {
        return DeviceRepository(api, googleHomeRepository)
    }

    @Provides
    @Singleton
    fun provideRuleRepository(
        api: PvpcApi
    ): RuleRepository {
        return RuleRepository(api)
    }

    @Provides
    @Singleton
    fun providePriceRepository(
        api: PvpcApi
    ): PriceRepository {
        return PriceRepository(api)
    }

    @Provides
    @Singleton
    fun provideScheduleRepository(
        api: PvpcApi,
        scheduleCache: ScheduleCache
    ): ScheduleRepository {
        return ScheduleRepository(api, scheduleCache)
    }
}
