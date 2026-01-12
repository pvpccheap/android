package com.crashbit.pvpccheap3.ui.screens.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.crashbit.pvpccheap3.data.model.DaysOfWeek
import com.crashbit.pvpccheap3.data.model.RuleWithDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onNavigateToRuleDetail: (String?) -> Unit,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refrescar regles quan la pantalla torna a ser visible (sense mostrar loading)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshRules()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Regles") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToRuleDetail(null) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Afegir regla")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.rules.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No hi ha regles",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Crea una regla per començar a estalviar",
                            style = MaterialTheme.typography.bodyMedium,
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
                        items(uiState.rules, key = { it.id }) { rule ->
                            RuleCard(
                                rule = rule,
                                onToggleEnabled = { viewModel.toggleRuleEnabled(rule) },
                                onDelete = { viewModel.deleteRule(rule.id) },
                                onClick = { onNavigateToRuleDetail(rule.id) }
                            )
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
fun RuleCard(
    rule: RuleWithDevice,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = rule.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildRuleDescription(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun buildRuleDescription(rule: RuleWithDevice): String {
    val parts = mutableListOf<String>()

    parts.add("${rule.maxHours}h")

    if (rule.minContinuousHours > 1) {
        parts.add("mínim ${rule.minContinuousHours}h contínues")
    }

    if (rule.timeWindowStart != null && rule.timeWindowEnd != null) {
        parts.add("${rule.timeWindowStart.take(5)}-${rule.timeWindowEnd.take(5)}")
    }

    if (rule.daysOfWeek != DaysOfWeek.ALL_DAYS) {
        parts.add(formatDaysOfWeek(rule.daysOfWeek))
    }

    return parts.joinToString(" • ")
}

private fun formatDaysOfWeek(bitmask: Int): String {
    val days = listOf("Dl", "Dm", "Dc", "Dj", "Dv", "Ds", "Dg")
    val bits = listOf(
        DaysOfWeek.MONDAY,
        DaysOfWeek.TUESDAY,
        DaysOfWeek.WEDNESDAY,
        DaysOfWeek.THURSDAY,
        DaysOfWeek.FRIDAY,
        DaysOfWeek.SATURDAY,
        DaysOfWeek.SUNDAY
    )

    return days.filterIndexed { index, _ ->
        DaysOfWeek.isEnabled(bitmask, bits[index])
    }.joinToString("")
}
