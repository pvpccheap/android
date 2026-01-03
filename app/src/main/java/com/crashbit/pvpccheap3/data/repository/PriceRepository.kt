package com.crashbit.pvpccheap3.data.repository

import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.model.DailyPrices
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepository @Inject constructor(
    private val api: PvpcApi
) {
    suspend fun getTodayPrices(): Result<DailyPrices> {
        return try {
            Result.success(api.getTodayPrices())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTomorrowPrices(): Result<DailyPrices> {
        return try {
            Result.success(api.getTomorrowPrices())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
