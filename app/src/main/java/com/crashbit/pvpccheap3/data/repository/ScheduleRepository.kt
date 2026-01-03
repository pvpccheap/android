package com.crashbit.pvpccheap3.data.repository

import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: PvpcApi
) {
    suspend fun getTodaySchedule(): Result<List<ScheduledAction>> {
        return try {
            Result.success(api.getTodaySchedule())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getScheduleByDate(date: String): Result<List<ScheduledAction>> {
        return try {
            Result.success(api.getScheduleByDate(date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun calculateOptimalHours(ruleId: String, date: String? = null): Result<OptimalHoursResponse> {
        return try {
            Result.success(api.calculateOptimalHours(CalculateScheduleRequest(ruleId, date)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateActionStatus(actionId: String, status: String): Result<Unit> {
        return try {
            api.updateActionStatus(actionId, UpdateActionStatusRequest(status))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
