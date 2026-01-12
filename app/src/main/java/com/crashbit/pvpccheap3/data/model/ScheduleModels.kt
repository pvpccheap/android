package com.crashbit.pvpccheap3.data.model

import com.google.gson.annotations.SerializedName

data class ScheduledAction(
    val id: String,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_name")
    val deviceName: String,
    @SerializedName("google_device_id")
    val googleDeviceId: String,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    val status: String
)

data class CalculateScheduleRequest(
    @SerializedName("rule_id")
    val ruleId: String,
    val date: String? = null
)

data class OptimalHoursResponse(
    @SerializedName("rule_id")
    val ruleId: String,
    val date: String,
    @SerializedName("optimal_hours")
    val optimalHours: List<Int>,
    @SerializedName("total_price")
    val totalPrice: Double
)

enum class ActionStatus(val value: String) {
    PENDING("pending"),
    EXECUTED("executed"),
    EXECUTED_ON("executed_on"),
    EXECUTED_OFF("executed_off"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    MISSED("missed")  // Acció que no es va executar i l'hora ja ha passat
}

data class UpdateActionStatusRequest(
    val status: String
)
