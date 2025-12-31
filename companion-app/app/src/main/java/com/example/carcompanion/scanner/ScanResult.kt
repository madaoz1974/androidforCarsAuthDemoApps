package com.example.carcompanion.scanner

import com.example.carcompanion.auth.ProviderInfo

sealed class ScanResult {
    object Idle : ScanResult()
    object Scanning : ScanResult()
    data class Success(val providerInfo: ProviderInfo) : ScanResult()
    data class UnknownProvider(val provider: String, val url: String, val code: String) : ScanResult()
    data class InvalidQRCode(val rawValue: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}
