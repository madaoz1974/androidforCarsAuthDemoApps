package com.example.carauth.auth

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.example.carauth.R
import com.example.carauth.data.model.DeviceCodeResponse
import com.example.carauth.data.model.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named

class GoogleAuthProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    @Named("googleClientId") private val clientId: String
) : AuthProvider {
    
    override val providerId = "google"
    override val displayName = "Google"
    override val displayNameResId = R.string.provider_google
    override val iconResId = R.drawable.ic_google
    override val brandColor = Color(0xFF4285F4)
    
    private val deviceCodeUrl = "https://oauth2.googleapis.com/device/code"
    private val tokenUrl = "https://oauth2.googleapis.com/token"
    private val scope = "openid email profile"
    
    override suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(deviceCodeUrl)
                        .post(FormBody.Builder()
                            .add("client_id", clientId)
                            .add("scope", scope)
                            .build())
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val deviceCode = parseDeviceCodeResponse(body)
                    Result.success(deviceCode)
                } else {
                    Result.failure(AuthException("Failed to get device code: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    private fun parseDeviceCodeResponse(json: String?): DeviceCodeResponse {
        val jsonObject = JSONObject(json ?: "{}")
        return DeviceCodeResponse(
            deviceCode = jsonObject.getString("device_code"),
            userCode = jsonObject.getString("user_code"),
            verificationUrl = jsonObject.getString("verification_url"),
            expiresIn = jsonObject.getInt("expires_in"),
            interval = jsonObject.getInt("interval")
        )
    }

    override suspend fun pollToken(deviceCode: String): Result<TokenResponse> =
         withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(tokenUrl)
                        .post(FormBody.Builder()
                            .add("client_id", clientId)
                            .add("device_code", deviceCode)
                            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                            .build())
                        .build()
                ).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val token = parseTokenResponse(body)
                    Result.success(token)
                } else {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    val error = json.optString("error")
                    if (error == "authorization_pending" || error == "slow_down") {
                        // These are expected errors during polling, but we return failure
                        // so the caller knows to retry or wait.
                         Result.failure(AuthException(error))
                    } else {
                        Result.failure(AuthException("Failed to get token: $error"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseTokenResponse(json: String?): TokenResponse {
        val jsonObject = JSONObject(json ?: "{}")
        return TokenResponse(
            accessToken = jsonObject.getString("access_token"),
            refreshToken = jsonObject.optString("refresh_token"),
            expiresIn = jsonObject.getInt("expires_in"),
            idToken = jsonObject.optString("id_token")
        )
    }
    
    override fun buildQRCodeContent(response: DeviceCodeResponse): String {
        return "carauth://auth?provider=google&url=${
            URLEncoder.encode(response.verificationUrl, "UTF-8")
        }&code=${response.userCode}"
    }
    
    override fun getPollingInterval(response: DeviceCodeResponse): Long = 
        response.interval * 1000L
}
