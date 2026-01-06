package com.example.carcompanion.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carcompanion.auth.CustomTabsHelper
import com.example.carcompanion.bluetooth.BluetoothTokenSender
import com.example.carcompanion.bluetooth.ConnectionState
import com.example.carcompanion.bluetooth.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    val customTabsHelper: CustomTabsHelper,
    private val bluetoothTokenSender: BluetoothTokenSender
) : ViewModel() {
    
    // Expose Bluetooth states for UI
    val scanState: StateFlow<ScanState> = bluetoothTokenSender.scanState
    val connectionState: StateFlow<ConnectionState> = bluetoothTokenSender.connectionState
    
    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()
    
    private var foundDevice: BluetoothDevice? = null
    
    init {
        // Monitor scan state to auto-connect when device found
        viewModelScope.launch {
            bluetoothTokenSender.scanState.collect { state ->
                if (state is ScanState.DeviceFound) {
                    foundDevice = state.device
                    bluetoothTokenSender.stopScan()
                    android.util.Log.d("AuthViewModel", "Found automotive device, stopped scanning")
                    
                    // If we already have a token, send it immediately
                    _authToken.value?.let { token ->
                        sendTokenToDevice(token)
                    }
                }
            }
        }
        
        // Monitor connection state
        viewModelScope.launch {
            bluetoothTokenSender.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.TokenSent -> {
                        android.util.Log.d("AuthViewModel", "Token sent successfully to automotive device!")
                    }
                    is ConnectionState.Error -> {
                        android.util.Log.e("AuthViewModel", "Bluetooth error: ${state.message}")
                    }
                    else -> {}
                }
            }
        }
    }
    
    /**
     * Start scanning for automotive device
     * Should be called when QR code is scanned
     */
    fun startBluetoothScan() {
        bluetoothTokenSender.startScan()
    }
    
    fun stopBluetoothScan() {
        bluetoothTokenSender.stopScan()
    }
    
    /**
     * Called when OAuth authentication completes
     */
    fun onAuthenticationComplete(token: String) {
        android.util.Log.d("AuthViewModel", "OAuth authentication complete, token received")
        _authToken.value = token
        
        // If device already found, send token
        foundDevice?.let { device ->
            sendTokenToDevice(token)
        }
    }
    
    private fun sendTokenToDevice(token: String) {
        foundDevice?.let { device ->
            android.util.Log.d("AuthViewModel", "Sending token to ${device.address}")
            bluetoothTokenSender.connectAndSendToken(device, token)
        }
    }
    
    fun disconnect() {
        bluetoothTokenSender.disconnect()
    }
    
    override fun onCleared() {
        super.onCleared()
        bluetoothTokenSender.stopScan()
        bluetoothTokenSender.disconnect()
    }
}
