package io.github.fireflyest.afirefly

import android.content.Intent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice

const val EXTRA_DEVICE_NAME = "extra_device_name"
const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
const val EXTRA_DEVICE_RSSI = "extra_device_rssi"
const val EXTRA_DEVICE_MAJOR_CLASS = "extra_device_major_class"
const val EXTRA_DEVICE_TYPE = "extra_device_type"
const val EXTRA_DEVICE_SERVICE_UUIDS = "extra_device_service_uuids"


data class ConnectedDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int,
    val majorClass: Int = BluetoothClass.Device.Major.UNCATEGORIZED,
    val deviceType: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN,
    val serviceUuids: List<String> = emptyList(),
) {
    fun categoryLabel(): String = when (majorClass) {
        BluetoothClass.Device.Major.PHONE -> "PHONE"
        BluetoothClass.Device.Major.COMPUTER -> "COMPUTER"
        BluetoothClass.Device.Major.NETWORKING -> "NETWORKING"
        BluetoothClass.Device.Major.WEARABLE -> "WEARABLE"
        BluetoothClass.Device.Major.HEALTH -> "HEALTH"
        BluetoothClass.Device.Major.TOY -> "TOY"
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "AUDIO"
        BluetoothClass.Device.Major.PERIPHERAL -> "PERIPHERAL"
        0x1F00 -> "WEARABLE"
        else -> when (deviceType) {
            BluetoothDevice.DEVICE_TYPE_LE -> "LE"
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            else -> "UNKNOWN"
        }
    }
}

fun Intent.putConnectedDevice(info: ConnectedDeviceInfo): Intent = apply {
    putExtra(EXTRA_DEVICE_NAME, info.name)
    putExtra(EXTRA_DEVICE_ADDRESS, info.address)
    putExtra(EXTRA_DEVICE_RSSI, info.rssi)
    putExtra(EXTRA_DEVICE_MAJOR_CLASS, info.majorClass)
    putExtra(EXTRA_DEVICE_TYPE, info.deviceType)
    putStringArrayListExtra(EXTRA_DEVICE_SERVICE_UUIDS, ArrayList(info.serviceUuids))
}

fun Intent.toConnectedDeviceInfo(): ConnectedDeviceInfo? {
    val name = getStringExtra(EXTRA_DEVICE_NAME) ?: return null
    val address = getStringExtra(EXTRA_DEVICE_ADDRESS) ?: return null
    val rssi = getIntExtra(EXTRA_DEVICE_RSSI, Int.MIN_VALUE)
    if (rssi == Int.MIN_VALUE) return null
    val majorClass = getIntExtra(EXTRA_DEVICE_MAJOR_CLASS, BluetoothClass.Device.Major.UNCATEGORIZED)
    val deviceType = getIntExtra(EXTRA_DEVICE_TYPE, BluetoothDevice.DEVICE_TYPE_UNKNOWN)
    val serviceUuids = getStringArrayListExtra(EXTRA_DEVICE_SERVICE_UUIDS)?.toList().orEmpty()
    return ConnectedDeviceInfo(name, address, rssi, majorClass, deviceType, serviceUuids)
}

fun ConnectedDeviceInfo.toMainDevice(): Device = Device(
    name = name,
    subtitle = categoryLabel(),
    uid = address,
    status = "LINKING",
    signal = "${rssi} dBm",
    battery = "--",
    locked = majorClass == BluetoothClass.Device.Major.PHONE,
    gps = majorClass == BluetoothClass.Device.Major.NETWORKING
)


