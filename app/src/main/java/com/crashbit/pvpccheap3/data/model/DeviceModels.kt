package com.crashbit.pvpccheap3.data.model

import com.google.gson.annotations.SerializedName

data class Device(
    val id: String,
    @SerializedName("google_device_id")
    val googleDeviceId: String,
    val name: String,
    @SerializedName("device_type")
    val deviceType: String?,
    val room: String?,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class DeviceSyncRequest(
    val devices: List<DeviceSyncItem>
)

data class DeviceSyncItem(
    @SerializedName("google_device_id")
    val googleDeviceId: String,
    val name: String,
    @SerializedName("device_type")
    val deviceType: String?,
    val room: String?
)

data class DeviceUpdateRequest(
    val name: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null,
    @SerializedName("google_device_id")
    val googleDeviceId: String? = null
)

/**
 * Estat d'un dispositiu per la UI (combina dades del backend amb estat de Google Home)
 */
data class DeviceWithState(
    val device: Device,
    val isOn: Boolean?,
    val isOnline: Boolean,
    val isExecutingCommand: Boolean = false
)

/**
 * Extensió per convertir GoogleHomeDevice a DeviceSyncItem
 */
fun GoogleHomeDevice.toSyncItem(): DeviceSyncItem {
    return DeviceSyncItem(
        googleDeviceId = this.id,
        name = this.name,
        deviceType = this.deviceType.name,
        room = this.roomName
    )
}
