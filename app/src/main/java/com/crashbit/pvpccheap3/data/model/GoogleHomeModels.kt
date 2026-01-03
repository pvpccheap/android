package com.crashbit.pvpccheap3.data.model

/**
 * Representa un dispositiu obtingut de Google Home SDK
 */
data class GoogleHomeDevice(
    val id: String,
    val name: String,
    val deviceType: GoogleDeviceType,
    val roomName: String?,
    val traits: List<DeviceTrait>,
    val isOnline: Boolean
)

/**
 * Tipus de dispositius suportats
 */
enum class GoogleDeviceType(val displayName: String) {
    LIGHT("Llum"),
    OUTLET("Endoll"),
    SWITCH("Interruptor"),
    THERMOSTAT("Termòstat"),
    FAN("Ventilador"),
    AIR_CONDITIONER("Aire Condicionat"),
    WASHER("Rentadora"),
    DRYER("Assecadora"),
    DISHWASHER("Rentavaixelles"),
    WATER_HEATER("Escalfador d'Aigua"),
    OTHER("Altre")
}

/**
 * Traits que pot tenir un dispositiu
 */
sealed class DeviceTrait {
    data class OnOff(val isOn: Boolean) : DeviceTrait()
    data class Brightness(val level: Int) : DeviceTrait() // 0-100
    data class ColorTemperature(val temperatureK: Int) : DeviceTrait()
    data class Temperature(val currentTemp: Float, val targetTemp: Float) : DeviceTrait()
}

/**
 * Resultat del consentiment de Google Home
 */
sealed class HomeAuthResult {
    data object Success : HomeAuthResult()
    data class Error(val message: String) : HomeAuthResult()
    data object Cancelled : HomeAuthResult()
}

/**
 * Estructura d'una casa de Google Home
 */
data class GoogleHomeStructure(
    val id: String,
    val name: String,
    val rooms: List<GoogleHomeRoom>
)

data class GoogleHomeRoom(
    val id: String,
    val name: String
)

/**
 * Comanda per controlar dispositiu
 */
sealed class DeviceCommand {
    data class SetOnOff(val on: Boolean) : DeviceCommand()
    data class SetBrightness(val level: Int) : DeviceCommand()
}

/**
 * Resultat d'executar una comanda
 */
sealed class CommandResult {
    data object Success : CommandResult()
    data class Error(val message: String) : CommandResult()
}

/**
 * Estat de l'autorització de Google Home
 */
enum class GoogleHomeAuthState {
    NOT_INITIALIZED,
    CHECKING,
    NOT_AUTHORIZED,
    AUTHORIZED,
    ERROR
}

/**
 * Representa un canvi d'estat d'un dispositiu
 */
data class DeviceStateChange(
    val deviceId: String,
    val isOn: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
