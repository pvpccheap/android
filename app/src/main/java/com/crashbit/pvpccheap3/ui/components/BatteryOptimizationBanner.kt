package com.crashbit.pvpccheap3.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crashbit.pvpccheap3.util.BatteryOptimizationHelper

/**
 * Banner que avisa l'usuari si l'optimització de bateria està activada.
 */
@Composable
fun BatteryOptimizationBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isOptimized by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }

    // Comprovar estat inicial i quan torna a primer pla
    LaunchedEffect(Unit) {
        isOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    // Només mostrar si l'optimització està activada
    if (isOptimized) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Optimització de bateria activa",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "L'execució automàtica pot fallar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
                TextButton(
                    onClick = { showDialog = true }
                ) {
                    Text("Configurar")
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null)
            },
            title = {
                Text("Desactivar optimització de bateria")
            },
            text = {
                Text(BatteryOptimizationHelper.getExplanationMessage())
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        openBatterySettings(context)
                    }
                ) {
                    Text("Obrir configuració")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Més tard")
                }
            }
        )
    }
}

private fun openBatterySettings(context: Context) {
    val intent = BatteryOptimizationHelper.createBatteryOptimizationIntent(context)
    if (intent != null) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback a configuració de l'app
            try {
                context.startActivity(
                    BatteryOptimizationHelper.createAppBatterySettingsIntent(context)
                )
            } catch (e2: Exception) {
                // No podem obrir la configuració
            }
        }
    }
}
