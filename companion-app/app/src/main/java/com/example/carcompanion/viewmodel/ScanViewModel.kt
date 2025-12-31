package com.example.carcompanion.viewmodel

import androidx.lifecycle.ViewModel
import com.example.carcompanion.auth.AuthUrlParser
import com.example.carcompanion.auth.ProviderInfo
import com.example.carcompanion.auth.ProviderType
import com.example.carcompanion.scanner.ScanResult
import com.example.carcompanion.util.VibrationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val vibrationUtils: VibrationUtils
) : ViewModel() {
    
    private val _scanResult = MutableStateFlow<ScanResult>(ScanResult.Idle)
    val scanResult: StateFlow<ScanResult> = _scanResult.asStateFlow()
    
    private var lastScannedValue: String? = null
    
    fun onQRCodeScanned(rawValue: String) {
        // Prevent continuous scanning of the same QR code
        if (rawValue == lastScannedValue) return
        lastScannedValue = rawValue
        
        vibrationUtils.vibrate(50)
        
        when (val parseResult = AuthUrlParser.parse(rawValue)) {
            is AuthUrlParser.ParseResult.Success -> {
                val providerInfo = ProviderInfo.fromParseResult(parseResult)
                if (providerInfo.type == ProviderType.UNKNOWN) {
                    _scanResult.value = ScanResult.UnknownProvider(
                        provider = parseResult.provider,
                        url = parseResult.url,
                        code = parseResult.code
                    )
                } else {
                    _scanResult.value = ScanResult.Success(providerInfo)
                }
            }
            is AuthUrlParser.ParseResult.InvalidScheme -> {
                _scanResult.value = ScanResult.InvalidQRCode(rawValue)
            }
            is AuthUrlParser.ParseResult.MissingParameters -> {
                _scanResult.value = ScanResult.Error("QRコードに必要な情報が含まれていません")
            }
            is AuthUrlParser.ParseResult.InvalidFormat -> {
                _scanResult.value = ScanResult.InvalidQRCode(rawValue)
            }
        }
    }
    
    fun resetScan() {
        lastScannedValue = null
        _scanResult.value = ScanResult.Scanning
    }
    
    fun startScanning() {
        _scanResult.value = ScanResult.Scanning
    }
}
