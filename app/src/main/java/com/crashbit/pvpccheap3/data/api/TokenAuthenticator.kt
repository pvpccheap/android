package com.crashbit.pvpccheap3.data.api

import android.util.Log
import com.crashbit.pvpccheap3.data.local.TokenManager
import com.crashbit.pvpccheap3.data.model.AuthResponse
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Authenticator que intercepta respostes 401 i intenta fer refresh del token.
 * Si el refresh falla, notifica que cal fer re-login.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val baseUrl: String,
    private val gson: Gson
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val MAX_RETRIES = 1
    }

    // Mutex per evitar múltiples refresh simultanis
    private val refreshMutex = Mutex()

    // Callback per notificar quan cal re-login
    private var onAuthenticationRequired: (() -> Unit)? = null

    fun setOnAuthenticationRequired(callback: () -> Unit) {
        onAuthenticationRequired = callback
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d(TAG, "401 rebut per: ${response.request.url}")

        // Evitar bucles infinits - no reintentar si ja hem provat
        val retryCount = response.request.header("X-Retry-Count")?.toIntOrNull() ?: 0
        if (retryCount >= MAX_RETRIES) {
            Log.d(TAG, "Màxim de reintents assolit, notificant re-login necessari")
            onAuthenticationRequired?.invoke()
            return null
        }

        // No intentar refresh per a l'endpoint de refresh (evitar bucle)
        if (response.request.url.encodedPath.contains("api/auth/refresh")) {
            Log.d(TAG, "El refresh ha fallat, cal re-login")
            onAuthenticationRequired?.invoke()
            return null
        }

        return runBlocking {
            refreshMutex.withLock {
                // Comprovar si un altre thread ja ha fet refresh
                val currentToken = tokenManager.getAccessToken()
                val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

                if (currentToken != null && currentToken != requestToken) {
                    // El token ja s'ha refrescat, reintentar amb el nou token
                    Log.d(TAG, "Token ja refrescat per un altre thread, reintentant")
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .header("X-Retry-Count", (retryCount + 1).toString())
                        .build()
                }

                // Intentar refresh
                Log.d(TAG, "Intentant refresh del token...")
                val newToken = tryRefreshToken()

                if (newToken != null) {
                    Log.d(TAG, "Token refrescat correctament")
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .header("X-Retry-Count", (retryCount + 1).toString())
                        .build()
                } else {
                    Log.d(TAG, "Refresh fallit, cal re-login")
                    onAuthenticationRequired?.invoke()
                    null
                }
            }
        }
    }

    /**
     * Intenta fer refresh del token cridant l'endpoint directament.
     * Retorna el nou token si té èxit, null si falla.
     */
    private suspend fun tryRefreshToken(): String? {
        return try {
            val currentToken = tokenManager.getAccessToken() ?: return null

            // Crear un client simple sense l'authenticator (evitar recursivitat)
            val client = OkHttpClient.Builder().build()

            val request = Request.Builder()
                .url("${baseUrl}api/auth/refresh")
                .post("".toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $currentToken")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                val authResponse = gson.fromJson(body, AuthResponse::class.java)

                // Guardar el nou token
                tokenManager.saveAuthData(
                    accessToken = authResponse.accessToken,
                    expiresIn = authResponse.expiresIn,
                    userId = authResponse.user.id,
                    email = authResponse.user.email,
                    name = authResponse.user.name,
                    pictureUrl = authResponse.user.pictureUrl
                )

                authResponse.accessToken
            } else {
                Log.e(TAG, "Refresh fallit: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fent refresh: ${e.message}", e)
            null
        }
    }
}
