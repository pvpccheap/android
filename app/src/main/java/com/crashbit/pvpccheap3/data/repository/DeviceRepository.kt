package com.crashbit.pvpccheap3.data.repository

import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val api: PvpcApi,
    private val googleHomeRepository: GoogleHomeRepository
) {
    suspend fun getDevices(): Result<List<Device>> {
        return try {
            Result.success(api.getDevices())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncDevices(devices: List<DeviceSyncItem>): Result<List<Device>> {
        return try {
            Result.success(api.syncDevices(DeviceSyncRequest(devices)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDevice(deviceId: String, name: String?, isActive: Boolean?): Result<Device> {
        return try {
            Result.success(api.updateDevice(deviceId, DeviceUpdateRequest(name = name, isActive = isActive)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reassigna el googleDeviceId d'un dispositiu.
     * Útil quan un dispositiu s'ha resincronitzat a Google Home i té un ID nou.
     */
    suspend fun reassignGoogleDeviceId(deviceId: String, newGoogleDeviceId: String): Result<Device> {
        return try {
            Result.success(api.updateDevice(deviceId, DeviceUpdateRequest(googleDeviceId = newGoogleDeviceId)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDevice(deviceId: String): Result<Unit> {
        return try {
            api.deleteDevice(deviceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sincronitza dispositius de Google Home amb el backend.
     */
    suspend fun syncFromGoogleHome(): Result<List<Device>> {
        // 1. Obtenir dispositius de Google Home
        val googleDevicesResult = googleHomeRepository.getControllableDevices()

        return googleDevicesResult.fold(
            onSuccess = { googleDevices ->
                // 2. Convertir a format del backend
                val syncItems = googleDevices.map { it.toSyncItem() }

                // 3. Enviar al backend
                syncDevices(syncItems)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * Obté dispositius amb el seu estat actual de Google Home.
     * Primer refresca els estats des del SDK si cal.
     */
    suspend fun getDevicesWithState(): Result<List<DeviceWithState>> {
        return try {
            // Primer refrescar els estats des de Google Home SDK
            googleHomeRepository.refreshDeviceStates()

            val devices = api.getDevices()

            val devicesWithState = devices.map { device ->
                val stateResult = googleHomeRepository.getDeviceState(device.googleDeviceId)
                DeviceWithState(
                    device = device,
                    isOn = stateResult.getOrNull(),
                    isOnline = stateResult.isSuccess,
                    isExecutingCommand = false
                )
            }

            Result.success(devicesWithState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Controla un dispositiu (ON/OFF) via Google Home.
     */
    suspend fun controlDevice(googleDeviceId: String, turnOn: Boolean): CommandResult {
        return googleHomeRepository.setDeviceOnOff(googleDeviceId, turnOn)
    }
}
