package com.example.carauth.bluetooth

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for Bluetooth authentication service.
 * Allows swapping between real BLE implementation and fake/mock for testing.
 */
interface BluetoothAuthServiceInterface {
    val isAdvertising: StateFlow<Boolean>
    val connectionState: StateFlow<BluetoothConnectionState>
    val receivedToken: StateFlow<String?>
    
    /**
     * Initialize Bluetooth adapter and verify it's available.
     * @return true if Bluetooth is available and enabled
     */
    fun initialize(): Boolean
    
    /**
     * Start BLE advertising to allow companion device to discover this device.
     */
    fun startAdvertising()
    
    /**
     * Stop BLE advertising and close GATT server.
     */
    fun stopAdvertising()
    
    /**
     * Reset the received token to null.
     */
    fun resetToken()
}
