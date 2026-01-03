package com.crashbit.pvpccheap3.data.model

import com.google.gson.annotations.SerializedName

data class RuleWithDevice(
    val id: String,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_name")
    val deviceName: String,
    val name: String,
    @SerializedName("max_hours")
    val maxHours: Int,
    @SerializedName("time_window_start")
    val timeWindowStart: String?,
    @SerializedName("time_window_end")
    val timeWindowEnd: String?,
    @SerializedName("min_continuous_hours")
    val minContinuousHours: Int,
    @SerializedName("days_of_week")
    val daysOfWeek: Int,
    @SerializedName("is_enabled")
    val isEnabled: Boolean
)

data class CreateRuleRequest(
    @SerializedName("device_id")
    val deviceId: String,
    val name: String,
    @SerializedName("max_hours")
    val maxHours: Int,
    @SerializedName("time_window_start")
    val timeWindowStart: String? = null,
    @SerializedName("time_window_end")
    val timeWindowEnd: String? = null,
    @SerializedName("min_continuous_hours")
    val minContinuousHours: Int? = null,
    @SerializedName("days_of_week")
    val daysOfWeek: Int? = null
)

data class UpdateRuleRequest(
    val name: String? = null,
    @SerializedName("max_hours")
    val maxHours: Int? = null,
    @SerializedName("time_window_start")
    val timeWindowStart: String? = null,
    @SerializedName("time_window_end")
    val timeWindowEnd: String? = null,
    @SerializedName("min_continuous_hours")
    val minContinuousHours: Int? = null,
    @SerializedName("days_of_week")
    val daysOfWeek: Int? = null,
    @SerializedName("is_enabled")
    val isEnabled: Boolean? = null
)

// Days of week bitmask constants
object DaysOfWeek {
    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 4
    const val THURSDAY = 8
    const val FRIDAY = 16
    const val SATURDAY = 32
    const val SUNDAY = 64
    const val ALL_DAYS = 127
    const val WEEKDAYS = 31 // Mon-Fri
    const val WEEKEND = 96 // Sat-Sun

    fun isEnabled(bitmask: Int, day: Int): Boolean = (bitmask and day) != 0

    fun toggle(bitmask: Int, day: Int): Int = bitmask xor day

    fun toList(bitmask: Int): List<Int> {
        return listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)
            .filter { isEnabled(bitmask, it) }
    }
}
