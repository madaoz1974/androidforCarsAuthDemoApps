package com.example.carauth.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carauth.auth.AuthProvider
import com.example.carauth.auth.AuthProviderFactory
import com.example.carauth.auth.AuthState
import com.example.carauth.bluetooth.BluetoothAuthService
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
    private val authProviderFactory: AuthProviderFactory,
    private val bluetoothAuthService: BluetoothAuthService
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val providers: List<AuthProvider> = authProviderFactory.getAllProviders()
    
    // Expose Bluetooth states for UI
    val isBluetoothAdvertising = bluetoothAuthService.isAdvertising
    val bluetoothConnectionState = bluetoothAuthService.connectionState

    init {
        // Listen for tokens received via Bluetooth
        viewModelScope.launch {
            bluetoothAuthService.receivedToken.collect { token ->
                if (token != null && _authState.value is AuthState.DisplayingQR) {
                    val currentState = _authState.value as AuthState.DisplayingQR
                    android.util.Log.d("AuthViewModel", "Token received via Bluetooth!")
                    _authState.value = AuthState.Authenticated(
                        provider = currentState.provider,
                        accessToken = token,
                        userEmail = null,
                        userName = null
                    )
                    bluetoothAuthService.stopAdvertising()
                    bluetoothAuthService.resetToken()
                }
            }
        }
    }

    fun resetInfo() {
        _authState.value = AuthState.Idle
        bluetoothAuthService.stopAdvertising()
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
                    expiresAt = Long.MAX_VALUE
                )

                // Start Bluetooth advertising for direct token transfer
                bluetoothAuthService.startAdvertising()
                
                // Also start OAuth polling as fallback
                pollToken(provider, response.deviceCode, provider.getPollingInterval(response))
            }.onFailure { e ->
                delay(5000)
                startAuth(provider)
            }
        }
    }

    private suspend fun pollToken(provider: AuthProvider, deviceCode: String, interval: Long) {
        android.util.Log.d("AuthViewModel", "pollToken started: deviceCode=${deviceCode.take(10)}..., interval=$interval")
        var isPolling = true

        while (isPolling) {
            // Check if state changed (e.g., Bluetooth token received or user cancelled)
            if (_authState.value !is AuthState.DisplayingQR) {
                android.util.Log.d("AuthViewModel", "pollToken: State changed, stopping polling")
                isPolling = false
                break
            }

            delay(interval)
            android.util.Log.d("AuthViewModel", "pollToken: Polling for token...")

            val result = provider.pollToken(deviceCode)
            result.onSuccess { token ->
                android.util.Log.d("AuthViewModel", "pollToken: SUCCESS! Token received via OAuth")
                isPolling = false
                bluetoothAuthService.stopAdvertising()
                _authState.value = AuthState.Authenticated(
                    provider = provider,
                    accessToken = token.accessToken,
                    userEmail = null,
                    userName = null
                )
            }.onFailure { e ->
                val message = e.message ?: ""
                android.util.Log.d("AuthViewModel", "pollToken: Failed with message: $message")
                when (message) {
                    "authorization_pending" -> {
                        android.util.Log.d("AuthViewModel", "pollToken: Authorization pending, continuing...")
                    }
                    "slow_down" -> {
                        android.util.Log.d("AuthViewModel", "pollToken: Slow down, waiting extra interval")
                        delay(interval * 2)
                    }
                    "expired_token" -> {
                        android.util.Log.w("AuthViewModel", "pollToken: Device code expired")
                        isPolling = false
                    }
                    "access_denied" -> {
                        android.util.Log.w("AuthViewModel", "pollToken: Access denied by user")
                        isPolling = false
                    }
                    else -> {
                        android.util.Log.w("AuthViewModel", "pollToken: Temporary error ($message), will retry")
                        delay(interval)
                    }
                }
            }
        }
        android.util.Log.d("AuthViewModel", "pollToken: Polling loop ended")
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
    
    override fun onCleared() {
        super.onCleared()
        bluetoothAuthService.stopAdvertising()
    }
}

