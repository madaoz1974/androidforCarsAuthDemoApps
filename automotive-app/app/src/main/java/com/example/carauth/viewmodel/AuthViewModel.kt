package com.example.carauth.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carauth.BuildConfig
import com.example.carauth.auth.AuthProvider
import com.example.carauth.auth.AuthProviderFactory
import com.example.carauth.auth.AuthState
import com.example.carauth.bluetooth.BluetoothAuthServiceInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.EnumMap
import javax.inject.Inject
import kotlin.math.pow

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authProviderFactory: AuthProviderFactory,
    private val bluetoothAuthService: BluetoothAuthServiceInterface
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
        private const val MAX_START_AUTH_RETRIES = 3
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val providers: List<AuthProvider> = authProviderFactory.getAllProviders()
    
    // Expose Bluetooth states for UI
    val isBluetoothAdvertising = bluetoothAuthService.isAdvertising
    val bluetoothConnectionState = bluetoothAuthService.connectionState
    
    // Job reference for cancellation
    private var pollingJob: Job? = null

    init {
        // Listen for tokens received via Bluetooth
        viewModelScope.launch {
            bluetoothAuthService.receivedToken.collect { token ->
                if (token != null && _authState.value is AuthState.DisplayingQR) {
                    val currentState = _authState.value as AuthState.DisplayingQR
                    log("Token received via Bluetooth!")
                    cancelPolling()
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
        cancelPolling()
        _authState.value = AuthState.Idle
        bluetoothAuthService.stopAdvertising()
    }
    
    fun cancelAuth() {
        cancelPolling()
        bluetoothAuthService.stopAdvertising()
        _authState.value = AuthState.Idle
    }
    
    private fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun selectProvider(provider: AuthProvider) {
        _authState.value = AuthState.RequestingCode(provider)
        startAuth(provider, retryCount = 0)
    }

    private fun startAuth(provider: AuthProvider, retryCount: Int) {
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

                // Start Bluetooth advertising for direct token transfer
                bluetoothAuthService.startAdvertising()
                
                // Start OAuth polling as fallback
                startPolling(provider, response.deviceCode, provider.getPollingInterval(response))
            }.onFailure { e ->
                log("startAuth failed (retry $retryCount/$MAX_START_AUTH_RETRIES): ${e.message}")
                if (retryCount < MAX_START_AUTH_RETRIES) {
                    // Exponential backoff: 1s, 2s, 4s
                    val backoffDelay = (2.0.pow(retryCount) * 1000).toLong()
                    delay(backoffDelay)
                    startAuth(provider, retryCount + 1)
                } else {
                    _authState.value = AuthState.Error(
                        message = "ネットワークエラー: ${e.message ?: "接続できませんでした"}",
                        canRetry = true
                    )
                }
            }
        }
    }
    
    private fun startPolling(provider: AuthProvider, deviceCode: String, interval: Long) {
        cancelPolling() // Cancel existing polling if any
        pollingJob = viewModelScope.launch {
            try {
                pollToken(provider, deviceCode, interval)
            } catch (e: CancellationException) {
                log("Polling cancelled")
                throw e // Re-throw CancellationException as per coroutine best practices
            }
        }
    }

    private suspend fun pollToken(provider: AuthProvider, deviceCode: String, interval: Long) {
        log("pollToken started: deviceCode=${deviceCode.take(10)}..., interval=$interval")

        while (true) {
            // Check if state changed (e.g., Bluetooth token received or user cancelled)
            if (_authState.value !is AuthState.DisplayingQR) {
                log("pollToken: State changed, stopping polling")
                break
            }

            delay(interval)
            log("pollToken: Polling for token...")

            val result = provider.pollToken(deviceCode)
            result.onSuccess { token ->
                log("pollToken: SUCCESS! Token received via OAuth")
                bluetoothAuthService.stopAdvertising()
                _authState.value = AuthState.Authenticated(
                    provider = provider,
                    accessToken = token.accessToken,
                    userEmail = null,
                    userName = null
                )
                return // Exit the function on success
            }.onFailure { e ->
                val message = e.message ?: ""
                log("pollToken: Failed with message: $message")
                when (message) {
                    "authorization_pending" -> {
                        log("pollToken: Authorization pending, continuing...")
                        // Continue polling
                    }
                    "slow_down" -> {
                        log("pollToken: Slow down, waiting extra interval")
                        delay(interval * 2)
                    }
                    "expired_token" -> {
                        log("pollToken: Device code expired")
                        bluetoothAuthService.stopAdvertising()
                        _authState.value = AuthState.Error(
                            message = "認証コードの有効期限が切れました",
                            canRetry = true
                        )
                        return
                    }
                    "access_denied" -> {
                        log("pollToken: Access denied by user")
                        bluetoothAuthService.stopAdvertising()
                        _authState.value = AuthState.Error(
                            message = "アクセスが拒否されました",
                            canRetry = true
                        )
                        return
                    }
                    else -> {
                        log("pollToken: Temporary error ($message), will retry")
                        delay(interval)
                    }
                }
            }
        }
        log("pollToken: Polling loop ended")
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
        cancelPolling()
        bluetoothAuthService.stopAdvertising()
    }
    
    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, message)
        }
    }
}
