package com.crashbit.pvpccheap3.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sistema de logs persistents que guarda a un fitxer.
 * Útil per debug quan no hi ha Android Studio connectat.
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val LOG_FILE_NAME = "pvpc_debug.log"
    private const val MAX_LOG_SIZE_BYTES = 1024 * 1024 // 1 MB màxim

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Inicialitza el logger amb el context de l'aplicació.
     */
    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            log("I", "FileLogger", "Logger inicialitzat")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicialitzant FileLogger: ${e.message}")
        }
    }

    /**
     * Escriu un log al fitxer.
     */
    @Synchronized
    fun log(level: String, tag: String, message: String) {
        val file = logFile ?: return

        try {
            // Rotar si és massa gran
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                rotateLog(file)
            }

            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp $level/$tag: $message\n"

            FileWriter(file, true).use { writer ->
                writer.write(logLine)
            }

            // També escriure al Logcat normal
            when (level) {
                "D" -> Log.d(tag, message)
                "I" -> Log.i(tag, message)
                "W" -> Log.w(tag, message)
                "E" -> Log.e(tag, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escrivint log: ${e.message}")
        }
    }

    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)

    fun e(tag: String, message: String, throwable: Throwable) {
        log("E", tag, "$message: ${throwable.message}")
        // Afegir stack trace
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    PrintWriter(writer).use { pw ->
                        throwable.printStackTrace(pw)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escrivint stack trace: ${e.message}")
        }
    }

    /**
     * Rota el fitxer de log (guarda l'antic com .old).
     */
    private fun rotateLog(file: File) {
        try {
            val oldFile = File(file.parent, "${LOG_FILE_NAME}.old")
            if (oldFile.exists()) {
                oldFile.delete()
            }
            file.renameTo(oldFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error rotant log: ${e.message}")
        }
    }

    /**
     * Llegeix tot el contingut del log.
     */
    fun readLogs(): String {
        return try {
            logFile?.readText() ?: "Log file not initialized"
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    /**
     * Llegeix les últimes N línies del log.
     */
    fun readLastLines(n: Int = 100): String {
        return try {
            val lines = logFile?.readLines() ?: return "Log file not initialized"
            lines.takeLast(n).joinToString("\n")
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    /**
     * Esborra el log.
     */
    fun clearLogs() {
        try {
            logFile?.writeText("")
            log("I", "FileLogger", "Logs esborrats")
        } catch (e: Exception) {
            Log.e(TAG, "Error esborrant logs: ${e.message}")
        }
    }

    /**
     * Retorna el path del fitxer de log.
     */
    fun getLogFilePath(): String? = logFile?.absolutePath
}
