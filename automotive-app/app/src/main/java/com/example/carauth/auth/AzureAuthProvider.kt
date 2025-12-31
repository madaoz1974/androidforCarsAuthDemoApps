package com.example.carauth.auth

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

class AzureAuthProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    @Named("azureTenantId") private val tenantId: String,
    @Named("azureClientId") private val clientId: String,
    @Named("azureUseCiam") private val useCiam: Boolean
) : AuthProvider {
    
    override val providerId = "azure"
    override val displayName = "Microsoft"
    override val displayNameResId = R.string.provider_microsoft
    override val iconResId = R.drawable.ic_microsoft
    override val brandColor = Color(0xFF00A4EF)
    
    private val baseUrl = if (useCiam) {
        "https://${tenantId.substringBefore(".")}.ciamlogin.com/$tenantId/oauth2/v2.0"
    } else {
        "https://login.microsoftonline.com/$tenantId/oauth2/v2.0"
    }
    
    private val deviceCodeUrl = "$baseUrl/devicecode"
    private val tokenUrl = "$baseUrl/token"
    private val scope = "openid profile email offline_access"
    
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
                    // Azure uses verification_uri
                    val deviceCode = parseAzureDeviceCodeResponse(body)
                    Result.success(deviceCode)
                } else {
                    Result.failure(AuthException("Failed to get device code: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    private fun parseAzureDeviceCodeResponse(json: String?): DeviceCodeResponse {
        val jsonObject = JSONObject(json ?: "{}")
        return DeviceCodeResponse(
            deviceCode = jsonObject.getString("device_code"),
            userCode = jsonObject.getString("user_code"),
            // Azure uses verification_uri
            verificationUrl = jsonObject.getString("verification_uri"),
            expiresIn = 900, // Force 15 minutes timeout
            interval = jsonObject.optInt("interval", 5) // Azure might not return interval, default to 5
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
        return "carauth://auth?provider=azure&url=${
            URLEncoder.encode(response.verificationUrl, "UTF-8")
        }&code=${response.userCode}"
    }
    
    override fun getPollingInterval(response: DeviceCodeResponse): Long = 
        response.interval * 1000L
}
