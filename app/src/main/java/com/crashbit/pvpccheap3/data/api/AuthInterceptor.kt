package com.crashbit.pvpccheap3.data.api

import com.crashbit.pvpccheap3.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    companion object {
        private val NO_AUTH_ENDPOINTS = listOf(
            "api/auth/google",
            "api/prices/today",
            "api/prices/tomorrow"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Skip auth for endpoints that don't require it
        if (NO_AUTH_ENDPOINTS.any { path.contains(it) }) {
            return chain.proceed(originalRequest)
        }

        // Get token synchronously (we're in an interceptor)
        val token = runBlocking { tokenManager.getAccessToken() }

        return if (token != null) {
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
