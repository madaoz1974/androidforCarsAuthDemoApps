package com.example.carcompanion.auth

import androidx.compose.ui.graphics.Color
import com.example.carcompanion.R

enum class ProviderType(val id: String) {
    GOOGLE("google"),
    AZURE("azure"),
    UNKNOWN("unknown");
    
    companion object {
        fun fromId(id: String): ProviderType = 
            values().find { it.id == id } ?: UNKNOWN
    }
}

data class ProviderInfo(
    val type: ProviderType,
    val displayName: String,
    val iconResId: Int,
    val brandColor: Color,
    val verificationUrl: String,
    val userCode: String
) {
    companion object {
        fun fromParseResult(result: AuthUrlParser.ParseResult.Success): ProviderInfo {
            val type = ProviderType.fromId(result.provider)
            return when (type) {
                ProviderType.GOOGLE -> ProviderInfo(
                    type = type,
                    displayName = "Google",
                    iconResId = R.drawable.ic_google,
                    brandColor = Color(0xFF4285F4),
                    verificationUrl = result.url,
                    userCode = result.code
                )
                ProviderType.AZURE -> ProviderInfo(
                    type = type,
                    displayName = "Microsoft",
                    iconResId = R.drawable.ic_microsoft,
                    brandColor = Color(0xFF00A4EF),
                    verificationUrl = result.url,
                    userCode = result.code
                )
                ProviderType.UNKNOWN -> ProviderInfo(
                    type = type,
                    displayName = "Unknown",
                    iconResId = R.drawable.ic_question,
                    brandColor = Color.Gray,
                    verificationUrl = result.url,
                    userCode = result.code
                )
            }
        }
    }
}
