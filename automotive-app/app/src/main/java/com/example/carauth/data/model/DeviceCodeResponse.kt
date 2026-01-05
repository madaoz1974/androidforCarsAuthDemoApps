package com.example.carauth.data.model

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val verificationUrlComplete: String?, // URL with pre-filled code (if available)
    val expiresIn: Int,
    val interval: Int
)
