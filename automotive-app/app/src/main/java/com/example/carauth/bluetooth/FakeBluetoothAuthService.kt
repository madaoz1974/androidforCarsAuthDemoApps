package com.example.carauth.bluetooth

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake Bluetooth auth service for testing and emulator use.
 * Simulates BLE advertising and token reception without requiring actual Bluetooth hardware.
 */
@Singleton
class FakeBluetoothAuthService @Inject constructor() : BluetoothAuthServiceInterface {
    
    companion object {
        private const val TAG = "FakeBluetoothAuth"
    }
    
    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()
    
    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()
    
    private val _receivedToken = MutableStateFlow<String?>(null)
    override val receivedToken: StateFlow<String?> = _receivedToken.asStateFlow()
    
    override fun initialize(): Boolean {
        Log.d(TAG, "Fake Bluetooth initialized (simulated)")
        return true
    }
    
    override fun startAdvertising() {
        _isAdvertising.value = true
        Log.d(TAG, "Fake advertising started (simulated)")
    }
    
    override fun stopAdvertising() {
        _isAdvertising.value = false
        _connectionState.value = BluetoothConnectionState.Disconnected
        Log.d(TAG, "Fake advertising stopped (simulated)")
    }
    
    override fun resetToken() {
        _receivedToken.value = null
    }
    
    // ===== Debug/Test methods =====
    
    /**
     * Simulate receiving a token from a paired device.
     * Use this for testing authentication flow on emulator.
     */
    fun simulateTokenReceived(token: String) {
        Log.d(TAG, "Simulating token received: ${token.take(20)}...")
        _receivedToken.value = token
    }
    
    /**
     * Simulate a device connection.
     */
    fun simulateConnection(deviceAddress: String = "00:11:22:33:44:55") {
        _connectionState.value = BluetoothConnectionState.Connected(deviceAddress)
        Log.d(TAG, "Simulating device connected: $deviceAddress")
    }
    
    /**
     * Simulate a device disconnection.
     */
    fun simulateDisconnection() {
        _connectionState.value = BluetoothConnectionState.Disconnected
        Log.d(TAG, "Simulating device disconnected")
    }
}
