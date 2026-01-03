package com.crashbit.pvpccheap3.data.model

data class DailyPrices(
    val date: String,
    val prices: List<HourlyPrice>
)

data class HourlyPrice(
    val hour: Int,
    val price: Double
)
