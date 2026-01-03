package com.crashbit.pvpccheap3.data.repository

import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.local.TokenManager
import com.crashbit.pvpccheap3.data.model.AuthResponse
import com.crashbit.pvpccheap3.data.model.GoogleLoginRequest
import com.crashbit.pvpccheap3.data.model.UserResponse
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: PvpcApi,
    private val tokenManager: TokenManager
) {
    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn
    val userEmail: Flow<String?> = tokenManager.userEmail
    val userName: Flow<String?> = tokenManager.userName
    val userPicture: Flow<String?> = tokenManager.userPicture

    suspend fun loginWithGoogle(idToken: String): Result<AuthResponse> {
        return try {
            val response = api.loginWithGoogle(GoogleLoginRequest(idToken))
            tokenManager.saveAuthData(
                accessToken = response.accessToken,
                expiresIn = response.expiresIn,
                userId = response.user.id,
                email = response.user.email,
                name = response.user.name,
                pictureUrl = response.user.pictureUrl
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<AuthResponse> {
        return try {
            val response = api.refreshToken()
            tokenManager.saveAuthData(
                accessToken = response.accessToken,
                expiresIn = response.expiresIn,
                userId = response.user.id,
                email = response.user.email,
                name = response.user.name,
                pictureUrl = response.user.pictureUrl
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<UserResponse> {
        return try {
            Result.success(api.getCurrentUser())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        tokenManager.clearAuthData()
    }

    suspend fun isTokenValid(): Boolean {
        return tokenManager.isTokenValid()
    }
}
