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

    /* Telemetry Packet Types */
    const val PKT_TYPE_STATUS   = 0x01.toByte()    /* 状态包：锁定、模式、电池等 */
    const val PKT_TYPE_ATTITUDE = 0x02.toByte()    /* 姿态包：四元数、PID等      */
    const val PKT_TYPE_GPS      = 0x03.toByte()    /* 定位包：经纬度、高度、速度 */

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
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Telemetry

            if (flightPhase != other.flightPhase) return false
            if (mode != other.mode) return false
            if (armState != other.armState) return false
            if (!quaternion.contentEquals(other.quaternion)) return false
            if (altitude != other.altitude) return false
            if (velocity != other.velocity) return false
            if (battery != other.battery) return false
            if (satellites != other.satellites) return false
            if (rssi != other.rssi) return false
            if (latitude != other.latitude) return false
            if (longitude != other.longitude) return false
            if (!rollPID.contentEquals(other.rollPID)) return false
            if (!pitchPID.contentEquals(other.pitchPID)) return false
            if (!yawPID.contentEquals(other.yawPID)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = flightPhase
            result = 31 * result + mode
            result = 31 * result + armState
            result = 31 * result + quaternion.contentHashCode()
            result = 31 * result + altitude.hashCode()
            result = 31 * result + velocity.hashCode()
            result = 31 * result + battery
            result = 31 * result + satellites
            result = 31 * result + rssi
            result = 31 * result + latitude.hashCode()
            result = 31 * result + longitude.hashCode()
            result = 31 * result + rollPID.contentHashCode()
            result = 31 * result + pitchPID.contentHashCode()
            result = 31 * result + yawPID.contentHashCode()
            return result
        }
    }

    /**
     * Parse telemetry packet with multiple type support.
     * [0] Header (0xAA)
     * [1] Packet Type (PKT_TYPE_XXX)
     * Payload starts at [2]
     */
    fun parseTelemetryDelta(data: ByteArray, current: Telemetry): Telemetry? {
        if (data.size < 2 || data[0] != 0xAA.toByte()) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // Skip Header [0]
        val type = buffer.get() // Packet Type [1]

        return when (type) {
            PKT_TYPE_STATUS -> {
                if (data.size < 9) return null
                val phase = buffer.get().toInt() and 0xFF
                val mode = buffer.get().toInt() and 0xFF
                val arm = buffer.get().toInt() and 0xFF
                val bat = buffer.get().toInt() and 0xFF
                val sats = buffer.get().toInt() and 0xFF
                val rssi = buffer.short.toInt()
                current.copy(
                    flightPhase = phase,
                    mode = mode,
                    armState = arm,
                    battery = bat,
                    satellites = sats,
                    rssi = rssi
                )
            }
            PKT_TYPE_ATTITUDE -> {
                if (data.size < 30) return null
                // Quaternion: w, x, y, z as requested
                val quat = floatArrayOf(
                    buffer.float, // w
                    buffer.float, // x
                    buffer.float, // y
                    buffer.float  // z
                )
                // PID Outputs (not P,I,D constants)
                val rateRollOutput = buffer.float
                val ratePitchOutput = buffer.float
                val rateYawOutput = buffer.float
                
                current.copy(
                    quaternion = quat,
                    rollPID = floatArrayOf(rateRollOutput, 0f, 0f), // Use first element for output
                    pitchPID = floatArrayOf(ratePitchOutput, 0f, 0f),
                    yawPID = floatArrayOf(rateYawOutput, 0f, 0f)
                )
            }
            PKT_TYPE_GPS -> {
                if (data.size < 26) return null
                val lat = buffer.double
                val lon = buffer.double
                val alt = buffer.float
                val spd = buffer.float
                current.copy(
                    latitude = lat,
                    longitude = lon,
                    altitude = alt,
                    velocity = spd
                )
            }
            else -> null
        }
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
