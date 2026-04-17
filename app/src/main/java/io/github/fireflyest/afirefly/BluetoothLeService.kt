package io.github.fireflyest.afirefly

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class BluetoothLeService : Service() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Pair of (DeviceAddress, State)
    private val _connectionState = MutableStateFlow<Pair<String?, Int>>(null to STATE_DISCONNECTED)
    val connectionState: StateFlow<Pair<String?, Int>> = _connectionState

    private val _discoveredServices = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    val discoveredServices: StateFlow<List<BluetoothGattService>> = _discoveredServices

    private val _receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val receivedData: SharedFlow<ByteArray> = _receivedData

    private val _writeResult = MutableSharedFlow<Pair<ByteArray, Int>>(extraBufferCapacity = 64)
    val writeResult: SharedFlow<Pair<ByteArray, Int>> = _writeResult

    // Buffer to handle fragmented telemetry data
    private var rxBuffer = ByteArray(0)
    private val rxBufferLock = Any()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    fun initialize(): Boolean {
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    fun connect(address: String): Boolean {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        disconnect()
        close()

        try {
            val device = bluetoothAdapter!!.getRemoteDevice(address)
            _connectionState.value = address to STATE_CONNECTING
            @SuppressLint("MissingPermission")
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Device not found with provided address.")
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (bluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized")
            return
        }
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        // Do not clear _discoveredServices here to avoid transient UI clearing
    }

    private var writeRetryCount = 0
    private val MAX_WRITE_RETRIES = 3

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        val gatt = bluetoothGatt ?: return
        
        // Log the attempt
        Log.d(TAG, "Writing characteristic ${characteristic.uuid}: ${data.joinToString("") { "%02X".format(it) }}")

        // Set write type to NO_RESPONSE for better reliability/performance with NUS-like services
        // since we are flooding it with joystick updates
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        // Use modern API if available (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    fun getSupportedGattServices(): List<BluetoothGattService>? {
        return bluetoothGatt?.services
    }

    fun resetDiscoveredServices() {
        _discoveredServices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun enableNotifications(serviceUuid: UUID, charUuid: UUID) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(serviceUuid) ?: return
        val characteristic = service.getCharacteristic(charUuid) ?: return

        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BluetoothConstants.CCCD_UUID)
        if (descriptor != null) {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun broadcastUpdate(characteristic: BluetoothGattCharacteristic) {
        @Suppress("DEPRECATION")
        val data = characteristic.value
        if (data != null && data.isNotEmpty()) {
            gattCallback.processIncomingData(data)
        }
    }

    // Deprecated String-based sendData for internal compatibility if needed, 
    // but preferred to use ByteArray version.
    fun sendData(serviceUuid: UUID, charUuid: UUID, data: String) {
        sendData(serviceUuid, charUuid, data.toByteArray())
    }

    fun sendData(data: String) {
        sendData(data.toByteArray())
    }

    fun sendData(serviceUuid: UUID, charUuid: UUID, data: ByteArray) {
        val gatt = bluetoothGatt ?: run {
            Log.e(TAG, "sendData: bluetoothGatt is null")
            return
        }
        val service = gatt.getService(serviceUuid) ?: run {
            Log.e(TAG, "sendData: service not found $serviceUuid")
            return
        }
        val characteristic = service.getCharacteristic(charUuid) ?: run {
            Log.e(TAG, "sendData: characteristic not found $charUuid")
            return
        }
        writeCharacteristic(characteristic, data)
    }

    fun sendData(data: ByteArray) {
        val gatt = bluetoothGatt ?: run {
            Log.e(TAG, "sendData (default): bluetoothGatt is null")
            return
        }
        
        // 1. Try saved UUIDs from preferences first to ensure stability across activity changes
        val prefs = getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
        val deviceAddress = gatt.device.address
        val savedJson = prefs.getString("saved_devices", "[]")
        val devicesArr = try { org.json.JSONArray(savedJson) } catch (e: Exception) { org.json.JSONArray() }
        
        for (i in 0 until devicesArr.length()) {
            val obj = devicesArr.getJSONObject(i)
            if (obj.getString("uid") == deviceAddress) {
                val sStr = obj.optString("serviceUuid", "")
                val cStr = obj.optString("charUuid", "")
                if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                    val sUuid = UUID.fromString(sStr)
                    val cUuid = UUID.fromString(cStr)
                    val service = gatt.getService(sUuid)
                    val characteristic = service?.getCharacteristic(cUuid)
                    if (characteristic != null) {
                        writeCharacteristic(characteristic, data)
                        return
                    }
                }
                break
            }
        }

        // 2. Fallback to standard Nordic UART Service if no specific config found
        val service = gatt.getService(BluetoothConstants.SERVICE_UUID) 
        val characteristic = service?.getCharacteristic(BluetoothConstants.TX_CHARACTERISTIC_UUID)
        if (characteristic != null) {
            writeCharacteristic(characteristic, data)
        } else {
            Log.e(TAG, "sendData: No characteristic matched for sending data")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceName = gatt.device.name ?: "Unknown"
            val deviceAddress = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = deviceAddress to STATE_CONNECTED
                Log.i(TAG, "Connected to GATT server: $deviceAddress")
                Log.i(TAG, "Attempting to start service discovery: ${gatt.discoverServices()}")
                LogManager.startSession(this@BluetoothLeService, deviceName)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = deviceAddress to STATE_DISCONNECTED
                // Do not clear _discoveredServices here to avoid transient UI clearing
                Log.i(TAG, "Disconnected from GATT server: $deviceAddress")
                LogManager.stopSession()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val deviceAddress = gatt.device.address
                Log.w(TAG, "onServicesDiscovered success for: $deviceAddress")
                val services = gatt.services ?: emptyList()
                if (services.isNotEmpty()) {
                    _discoveredServices.value = services

                    // 1. Try to enable custom UUID notifications from saved preferences first
                    val prefs = getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
                    val savedJson = prefs.getString("saved_devices", "[]")
                    val devicesArr = try { org.json.JSONArray(savedJson) } catch (e: Exception) { org.json.JSONArray() }
                    var enabledCustom = false
                    
                    for (i in 0 until devicesArr.length()) {
                        val obj = devicesArr.getJSONObject(i)
                        if (obj.getString("uid") == deviceAddress) {
                            val sStr = obj.optString("serviceUuid", "")
                            val cStr = obj.optString("charUuid", "")
                            if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                                Log.i(TAG, "Auto-enabling custom notifications: $sStr / $cStr")
                                enableNotifications(UUID.fromString(sStr), UUID.fromString(cStr))
                                enabledCustom = true
                            }
                            break
                        }
                    }

                    // 2. Fallback to standard Nordic UART Service if no custom config was found/applied
                    if (!enabledCustom) {
                        val service = gatt.getService(BluetoothConstants.SERVICE_UUID)
                        val rxChar = service?.getCharacteristic(BluetoothConstants.RX_CHARACTERISTIC_UUID)
                        if (rxChar != null) {
                            Log.i(TAG, "Auto-enabling default Nordic notifications")
                            enableNotifications(BluetoothConstants.SERVICE_UUID, BluetoothConstants.RX_CHARACTERISTIC_UUID)
                        }
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered failed with status: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            broadcastUpdate(characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            processIncomingData(value)
        }

        internal fun processIncomingData(data: ByteArray) {
            synchronized(rxBufferLock) {
                // Append new data to buffer
                rxBuffer += data
                
                // Max buffer size to prevent memory leak (now handling up to 64-byte packets)
                if (rxBuffer.size > 256) {
                    // Find latest 0xAA to try and rescue the stream
                    val lastSync = rxBuffer.lastIndexOf(0xAA.toByte())
                    rxBuffer = if (lastSync != -1) {
                        rxBuffer.copyOfRange(lastSync, rxBuffer.size)
                    } else {
                        ByteArray(0)
                    }
                }

                // Process all complete packets in the buffer (now 64 bytes)
                while (rxBuffer.size >= 64) {
                    val syncIndex = rxBuffer.indexOf(0xAA.toByte())
                    
                    if (syncIndex == -1) {
                        // No sync byte found, clear buffer
                        rxBuffer = ByteArray(0)
                        break
                    }
                    
                    if (syncIndex > 0) {
                        // Discard data before sync byte
                        rxBuffer = rxBuffer.copyOfRange(syncIndex, rxBuffer.size)
                        if (rxBuffer.size < 64) break
                    }
                    
                    // Possible packet found starting with 0xAA
                    val packet = rxBuffer.copyOfRange(0, 64)
                    _receivedData.tryEmit(packet)
                    LogManager.logPacket(packet)
                    
                    // Remove processed packet from buffer
                    rxBuffer = rxBuffer.copyOfRange(64, rxBuffer.size)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(characteristic)
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val hex = characteristic?.value?.joinToString("") { "%02X".format(it) } ?: "null"
            Log.d(TAG, "onCharacteristicWrite: status=$status, data=$hex")
            characteristic?.value?.let { 
                _writeResult.tryEmit(it to status)
            }
        }
    }

    companion object {
        const val TAG = "BluetoothLeService"
        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2
    }
}
