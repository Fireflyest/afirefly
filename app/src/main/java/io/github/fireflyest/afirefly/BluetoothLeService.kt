package io.github.fireflyest.afirefly

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

    private val _connectionState = MutableStateFlow(STATE_DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState

    private val _receivedData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val receivedData: SharedFlow<ByteArray> = _receivedData

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

        try {
            val device = bluetoothAdapter!!.getRemoteDevice(address)
            _connectionState.value = STATE_CONNECTING
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Device not found with provided address.")
            return false
        }
        return true
    }

    fun disconnect() {
        if (bluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized")
            return
        }
        bluetoothGatt?.disconnect()
    }

    private fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (bluetoothGatt == null) return
        @Suppress("DEPRECATION")
        characteristic.value = data
        @Suppress("DEPRECATION")
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    fun getSupportedGattServices(): List<BluetoothGattService>? {
        return bluetoothGatt?.services
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = STATE_CONNECTED
                Log.i(TAG, "Connected to GATT server.")
                Log.i(TAG, "Attempting to start service discovery: ${bluetoothGatt?.discoverServices()}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = STATE_DISCONNECTED
                Log.i(TAG, "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: $status")
                enableNotifications()
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            broadcastUpdate(characteristic)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(characteristic)
            }
        }
    }

    private fun enableNotifications() {
        val service = bluetoothGatt?.getService(BluetoothConstants.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(BluetoothConstants.RX_CHARACTERISTIC_UUID)
        if (characteristic != null) {
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(BluetoothConstants.CCCD_UUID)
            if (descriptor != null) {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeDescriptor(descriptor)
            }
        }
    }

    private fun broadcastUpdate(characteristic: BluetoothGattCharacteristic) {
        if (BluetoothConstants.RX_CHARACTERISTIC_UUID == characteristic.uuid) {
            @Suppress("DEPRECATION")
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                _receivedData.tryEmit(data)
            }
        }
    }

    fun sendData(data: String) {
        val service = bluetoothGatt?.getService(BluetoothConstants.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(BluetoothConstants.TX_CHARACTERISTIC_UUID)
        if (characteristic != null) {
            writeCharacteristic(characteristic, data.toByteArray())
        }
    }

    companion object {
        const val TAG = "BluetoothLeService"
        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2
    }
}
