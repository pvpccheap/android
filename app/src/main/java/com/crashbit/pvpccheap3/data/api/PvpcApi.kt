package com.crashbit.pvpccheap3.data.api

import com.crashbit.pvpccheap3.data.model.*
import retrofit2.http.*

interface PvpcApi {

    // Auth endpoints
    @POST("api/auth/google")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refreshToken(): AuthResponse

    @GET("api/auth/me")
    suspend fun getCurrentUser(): UserResponse

    // Device endpoints
    @GET("api/devices")
    suspend fun getDevices(): List<Device>

    @POST("api/devices/sync")
    suspend fun syncDevices(@Body request: DeviceSyncRequest): List<Device>

    @PATCH("api/devices/{id}")
    suspend fun updateDevice(
        @Path("id") deviceId: String,
        @Body request: DeviceUpdateRequest
    ): Device

    @DELETE("api/devices/{id}")
    suspend fun deleteDevice(@Path("id") deviceId: String)

    // Rule endpoints
    @GET("api/rules")
    suspend fun getRules(): List<RuleWithDevice>

    @POST("api/rules")
    suspend fun createRule(@Body request: CreateRuleRequest): RuleWithDevice

    @GET("api/rules/{id}")
    suspend fun getRule(@Path("id") ruleId: String): RuleWithDevice

    @PUT("api/rules/{id}")
    suspend fun updateRule(
        @Path("id") ruleId: String,
        @Body request: UpdateRuleRequest
    ): RuleWithDevice

    @DELETE("api/rules/{id}")
    suspend fun deleteRule(@Path("id") ruleId: String)

    // Price endpoints (no auth required)
    @GET("api/prices/today")
    suspend fun getTodayPrices(): DailyPrices

    @GET("api/prices/tomorrow")
    suspend fun getTomorrowPrices(): DailyPrices

    // Schedule endpoints
    @GET("api/schedule/today")
    suspend fun getTodaySchedule(): List<ScheduledAction>

    @GET("api/schedule/{date}")
    suspend fun getScheduleByDate(@Path("date") date: String): List<ScheduledAction>

    @POST("api/schedule/calculate")
    suspend fun calculateOptimalHours(@Body request: CalculateScheduleRequest): OptimalHoursResponse

    @PATCH("api/schedule/{id}/status")
    suspend fun updateActionStatus(
        @Path("id") actionId: String,
        @Body request: UpdateActionStatusRequest
    )
}
