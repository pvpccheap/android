package com.crashbit.pvpccheap3.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crashbit.pvpccheap3.data.model.*
import com.crashbit.pvpccheap3.ui.components.BatteryOptimizationBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dispositius") },
                actions = {
                    // Botó per sincronitzar amb Google Home
                    IconButton(
                        onClick = { viewModel.startGoogleHomeSync() },
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sincronitzar")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                uiState.googleHomeAuthState == GoogleHomeAuthState.NOT_AUTHORIZED -> {
                    // Mostrar pantalla per connectar Google Home
                    GoogleHomeConnectPrompt(
                        onConnectClick = { viewModel.requestGoogleHomeAuthorization() },
                        isLoading = uiState.isSyncing
                    )
                }
                uiState.devices.isEmpty() -> {
                    EmptyDevicesPrompt(
                        onSyncClick = { viewModel.startGoogleHomeSync() }
                    )
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Banner d'optimització de bateria
                        BatteryOptimizationBanner()

                        DevicesList(
                            devices = uiState.devices,
                            onControlDevice = { device, turnOn ->
                                viewModel.controlDevice(device, turnOn)
                            },
                            onToggleActive = { viewModel.toggleDeviceActive(it.device) },
                            onDelete = { viewModel.deleteDevice(it.device.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Progress indicator
            if (uiState.isLoading && uiState.devices.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    // Dialog per seleccionar dispositius
    if (uiState.showDeviceSelector) {
        DeviceSelectorDialog(
            devices = uiState.googleHomeDevices,
            onConfirm = { selectedDevices ->
                viewModel.syncSelectedDevices(selectedDevices)
            },
            onDismiss = { viewModel.cancelDeviceSelection() }
        )
    }
}

@Composable
fun GoogleHomeConnectPrompt(
    onConnectClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connecta Google Home",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Per controlar els teus dispositius, cal connectar la teva llar de Google Home",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onConnectClick,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Connectar Google Home")
        }
    }
}

@Composable
fun EmptyDevicesPrompt(onSyncClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DevicesOther,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hi ha dispositius",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sincronitza amb Google Home per afegir dispositius",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onSyncClick) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sincronitzar ara")
        }
    }
}

@Composable
fun DevicesList(
    devices: List<DeviceWithState>,
    onControlDevice: (DeviceWithState, Boolean) -> Unit,
    onToggleActive: (DeviceWithState) -> Unit,
    onDelete: (DeviceWithState) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(devices, key = { it.device.id }) { deviceWithState ->
            DeviceCardWithControl(
                deviceWithState = deviceWithState,
                onControlDevice = onControlDevice,
                onToggleActive = onToggleActive,
                onDelete = onDelete
            )
        }
    }
}

@Composable
fun DeviceCardWithControl(
    deviceWithState: DeviceWithState,
    onControlDevice: (DeviceWithState, Boolean) -> Unit,
    onToggleActive: (DeviceWithState) -> Unit,
    onDelete: (DeviceWithState) -> Unit
) {
    val device = deviceWithState.device

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!deviceWithState.isOnline) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Desconnectat",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    device.room?.let { room ->
                        Text(
                            text = room,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    device.deviceType?.let { type ->
                        Text(
                            text = type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Botó ON/OFF
                if (deviceWithState.isOnline && device.isActive) {
                    Box {
                        if (deviceWithState.isExecutingCommand) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            FilledIconToggleButton(
                                checked = deviceWithState.isOn == true,
                                onCheckedChange = { turnOn ->
                                    onControlDevice(deviceWithState, turnOn)
                                }
                            ) {
                                Icon(
                                    if (deviceWithState.isOn == true)
                                        Icons.Default.PowerSettingsNew
                                    else
                                        Icons.Default.Power,
                                    contentDescription = if (deviceWithState.isOn == true) "Apagar" else "Encendre"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Controls secundaris
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (device.isActive) "Optimització activa" else "Optimització inactiva",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (device.isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = device.isActive,
                        onCheckedChange = { onToggleActive(deviceWithState) },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                IconButton(onClick = { onDelete(deviceWithState) }) {
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

@Composable
fun DeviceSelectorDialog(
    devices: List<GoogleHomeDevice>,
    onConfirm: (List<GoogleHomeDevice>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDevices by remember { mutableStateOf(devices.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecciona dispositius") },
        text = {
            if (devices.isEmpty()) {
                Text("No s'han trobat dispositius controlables a Google Home")
            } else {
                LazyColumn {
                    items(devices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = device in selectedDevices,
                                onCheckedChange = { checked ->
                                    selectedDevices = if (checked) {
                                        selectedDevices + device
                                    } else {
                                        selectedDevices - device
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row {
                                    device.roomName?.let { room ->
                                        Text(
                                            text = room,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = " · ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = device.deviceType.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDevices.toList()) },
                enabled = selectedDevices.isNotEmpty()
            ) {
                Text("Sincronitzar (${selectedDevices.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel·lar")
            }
        }
    )
}
