package com.example.carauth.viewmodel

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carauth.auth.AuthProvider
import com.example.carauth.auth.AuthProviderFactory
import com.example.carauth.auth.AuthState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.EnumMap
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authProviderFactory: AuthProviderFactory
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    val providers: List<AuthProvider> = authProviderFactory.getAllProviders()
    
    fun resetInfo() {
         _authState.value = AuthState.Idle
    }

    fun selectProvider(provider: AuthProvider) {
        _authState.value = AuthState.RequestingCode(provider)
        startAuth(provider)
    }
    
    private fun startAuth(provider: AuthProvider) {
        viewModelScope.launch {
            val result = provider.requestDeviceCode()
            result.onSuccess { response ->
                val qrContent = provider.buildQRCodeContent(response)
                val qrBitmap = generateQRCode(qrContent)
                
                _authState.value = AuthState.DisplayingQR(
                    provider = provider,
                    qrCodeBitmap = qrBitmap,
                    userCode = response.userCode,
                    verificationUrl = response.verificationUrl,
                    expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L)
                )
                
                // Start polling
                pollToken(provider, response.deviceCode, provider.getPollingInterval(response))
            }.onFailure { e ->
                _authState.value = AuthState.Error(provider, e.message ?: "Failed to request device code")
            }
        }
    }
    
    private suspend fun pollToken(provider: AuthProvider, deviceCode: String, interval: Long) {
        var isPolling = true
        while (isPolling) {
             // Check if we are still in a state where we should be polling (DisplayingQR)
             // Ideally we should have a separate Polling state or just check if the current state is DisplayingQR
             if (_authState.value !is AuthState.DisplayingQR) {
                 isPolling = false
                 break
             }

            delay(interval)
            
            val result = provider.pollToken(deviceCode)
            result.onSuccess { token ->
                isPolling = false
                _authState.value = AuthState.Authenticated(
                    provider = provider,
                    accessToken = token.accessToken,
                    userEmail = null, // In a real app we'd decode the ID token or call UserInfo endpoint
                    userName = null
                )
            }.onFailure { e ->
                 // If it's a "slow_down" or "authorization_pending" error, we continue polling.
                 // Our provider implementation returns failure for these, so we need to check the message.
                 if (e.message != "authorization_pending" && e.message != "slow_down") {
                     isPolling = false
                     _authState.value = AuthState.Error(provider, e.message ?: "Auth failed")
                 }
                 // If slow_down, we might want to increase interval, but for now just continue
            }
        }
    }

    private fun generateQRCode(content: String): Bitmap {
        val size = 512
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.MARGIN] = 1
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }
}
