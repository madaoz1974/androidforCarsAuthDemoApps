package com.example.carauth.data.model

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int,
    val idToken: String?
)
