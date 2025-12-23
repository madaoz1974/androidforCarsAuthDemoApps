package com.example.carauth.auth

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor

sealed class AuthState {
    object Idle : AuthState()
    object SelectingProvider : AuthState()
    data class RequestingCode(val provider: AuthProvider) : AuthState()
    data class DisplayingQR(
        val provider: AuthProvider,
        val qrCodeBitmap: Bitmap,
        val userCode: String,
        val verificationUrl: String,
        val expiresAt: Long
    ) : AuthState()
    data class Polling(
        val provider: AuthProvider,
        val progress: Float
    ) : AuthState()
    data class Authenticated(
        val provider: AuthProvider,
        val accessToken: String,
        val userEmail: String?,
        val userName: String?
    ) : AuthState()
    data class Error(
        val provider: AuthProvider?,
        val message: String,
        val canRetry: Boolean = true
    ) : AuthState()
}
