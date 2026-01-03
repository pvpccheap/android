package com.crashbit.pvpccheap3.data.repository

import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(
    private val api: PvpcApi
) {
    suspend fun getRules(): Result<List<RuleWithDevice>> {
        return try {
            Result.success(api.getRules())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRule(ruleId: String): Result<RuleWithDevice> {
        return try {
            Result.success(api.getRule(ruleId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRule(request: CreateRuleRequest): Result<RuleWithDevice> {
        return try {
            Result.success(api.createRule(request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRule(ruleId: String, request: UpdateRuleRequest): Result<RuleWithDevice> {
        return try {
            Result.success(api.updateRule(ruleId, request))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRule(ruleId: String): Result<Unit> {
        return try {
            api.deleteRule(ruleId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
