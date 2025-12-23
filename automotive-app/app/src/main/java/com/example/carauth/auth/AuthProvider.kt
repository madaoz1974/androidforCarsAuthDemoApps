package com.example.carauth.auth

import androidx.compose.ui.graphics.Color
import com.example.carauth.data.model.DeviceCodeResponse
import com.example.carauth.data.model.TokenResponse

interface AuthProvider {
    val providerId: String
    val displayName: String
    val displayNameResId: Int
    val iconResId: Int
    val brandColor: Color
    
    suspend fun requestDeviceCode(): Result<DeviceCodeResponse>
    suspend fun pollToken(deviceCode: String): Result<TokenResponse>
    fun buildQRCodeContent(response: DeviceCodeResponse): String
    fun getPollingInterval(response: DeviceCodeResponse): Long
}
