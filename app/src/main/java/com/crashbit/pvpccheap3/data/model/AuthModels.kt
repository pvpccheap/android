package com.crashbit.pvpccheap3.data.model

import com.google.gson.annotations.SerializedName

data class GoogleLoginRequest(
    @SerializedName("id_token")
    val idToken: String
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Long,
    val user: UserResponse
)

data class UserResponse(
    val id: String,
    val email: String,
    val name: String?,
    @SerializedName("picture_url")
    val pictureUrl: String?
)
