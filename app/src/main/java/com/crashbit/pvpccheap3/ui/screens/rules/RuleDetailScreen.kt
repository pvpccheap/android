package com.crashbit.pvpccheap3.ui.screens.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crashbit.pvpccheap3.data.model.DaysOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDetailScreen(
    ruleId: String?,
    onNavigateBack: () -> Unit,
    viewModel: RuleDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Editar regla" else "Nova regla") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Enrere")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Guardar")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Device selector
                var deviceExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = deviceExpanded,
                    onExpandedChange = { deviceExpanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.devices.find { it.id == uiState.selectedDeviceId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Dispositiu") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !uiState.isEditMode),
                        enabled = !uiState.isEditMode
                    )
                    ExposedDropdownMenu(
                        expanded = deviceExpanded,
                        onDismissRequest = { deviceExpanded = false }
                    ) {
                        uiState.devices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name) },
                                onClick = {
                                    viewModel.updateSelectedDevice(device.id)
                                    deviceExpanded = false
                                }
                            )
                        }
                    }
                }

                // Name
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Nom de la regla") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Max hours
                Text(
                    text = "Màxim d'hores: ${uiState.maxHours}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.maxHours.toFloat(),
                    onValueChange = { viewModel.updateMaxHours(it.toInt()) },
                    valueRange = 1f..24f,
                    steps = 22
                )

                // Min continuous hours
                Text(
                    text = "Mínim d'hores contínues: ${uiState.minContinuousHours}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.minContinuousHours.toFloat(),
                    onValueChange = { viewModel.updateMinContinuousHours(it.toInt()) },
                    valueRange = 1f..uiState.maxHours.toFloat(),
                    steps = (uiState.maxHours - 2).coerceAtLeast(0)
                )

                // Days of week
                Text(
                    text = "Dies de la setmana",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val days = listOf(
                        "Dl" to DaysOfWeek.MONDAY,
                        "Dm" to DaysOfWeek.TUESDAY,
                        "Dc" to DaysOfWeek.WEDNESDAY,
                        "Dj" to DaysOfWeek.THURSDAY,
                        "Dv" to DaysOfWeek.FRIDAY,
                        "Ds" to DaysOfWeek.SATURDAY,
                        "Dg" to DaysOfWeek.SUNDAY
                    )

                    days.forEach { (label, day) ->
                        val isSelected = DaysOfWeek.isEnabled(uiState.daysOfWeek, day)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleDayOfWeek(day) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                // Time window (simplified - could add time pickers)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.timeWindowStart?.take(5) ?: "",
                        onValueChange = {
                            val time = if (it.isNotEmpty()) "$it:00" else null
                            viewModel.updateTimeWindow(time, uiState.timeWindowEnd)
                        },
                        label = { Text("Des de (HH:MM)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.timeWindowEnd?.take(5) ?: "",
                        onValueChange = {
                            val time = if (it.isNotEmpty()) "$it:00" else null
                            viewModel.updateTimeWindow(uiState.timeWindowStart, time)
                        },
                        label = { Text("Fins a (HH:MM)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
