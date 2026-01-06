package com.example.carcompanion.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth GATT Client for sending authentication tokens to automotive device
 */
@Singleton
class BluetoothTokenSender @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BluetoothTokenSender"
        
        // Must match UUIDs in automotive app's BluetoothAuthService
        val AUTH_SERVICE_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val AUTH_TOKEN_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    }
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var pendingToken: String? = null
    
    @SuppressLint("MissingPermission")
    fun initialize(): Boolean {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device")
            return false
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }
        
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return false
        }
        
        return true
    }
    
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!initialize()) {
            _scanState.value = ScanState.Error("Bluetooth not available")
            return
        }
        
        _scanState.value = ScanState.Scanning
        
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(AUTH_SERVICE_UUID))
            .build()
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanner?.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "Started scanning for automotive device")
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        _scanState.value = ScanState.Idle
        Log.d(TAG, "Stopped scanning")
    }
    
    @SuppressLint("MissingPermission")
    fun connectAndSendToken(device: BluetoothDevice, token: String) {
        pendingToken = token
        _connectionState.value = ConnectionState.Connecting
        gatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Connecting to ${device.address}")
    }
    
    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.close()
        gatt = null
        pendingToken = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "Found device: ${result.device.address}")
            _scanState.value = ScanState.DeviceFound(result.device)
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            _scanState.value = ScanState.Error("Scan failed: $errorCode")
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.Connected
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                
                val service = gatt.getService(AUTH_SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(AUTH_TOKEN_CHARACTERISTIC_UUID)
                    if (characteristic != null && pendingToken != null) {
                        characteristic.value = pendingToken!!.toByteArray(Charsets.UTF_8)
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        val success = gatt.writeCharacteristic(characteristic)
                        Log.d(TAG, "Writing token: $success")
                    }
                } else {
                    Log.e(TAG, "Auth service not found")
                    _connectionState.value = ConnectionState.Error("Auth service not found")
                }
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Token sent successfully!")
                _connectionState.value = ConnectionState.TokenSent
            } else {
                Log.e(TAG, "Failed to send token: $status")
                _connectionState.value = ConnectionState.Error("Failed to send token")
            }
        }
    }
}

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class DeviceFound(val device: BluetoothDevice) : ScanState()
    data class Error(val message: String) : ScanState()
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object TokenSent : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
