package io.github.fireflyest.afirefly

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Command protocols for talking to the flight controller.
 * Ported from the hardware C definitions provided.
 */
object FlightCommands {
    /* Command Types (0x10 ~ 0x1F) */
    const val CMD_TYPE_CONTROL_MODE      = 0x10.toByte()   /* 切换控制层级      */
    const val CMD_TYPE_CONTROL_THROTTLE  = 0x11.toByte()   /* 直接油门         */
    const val CMD_TYPE_CONTROL_HEIGHT    = 0x12.toByte()   /* 设目标高度        */
    const val CMD_TYPE_CONTROL_MOVE      = 0x13.toByte()   /* 平移指令         */
    const val CMD_TYPE_CONTROL_ATTITUDE  = 0x14.toByte()   /* 设目标姿态        */
    const val CMD_TYPE_CONTROL_ARM       = 0x15.toByte()   /* 解锁             */
    const val CMD_TYPE_CONTROL_DISARM    = 0x16.toByte()   /* 锁定             */
    const val CMD_TYPE_CONTROL_ESTOP     = 0x17.toByte()   /* 紧急停止         */
    const val CMD_TYPE_CONTROL_TAKEOFF   = 0x18.toByte()   /* 起飞（带高度参数）  */
    const val CMD_TYPE_CONTROL_LAND      = 0x19.toByte()   /* 降落             */
    const val CMD_TYPE_CONTROL_HOVER     = 0x1A.toByte()   /* 悬停             */

    /* Control Modes */
    const val CONTROL_MODE_DIRECT     = 0.toByte()
    const val CONTROL_MODE_STABILIZED = 1.toByte()
    const val CONTROL_MODE_ALTITUDE   = 2.toByte()
    const val CONTROL_MODE_VELOCITY   = 3.toByte()
    const val CONTROL_MODE_POSITION   = 4.toByte()

    /**
     * Set control mode.
     */
    fun setMode(mode: Byte): ByteArray {
        return byteArrayOf(CMD_TYPE_CONTROL_MODE, mode)
    }

    /**
     * Set direct throttle (0.0 to 1.0).
     */
    fun setThrottle(throttle: Float): ByteArray {
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(CMD_TYPE_CONTROL_THROTTLE)
        buffer.putFloat(throttle)
        return buffer.array()
    }

    /**
     * Set target height.
     */
    fun setHeight(height: Float): ByteArray {
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(CMD_TYPE_CONTROL_HEIGHT)
        buffer.putFloat(height)
        return buffer.array()
    }

    /**
     * Move command (forward, right).
     */
    fun move(forward: Float, right: Float): ByteArray {
        val buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(CMD_TYPE_CONTROL_MOVE)
        buffer.putFloat(forward)
        buffer.putFloat(right)
        return buffer.array()
    }

    /**
     * Set target attitude (roll, pitch, yaw).
     */
    fun setAttitude(roll: Float, pitch: Float, yaw: Float): ByteArray {
        val buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(CMD_TYPE_CONTROL_ATTITUDE)
        buffer.putFloat(roll)
        buffer.putFloat(pitch)
        buffer.putFloat(yaw)
        return buffer.array()
    }

    /**
     * Arm the drone.
     */
    fun arm(): ByteArray = byteArrayOf(CMD_TYPE_CONTROL_ARM)

    /**
     * Disarm the drone.
     */
    fun disarm(): ByteArray = byteArrayOf(CMD_TYPE_CONTROL_DISARM)

    /**
     * Emergency stop.
     */
    fun emergencyStop(): ByteArray = byteArrayOf(CMD_TYPE_CONTROL_ESTOP)

    /**
     * Takeoff with relative height.
     */
    fun takeoff(relativeHeight: Float): ByteArray {
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(CMD_TYPE_CONTROL_TAKEOFF)
        buffer.putFloat(relativeHeight)
        return buffer.array()
    }

    /**
     * Land.
     */
    fun land(): ByteArray = byteArrayOf(CMD_TYPE_CONTROL_LAND)

    /**
     * Hover.
     */
    fun hover(): ByteArray = byteArrayOf(CMD_TYPE_CONTROL_HOVER)

    /**
     * Telemetry data parsed from the drone.
     */
    data class Telemetry(
        val flightPhase: Int = 0,
        val mode: Int = 0,
        val armState: Int = 0,
        val quaternion: FloatArray = floatArrayOf(1f, 0f, 0f, 0f),
        val altitude: Float = 0f,
        val velocity: Float = 0f,
        val battery: Int = 0,
        val satellites: Int = 0,
        val rssi: Int = 0,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val rollPID: FloatArray = floatArrayOf(0f, 0f, 0f), // P, I, D
        val pitchPID: FloatArray = floatArrayOf(0f, 0f, 0f),
        val yawPID: FloatArray = floatArrayOf(0f, 0f, 0f)
    )

    /**
     * Parse 64-byte telemetry packet.
     * Protocol Definition (Total 64 bytes):
     * [0] Header (0xAA)
     * [1] Flight Phase (uint8)
     * [2] Mode (uint8)
     * [3] Arm State (uint8)
     * [4-19] Quaternion (4 x float32)
     * [20-23] Altitude (float32)
     * [24-27] Velocity (float32)
     * [28] Battery % (uint8)
     * [29] Satellites (uint8)
     * [30-31] RSSI (int16)
     * [32-39] Latitude (float64)
     * [40-47] Longitude (float64)
     * [48-51] Roll P (float32)
     * [52-55] Pitch P (float32)
     * [56-59] Yaw P (float32)
     * [60-63] Checksum (Addition sum of 0..62)
     */
    fun parseTelemetry64(data: ByteArray): Telemetry? {
        if (data.size != 64 || data[0] != 0xAA.toByte()) return null

        // Checksum validation
        var sum = 0
        for (i in 0 until 63) {
            sum += data[i].toInt() and 0xFF
        }
        if ((sum and 0xFF).toByte() != data[63]) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // Skip Header [0]

        val phase = buffer.get().toInt() and 0xFF // [1]
        val mode = buffer.get().toInt() and 0xFF // [2]
        val arm = buffer.get().toInt() and 0xFF  // [3]

        val quat = floatArrayOf(
            buffer.float, // [4-7]
            buffer.float, // [8-11]
            buffer.float, // [12-15]
            buffer.float  // [16-19]
        )
        val alt = buffer.float // [20-23]
        val spd = buffer.float // [24-27]
        val bat = buffer.get().toInt() and 0xFF // [28]
        val sats = buffer.get().toInt() and 0xFF // [29]
        val rssi = buffer.short.toInt() // [30-31]

        val lat = buffer.double // [32-39]
        val lon = buffer.double // [40-47]

        // Only sending P values for now to fit in 64 bytes if we include more,
        // but here we define some PID slots
        val rP = buffer.float // [48-51]
        val pP = buffer.float // [52-55]
        val yP = buffer.float // [56-59]

        return Telemetry(
            flightPhase = phase,
            mode = mode,
            armState = arm,
            quaternion = quat,
            altitude = alt,
            velocity = spd,
            battery = bat,
            satellites = sats,
            rssi = rssi,
            latitude = lat,
            longitude = lon,
            rollPID = floatArrayOf(rP, 0f, 0f),
            pitchPID = floatArrayOf(pP, 0f, 0f),
            yawPID = floatArrayOf(yP, 0f, 0f)
        )
    }

    /**
     * Legacy 32-byte parser (optional/fallback)
     */
    fun parseTelemetry(data: ByteArray): Telemetry? {
        // Strict length requirement for reassembled packets
        if (data.size != 32 || data[0] != 0xAA.toByte()) return null
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // Skip Header
        
        val phase = buffer.get().toInt()
        val quat = floatArrayOf(
            buffer.float, // W
            buffer.float, // X
            buffer.float, // Y
            buffer.float  // Z
        )
        val alt = buffer.float
        val spd = buffer.float
        val bat = buffer.get().toInt() and 0xFF
        val sats = buffer.get().toInt() and 0xFF
        val rssi = buffer.short.toInt()
        
        return Telemetry(
            flightPhase = phase,
            quaternion = quat,
            altitude = alt,
            velocity = spd,
            battery = bat,
            satellites = sats,
            rssi = rssi
        )
    }
}
