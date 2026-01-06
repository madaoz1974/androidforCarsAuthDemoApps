package com.example.carauth.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
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
 * Real Bluetooth GATT Server implementation for receiving authentication tokens from companion device.
 * This implementation requires actual Bluetooth hardware and will not work on emulators.
 */
@Singleton
class BluetoothAuthService @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothAuthServiceInterface {
    companion object {
        private const val TAG = "BluetoothAuthService"
        
        // Custom UUIDs for our auth service
        val AUTH_SERVICE_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val AUTH_TOKEN_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val AUTH_STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb")
    }
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    
    private val _receivedToken = MutableStateFlow<String?>(null)
    override val receivedToken: StateFlow<String?> = _receivedToken.asStateFlow()
    
    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()
    
    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()
    
    @SuppressLint("MissingPermission")
    override fun initialize(): Boolean {
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
        
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE advertising not supported")
            return false
        }
        
        return true
    }
    
    @SuppressLint("MissingPermission")
    override fun startAdvertising() {
        if (!initialize()) {
            Log.e(TAG, "Failed to initialize Bluetooth")
            return
        }
        
        // Create GATT Server
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            Log.e(TAG, "Failed to open GATT server")
            return
        }
        
        // Create auth service with characteristics
        val authService = BluetoothGattService(
            AUTH_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // Token characteristic (writable by client)
        val tokenCharacteristic = BluetoothGattCharacteristic(
            AUTH_TOKEN_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // Status characteristic (readable by client)
        val statusCharacteristic = BluetoothGattCharacteristic(
            AUTH_STATUS_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        authService.addCharacteristic(tokenCharacteristic)
        authService.addCharacteristic(statusCharacteristic)
        
        gattServer?.addService(authService)
        
        // Start advertising
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(AUTH_SERVICE_UUID))
            .build()
        
        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.d(TAG, "Started BLE advertising")
    }
    
    @SuppressLint("MissingPermission")
    override fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        _isAdvertising.value = false
        _connectionState.value = BluetoothConnectionState.Disconnected
        Log.d(TAG, "Stopped BLE advertising")
    }
    
    override fun resetToken() {
        _receivedToken.value = null
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising started successfully")
            _isAdvertising.value = true
        }
        
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error: $errorCode")
            _isAdvertising.value = false
        }
    }
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Device connected: ${device.address}")
                    _connectionState.value = BluetoothConnectionState.Connected(device.address)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Device disconnected: ${device.address}")
                    _connectionState.value = BluetoothConnectionState.Disconnected
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "Characteristic write request from ${device.address}")
            
            if (characteristic.uuid == AUTH_TOKEN_CHARACTERISTIC_UUID) {
                val token = String(value, Charsets.UTF_8)
                Log.d(TAG, "Received auth token: ${token.take(20)}...")
                _receivedToken.value = token
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == AUTH_STATUS_CHARACTERISTIC_UUID) {
                val status = if (_receivedToken.value != null) "authenticated" else "waiting"
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, status.toByteArray())
            }
        }
    }
}

sealed class BluetoothConnectionState {
    object Disconnected : BluetoothConnectionState()
    data class Connected(val deviceAddress: String) : BluetoothConnectionState()
}
