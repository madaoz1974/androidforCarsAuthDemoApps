package com.example.carcompanion.auth

import android.net.Uri
import java.net.URLDecoder

object AuthUrlParser {
    private const val SCHEME = "carauth"
    private const val HOST = "auth"
    
    fun parse(qrContent: String): ParseResult {
        return try {
            val uri = Uri.parse(qrContent)
            
            // Validate scheme and host
            if (uri.scheme != SCHEME || uri.host != HOST) {
                return ParseResult.InvalidScheme(qrContent)
            }
            
            val provider = uri.getQueryParameter("provider")
            val url = uri.getQueryParameter("url")
            val code = uri.getQueryParameter("code")
            
            when {
                url == null || code == null -> ParseResult.MissingParameters
                provider == null -> {
                    // Backwards compatibility: if provider is missing, assume Google
                    ParseResult.Success(
                        provider = "google",
                        url = URLDecoder.decode(url, "UTF-8"),
                        code = code
                    )
                }
                else -> ParseResult.Success(
                    provider = provider,
                    url = URLDecoder.decode(url, "UTF-8"),
                    code = code
                )
            }
        } catch (e: Exception) {
            ParseResult.InvalidFormat(qrContent, e.message)
        }
    }
    
    sealed class ParseResult {
        data class Success(
            val provider: String,
            val url: String,
            val code: String
        ) : ParseResult()
        
        data class InvalidScheme(val rawValue: String) : ParseResult()
        object MissingParameters : ParseResult()
        data class InvalidFormat(val rawValue: String, val error: String?) : ParseResult()
    }
}
