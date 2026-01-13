package com.crashbit.pvpccheap3.ui.screens.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crashbit.pvpccheap3.util.FileLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(
    onBack: () -> Unit
) {
    var logs by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    // Carregar logs inicialment
    LaunchedEffect(Unit) {
        logs = FileLogger.readLastLines(200)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Enrere")
                    }
                },
                actions = {
                    IconButton(onClick = { logs = FileLogger.readLastLines(200) }) {
                        Icon(Icons.Default.Refresh, "Refrescar")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, "Esborrar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Info del fitxer
            Text(
                text = "Path: ${FileLogger.getLogFilePath() ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            HorizontalDivider()

            // Logs amb scroll horitzontal i vertical
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    text = if (logs.isEmpty()) "No hi ha logs" else logs,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFCCCCCC),
                    lineHeight = 14.sp
                )
            }
        }
    }

    // Diàleg de confirmació per esborrar
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Esborrar logs?") },
            text = { Text("Aquesta acció no es pot desfer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        FileLogger.clearLogs()
                        logs = ""
                        showClearDialog = false
                    }
                ) {
                    Text("Esborrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel·lar")
                }
            }
        )
    }
}
