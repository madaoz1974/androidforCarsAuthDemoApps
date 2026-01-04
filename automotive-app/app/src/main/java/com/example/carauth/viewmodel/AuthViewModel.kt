package com.example.carauth.viewmodel

import android.graphics.Bitmap
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
                    expiresAt = Long.MAX_VALUE // Set a very long timeout
                )

                // Start polling
                pollToken(provider, response.deviceCode, provider.getPollingInterval(response))
            }.onFailure { e ->
                // Instead of showing an error, wait and then restart the auth flow.
                delay(5000) // Wait 5 seconds before retrying
                startAuth(provider)
            }
        }
    }

    private suspend fun pollToken(provider: AuthProvider, deviceCode: String, interval: Long) {
        var isPolling = true
        var retryCount = 0
        val maxRetries = 5  // 一時的なエラーの最大リトライ回数

        while (isPolling) {
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
                    userEmail = null,
                    userName = null
                )
            }.onFailure { e ->
                when (val message = e.message ?: "") {
                    "authorization_pending" -> {
                        // ユーザーがまだ認証していない - 継続
                        retryCount = 0  // 正常なポーリング応答なのでリセット
                    }
                    "slow_down" -> {
                        // レート制限 - 待機時間を延長して継続
                        delay(interval)
                        retryCount = 0
                    }
                    "expired_token", "access_denied", "invalid_grant" -> {
                        // 永続的なエラー - 再認証が必要
                        isPolling = false
                        startAuth(provider)
                    }
                    else -> {
                        // 一時的なエラー（ネットワーク等）- リトライ
                        retryCount++
                        if (retryCount >= maxRetries) {
                            isPolling = false
                            startAuth(provider)
                        }
                        // リトライ前に追加待機
                        delay(interval)
                    }
                }
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
