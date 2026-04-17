package io.github.fireflyest.afirefly

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Manages the log of 64-byte telemetry packets.
 */
object LogManager {
    private val executor = Executors.newSingleThreadExecutor()
    private var currentFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val logDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Start a new log session, creating a new file.
     */
    fun startSession(context: Context, deviceName: String?) {
        executor.execute {
            try {
                closeSessionInternal()
                // Save to public Downloads/afirefly_logs folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val logDir = File(downloadsDir, "afirefly_logs")
                if (!logDir.exists()) logDir.mkdirs()

                val fileName = "telemetry_${deviceName ?: "unknown"}_${dateFormat.format(Date())}.txt"
                currentFile = File(logDir, fileName)
                fileOutputStream = FileOutputStream(currentFile, true)

                val header = "--- Session Started: ${Date()} ---\n"
                fileOutputStream?.write(header.toByteArray())
                fileOutputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Stop the current log session and close the file.
     */
    fun stopSession() {
        executor.execute {
            closeSessionInternal()
        }
    }

    private fun closeSessionInternal() {
        try {
            fileOutputStream?.write("\n--- Session Stopped: ${Date()} ---\n".toByteArray())
            fileOutputStream?.flush()
            fileOutputStream?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            fileOutputStream = null
            currentFile = null
        }
    }

    /**
     * Log a 64-byte telemetry packet in Hex format with timestamp.
     */
    fun logPacket(packet: ByteArray) {
        if (packet.size != 64) return

        executor.execute {
            try {
                fileOutputStream?.let { fos ->
                    val timestamp = logDateFormat.format(Date())
                    val hexString = packet.joinToString("") { "%02X".format(it) }
                    val entry = "[$timestamp] $hexString\n"
                    fos.write(entry.toByteArray())
                    fos.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
