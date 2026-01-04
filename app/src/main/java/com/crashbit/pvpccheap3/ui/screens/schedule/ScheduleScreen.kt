package com.crashbit.pvpccheap3.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.crashbit.pvpccheap3.data.model.ScheduledAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ca")) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refrescar horari quan la pantalla torna a ser visible (sense mostrar loading)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshSchedule()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Horari") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Date selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.selectDate(uiState.selectedDate.minusDays(1))
                    }
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Dia anterior")
                }

                Text(
                    text = uiState.selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(
                    onClick = {
                        viewModel.selectDate(uiState.selectedDate.plusDays(1))
                    }
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Dia següent")
                }
            }

            // Today button
            if (uiState.selectedDate != LocalDate.now()) {
                TextButton(
                    onClick = { viewModel.selectDate(LocalDate.now()) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Avui")
                }
            }

            Divider()

            // Schedule content
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.scheduledActions.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No hi ha accions programades",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.scheduledActions, key = { it.id }) { action ->
                                ScheduleActionCard(action = action)
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

// Colors personalitzats per als estats
private val SuccessGreen = Color(0xFF2E7D32)  // Verd fosc
private val WarningAmber = Color(0xFFFF8F00)  // Taronja/Ambre
private val ErrorRed = Color(0xFFC62828)      // Vermell

@Composable
fun ScheduleActionCard(action: ScheduledAction) {
    val statusColor = when (action.status) {
        "pending" -> MaterialTheme.colorScheme.primary      // Blau
        "executed", "executed_on", "executed_off" -> SuccessGreen  // Verd
        "failed" -> ErrorRed                                 // Vermell
        "missed" -> WarningAmber                            // Taronja
        "cancelled" -> MaterialTheme.colorScheme.outline    // Gris
        else -> WarningAmber  // Per defecte taronja (visible)
    }

    val statusText = when (action.status) {
        "pending" -> "Pendent"
        "executed" -> "Executat"
        "executed_on" -> "Encès"
        "executed_off" -> "Apagat"
        "failed" -> "Fallat"
        "missed" -> "Perdut"
        "cancelled" -> "Cancel·lat"
        else -> action.status
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Time block
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = action.startTime.take(5),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = action.endTime.take(5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Device info
                Column {
                    Text(
                        text = action.deviceName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Status badge
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}
