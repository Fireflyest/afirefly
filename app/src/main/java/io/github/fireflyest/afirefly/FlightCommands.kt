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
        val quaternion: FloatArray = floatArrayOf(1f, 0f, 0f, 0f),
        val altitude: Float = 0f,
        val velocity: Float = 0f,
        val battery: Int = 0,
        val satellites: Int = 0,
        val rssi: Int = 0
    )

    /**
     * Parse 32-byte telemetry packet.
     */
    fun parseTelemetry(data: ByteArray): Telemetry? {
        if (data.size < 30 || data[0] != 0xAA.toByte()) return null
        
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
        
        return Telemetry(phase, quat, alt, spd, bat, sats, rssi)
    }
}
