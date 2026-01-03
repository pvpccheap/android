package com.crashbit.pvpccheap3.ui.screens.prices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crashbit.pvpccheap3.data.model.HourlyPrice
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(
    viewModel: PricesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preus PVPC") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab selector
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Avui") }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Demà") },
                    enabled = uiState.tomorrowPrices != null
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    viewModel.getCurrentPrices().isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (uiState.selectedTab == 1) {
                                    "Els preus de demà encara no estan disponibles"
                                } else {
                                    "No hi ha dades de preus disponibles"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (uiState.selectedTab == 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Normalment es publiquen cap a les 20:30",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        val prices = viewModel.getCurrentPrices()
                        val cheapestHours = viewModel.getCheapestHours(6)
                        val minPrice = prices.minOfOrNull { it.price } ?: 0.0
                        val maxPrice = prices.maxOfOrNull { it.price } ?: 1.0

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Stats summary
                            item {
                                PricesSummaryCard(prices = prices)
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Price bars
                            items(prices.sortedBy { it.hour }) { hourlyPrice ->
                                PriceBar(
                                    hourlyPrice = hourlyPrice,
                                    minPrice = minPrice,
                                    maxPrice = maxPrice,
                                    isCheapest = hourlyPrice.hour in cheapestHours
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
fun PricesSummaryCard(prices: List<HourlyPrice>) {
    val minPrice = prices.minByOrNull { it.price }
    val maxPrice = prices.maxByOrNull { it.price }
    val avgPrice = prices.map { it.price }.average()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PriceStat(
                label = "Mínim",
                price = minPrice?.price ?: 0.0,
                hour = minPrice?.hour,
                color = MaterialTheme.colorScheme.tertiary
            )
            PriceStat(
                label = "Mitjà",
                price = avgPrice,
                hour = null,
                color = MaterialTheme.colorScheme.primary
            )
            PriceStat(
                label = "Màxim",
                price = maxPrice?.price ?: 0.0,
                hour = maxPrice?.hour,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun PriceStat(
    label: String,
    price: Double,
    hour: Int?,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format("%.4f", price),
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(
            text = "€/kWh",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        hour?.let {
            Text(
                text = "${it}:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PriceBar(
    hourlyPrice: HourlyPrice,
    minPrice: Double,
    maxPrice: Double,
    isCheapest: Boolean
) {
    val range = maxPrice - minPrice
    val normalizedPrice = if (range > 0) {
        ((hourlyPrice.price - minPrice) / range).toFloat()
    } else {
        0.5f
    }

    val barColor = when {
        isCheapest -> MaterialTheme.colorScheme.tertiary
        normalizedPrice < 0.33f -> MaterialTheme.colorScheme.primary
        normalizedPrice < 0.66f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hour label
        Text(
            text = String.format("%02d:00", hourlyPrice.hour),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp)
        )

        // Price bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.15f + (normalizedPrice * 0.85f))
                    .background(
                        color = barColor,
                        shape = MaterialTheme.shapes.small
                    )
            )
        }

        // Price value
        Text(
            text = String.format("%.4f", hourlyPrice.price),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End
        )
    }
}
