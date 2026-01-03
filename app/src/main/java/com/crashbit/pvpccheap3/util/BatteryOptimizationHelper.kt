package com.crashbit.pvpccheap3.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Helper per gestionar l'optimització de bateria.
 * L'app necessita estar exempta per funcionar correctament en segon pla.
 */
object BatteryOptimizationHelper {

    /**
     * Comprova si l'app està exempta de l'optimització de bateria.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Crea un Intent per obrir la configuració d'optimització de bateria.
     * Retorna null si no es pot obrir directament.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent? {
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } catch (e: Exception) {
            // Fallback a la configuració general de bateria
            try {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Crea un Intent per obrir la configuració de bateria de l'app.
     */
    fun createAppBatterySettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Missatge explicatiu per l'usuari.
     */
    fun getExplanationMessage(): String {
        return "Per garantir que els dispositius s'encenguin i apaguin automàticament, " +
                "cal desactivar l'optimització de bateria per aquesta aplicació.\n\n" +
                "Això permet que l'app s'executi en segon pla i controli els dispositius " +
                "segons l'horari programat."
    }
}
