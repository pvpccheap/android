package com.crashbit.pvpccheap3.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class TokenManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val TOKEN_EXPIRY_KEY = longPreferencesKey("token_expiry")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_PICTURE_KEY = stringPreferencesKey("user_picture")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACCESS_TOKEN_KEY]
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val token = prefs[ACCESS_TOKEN_KEY]
        val expiry = prefs[TOKEN_EXPIRY_KEY] ?: 0L
        !token.isNullOrEmpty() && System.currentTimeMillis() < expiry
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_EMAIL_KEY]
    }

    val userName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME_KEY]
    }

    val userPicture: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_PICTURE_KEY]
    }

    suspend fun getAccessToken(): String? {
        return context.dataStore.data.first()[ACCESS_TOKEN_KEY]
    }

    suspend fun isTokenValid(): Boolean {
        val prefs = context.dataStore.data.first()
        val token = prefs[ACCESS_TOKEN_KEY]
        val expiry = prefs[TOKEN_EXPIRY_KEY] ?: 0L
        return !token.isNullOrEmpty() && System.currentTimeMillis() < expiry
    }

    suspend fun saveAuthData(
        accessToken: String,
        expiresIn: Long,
        userId: String,
        email: String,
        name: String?,
        pictureUrl: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[TOKEN_EXPIRY_KEY] = System.currentTimeMillis() + (expiresIn * 1000)
            prefs[USER_ID_KEY] = userId
            prefs[USER_EMAIL_KEY] = email
            name?.let { prefs[USER_NAME_KEY] = it }
            pictureUrl?.let { prefs[USER_PICTURE_KEY] = it }
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(TOKEN_EXPIRY_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USER_EMAIL_KEY)
            prefs.remove(USER_NAME_KEY)
            prefs.remove(USER_PICTURE_KEY)
        }
    }
}
