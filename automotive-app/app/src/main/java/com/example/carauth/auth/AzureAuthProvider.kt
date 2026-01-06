package com.example.carauth.auth

import androidx.compose.ui.graphics.Color
import com.example.carauth.BuildConfig
import com.example.carauth.R
import com.example.carauth.data.model.DeviceCodeResponse
import com.example.carauth.data.model.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
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
    
    companion object {
        private const val TAG = "AzureAuthProvider"
    }
    
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
                    val deviceCode = parseAzureDeviceCodeResponse(body)
                    Result.success(deviceCode)
                } else {
                    Result.failure(AuthException("Failed to get device code: ${response.code}"))
                }
            } catch (e: Exception) {
                log("requestDeviceCode exception: ${e.message}")
                Result.failure(e)
            }
        }
    
    private fun parseAzureDeviceCodeResponse(json: String?): DeviceCodeResponse {
        if (json.isNullOrBlank()) {
            throw AuthException("Empty response from server")
        }
        
        return try {
            val jsonObject = JSONObject(json)
            DeviceCodeResponse(
                deviceCode = jsonObject.optString("device_code").takeIf { it.isNotEmpty() }
                    ?: throw AuthException("Missing device_code"),
                userCode = jsonObject.optString("user_code").takeIf { it.isNotEmpty() }
                    ?: throw AuthException("Missing user_code"),
                verificationUrl = jsonObject.optString("verification_uri").takeIf { it.isNotEmpty() }
                    ?: throw AuthException("Missing verification_uri"),
                verificationUrlComplete = jsonObject.optString("verification_uri_complete")
                    .takeIf { it.isNotEmpty() },
                expiresIn = jsonObject.optInt("expires_in", 1800),
                interval = jsonObject.optInt("interval", 5)
            )
        } catch (e: JSONException) {
            throw AuthException("Invalid JSON response: ${e.message}")
        }
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
                    log("pollToken SUCCESS: token received (length=${body?.length ?: 0})")
                    val token = parseTokenResponse(body)
                    Result.success(token)
                } else {
                    val body = response.body?.string()
                    log("pollToken response: ${body?.take(100)}...")
                    val json = JSONObject(body ?: "{}")
                    val error = json.optString("error", "unknown_error")
                    Result.failure(AuthException(error))
                }
            } catch (e: Exception) {
                log("pollToken exception: ${e.message}")
                Result.failure(e)
            }
        }

    private fun parseTokenResponse(json: String?): TokenResponse {
        if (json.isNullOrBlank()) {
            throw AuthException("Empty token response")
        }
        
        return try {
            val jsonObject = JSONObject(json)
            TokenResponse(
                accessToken = jsonObject.optString("access_token").takeIf { it.isNotEmpty() }
                    ?: throw AuthException("Missing access_token"),
                refreshToken = jsonObject.optString("refresh_token").takeIf { it.isNotEmpty() },
                expiresIn = jsonObject.optInt("expires_in", 3600),
                idToken = jsonObject.optString("id_token").takeIf { it.isNotEmpty() }
            )
        } catch (e: JSONException) {
            throw AuthException("Invalid token response: ${e.message}")
        }
    }
    
    override fun buildQRCodeContent(response: DeviceCodeResponse): String {
        val url = response.verificationUrlComplete ?: response.verificationUrl
        return "carauth://auth?provider=azure&url=${
            URLEncoder.encode(url, "UTF-8")
        }&code=${response.userCode}"
    }
    
    override fun getPollingInterval(response: DeviceCodeResponse): Long = 
        response.interval * 1000L
    
    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, message)
        }
    }
}

