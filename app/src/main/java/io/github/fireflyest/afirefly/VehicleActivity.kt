package io.github.fireflyest.afirefly

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import io.github.fireflyest.afirefly.ui.theme.AfireflyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import java.util.*
import kotlin.math.*

class VehicleActivity : ComponentActivity() {
    private val _bluetoothService = mutableStateOf<BluetoothLeService?>(null)
    private var deviceAddress: String? = null

    companion object {
        var isVehicleActivityActive = false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothLeService.LocalBinder
            val bService = binder.getService()
            bService.initialize() // Ensure initialized
            _bluetoothService.value = bService
            
            // Connect to device if address is available and not already connected
            deviceAddress?.let { address ->
                val currentState = bService.connectionState.value.second
                if (currentState == BluetoothLeService.STATE_DISCONNECTED) {
                    bService.connect(address)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _bluetoothService.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceName = intent.getStringExtra("device_name")
        deviceAddress = intent.getStringExtra("device_address")

        // Force landscape and full screen
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Start bind immediately
        val intentService = Intent(this, BluetoothLeService::class.java)
        bindService(intentService, connection, BIND_AUTO_CREATE)

        setContent {
            AfireflyTheme {
                val service by _bluetoothService
                VehicleScreen(service, deviceName, deviceAddress)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVehicleActivityActive = true
    }

    override fun onPause() {
        super.onPause()
        isVehicleActivityActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }
}

@Composable
fun VehicleScreen(service: BluetoothLeService?, deviceName: String?, deviceAddress: String?) {
    // UI state for joysticks
    // throttle (left stick Y) starts at 0 (bottom). Map 0..100% to offset.
    // In our joystick mapping in detectDragGestures: offset.y of (radius-knobRadius) is bottom (-1.0 in normalized)
    // Actually, let's just initialize it to the bottom position:
    // radius = 88.dp, knobRadius = 28.dp -> maxDist = 60.dp
    // But we don't have density here easily. 
    // Let's use normalized coordinates in Joystick if possible, or just set it to a large enough value.
    // Re-evaluating: let's change how Joystick initializes or how VehicleScreen starts.
    
    // In Joystick component: onValueChange(Offset(offset.x / maxDist, -offset.y / maxDist))
    // If we want throttle 0, we want -offset.y / maxDist = 0? 
    // Wait, the current logic is:
    // val throttle = ((-leftOffset.y + 1f) / 2f * 100f).coerceIn(0f, 100f)
    // If leftOffset.y = 1f (bottom), throttle = ((-1 + 1)/2)*100 = 0.
    // If leftOffset.y = -1f (top), throttle = ((1 + 1)/2)*100 = 100.
    // If leftOffset.y = 0f (center), throttle = ((0 + 1)/2)*100 = 50.
    // So we want leftOffset.y to start at 1f.
    var leftOffset by remember { mutableStateOf(Offset(0f, 1f)) }
    var rightOffset by remember { mutableStateOf(Offset.Zero) }

    val connectionStatePair by (service?.connectionState ?: MutableStateFlow(null to BluetoothLeService.STATE_DISCONNECTED)).collectAsState()
    // Validate that the state update belongs to the device this Activity is interested in
    val isCorrectDevice = connectionStatePair.first == deviceAddress
    val connectionState = if (isCorrectDevice) connectionStatePair.second else BluetoothLeService.STATE_DISCONNECTED
    
    // Command history state moved up to share with sending logic
    val cmdHistory = remember { mutableStateListOf<String>() }
    
    // Remote states from drone
    var remoteTelemetry by remember { mutableStateOf(FlightCommands.Telemetry()) }

    // Listen to incoming telemetry
    LaunchedEffect(service) {
        service?.receivedData?.collect { data ->
            // Try 64-byte parser first, then fallback to 32-byte
            val telemetry = FlightCommands.parseTelemetry64(data) 
                ?: FlightCommands.parseTelemetry(data)
            
            telemetry?.let {
                remoteTelemetry = it
            }
        }
    }

    // Periodically send joystick data if connected
    LaunchedEffect(leftOffset, rightOffset, connectionState) {
        if (connectionState == BluetoothLeService.STATE_CONNECTED && service != null) {
            // throttle (forward/backward on left stick Y)
            // Map leftOffset.y: -1 (UP) -> 100.0 (FULL), 1 (DOWN) -> 0.0 (OFF)
            // The hardware expected range is 0 to 100 according to user feedback
            val throttle = ((-leftOffset.y + 1f) / 2f * 100f).coerceIn(0f, 100f)
            
            // Get saved UUIDs if available to ensure correct characteristic is used
            val prefs = service.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
            val savedJson = prefs.getString("saved_devices", "[]")
            val devicesArr = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
            var sUuid: UUID? = null
            var cUuid: UUID? = null
            
            for (i in 0 until devicesArr.length()) {
                val obj = devicesArr.getJSONObject(i)
                if (obj.getString("uid") == deviceAddress) {
                    val sStr = obj.optString("serviceUuid", "")
                    val cStr = obj.optString("charUuid", "")
                    if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                        sUuid = UUID.fromString(sStr)
                        cUuid = UUID.fromString(cStr)
                    }
                    break
                }
            }

            fun sendCmd(data: ByteArray) {
                val hex = data.joinToString("") { "%02X".format(it) }
                if (sUuid != null && cUuid != null) {
                    service.sendData(sUuid, cUuid, data)
                } else {
                    service.sendData(data)
                }
                // Add to history immediately for UI feedback
                if (cmdHistory.firstOrNull() != hex) {
                    cmdHistory.add(0, hex)
                    if (cmdHistory.size > 20) cmdHistory.removeAt(cmdHistory.size - 1)
                }
            }

            // Only send throttle if changed significantly or periodically
            // We use a small delay to avoid overwhelming the Bluetooth buffer
            kotlinx.coroutines.delay(50) 
            sendCmd(FlightCommands.setThrottle(throttle))

            // Map right stick to Move (forward, right)
            // rightOffset.y stays consistent with leftOffset.y: -1 (UP), 1 (DOWN)
            // Most flight controllers expect Pitch: 1.0 = Forward, -1.0 = Backward.
            // So if stick is UP (y = -1), we send 1.0. 
            // We use -rightOffset.y to achieve this.
            kotlinx.coroutines.delay(50)
            sendCmd(FlightCommands.move((-rightOffset.y).coerceIn(-1f, 1f), (rightOffset.x).coerceIn(-1f, 1f)))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10141A)) // Dark background from HTML
            .drawBehind {
                // Subtle grid
                val step = 40.dp.toPx()
                val stroke = 1.dp.toPx()
                val c = Color(0xFF7BDB80).copy(alpha = 0.03f)
                var x = 0f
                while (x < size.width) {
                    drawLine(c, Offset(x, 0f), Offset(x, size.height), stroke)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(c, Offset(0f, y), Offset(size.width, y), stroke)
                    y += step
                }
            }
    ) {
        // 1. Top Bar
        VehicleTopBar(
            service, 
            deviceName, 
            deviceAddress, 
            connectionState, 
            remoteTelemetry,
            onCommandSent = { hex ->
                cmdHistory.add(0, hex)
                if (cmdHistory.size > 20) cmdHistory.removeAt(cmdHistory.size - 1)
            }
        )

        // 2. HUD Elements (Left & Right)
        VehicleHudOverlays(remoteTelemetry)

        // 3. Central HUD Elements (Compass & Radar-like)
        VehicleCentralHud(remoteTelemetry)

        // 4. Main Controls (Joysticks)
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            // Left Stick - Throttle (Y stays, X/Yaw springs back)
            Joystick(
                modifier = Modifier.align(Alignment.BottomStart),
                initialOffset = leftOffset, // Pass initial offset
                isSpringy = true,   // X axis (Yaw) -> Springs back
                isSpringyY = false, // Y axis (Throttle) -> Stays put
                onValueChange = { leftOffset = it }
            )
            // Right Stick - Direction (Spring back on both)
            Joystick(
                modifier = Modifier.align(Alignment.BottomEnd),
                isSpringy = true,
                isSpringyY = true,
                onValueChange = { rightOffset = it }
            )
        }

        // 5. Center Button Grid
        VehicleControlButtonGrid(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), 
            service = service,
            deviceAddress = deviceAddress,
            onCommandSent = { hex -> 
                cmdHistory.add(0, hex)
                if (cmdHistory.size > 20) cmdHistory.removeAt(cmdHistory.size - 1)
            }
        )

        // 6. Bottom Telemetry Bar
        VehicleBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter), 
            telemetry = remoteTelemetry, 
            service = service,
            history = cmdHistory
        )
    }
}

@Composable
fun VehicleTopBar(
    service: BluetoothLeService?, 
    deviceName: String?, 
    deviceAddress: String?, 
    connectionState: Int,
    telemetry: FlightCommands.Telemetry,
    onCommandSent: (String) -> Unit = {} // Added callback
) {
    var modeExpanded by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf("DIRECT") } // Set default to DIRECT as requested
    val modes = listOf(
        "DIRECT" to FlightCommands.CONTROL_MODE_DIRECT,
        "STABILIZED" to FlightCommands.CONTROL_MODE_STABILIZED,
        "ALTITUDE" to FlightCommands.CONTROL_MODE_ALTITUDE,
        "VELOCITY" to FlightCommands.CONTROL_MODE_VELOCITY,
        "POSITION" to FlightCommands.CONTROL_MODE_POSITION
    )

    val isConnected = connectionState == BluetoothLeService.STATE_CONNECTED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF10141A).copy(alpha = 0.6f))
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.FlightTakeoff,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF7BDB80) else Color(0xFFFFB4AB),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = deviceName ?: "Unknown Device",
                    color = if (isConnected) Color(0xFF7BDB80) else Color(0xFFFFB4AB),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Connection dependent UI
            if (isConnected) {
                // Mode Switcher
                Box {
                    Surface(
                        onClick = { modeExpanded = true },
                        color = Color(0xFF7BDB80).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF7BDB80).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = currentMode,
                                color = Color(0xFF7BDB80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                null,
                                tint = Color(0xFF7BDB80),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                        modifier = Modifier.background(Color(0xFF181C22))
                    ) {
                        modes.forEach { (modeLabel, modeValue) ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        modeLabel, 
                                        color = if (modeLabel == currentMode) Color(0xFF7BDB80) else Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    ) 
                                },
                                onClick = {
                                    currentMode = modeLabel
                                    modeExpanded = false
                                    
                                    val prefs = service?.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
                                    val savedJson = prefs?.getString("saved_devices", "[]") ?: "[]"
                                    val devicesArr = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
                                    var sUuid: UUID? = null
                                    var cUuid: UUID? = null
                                    for (i in 0 until devicesArr.length()) {
                                        val obj = devicesArr.getJSONObject(i)
                                        if (obj.getString("uid") == deviceAddress) {
                                            val sStr = obj.optString("serviceUuid", "")
                                            val cStr = obj.optString("charUuid", "")
                                            if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                                                sUuid = UUID.fromString(sStr)
                                                cUuid = UUID.fromString(cStr)
                                            }
                                            break
                                        }
                                    }
                                    val data = FlightCommands.setMode(modeValue)
                                    if (sUuid != null && cUuid != null) {
                                        service?.sendData(sUuid, cUuid, data)
                                    } else {
                                        service?.sendData(data)
                                    }
                                    // ADDED: Force update history
                                    onCommandSent(data.joinToString("") { "%02X".format(it) })
                                }
                            )
                        }
                    }
                }
            } else {
                // Reconnect Button
                val isConnecting = connectionState == BluetoothLeService.STATE_CONNECTING
                Box(
                    modifier = Modifier
                        .size(32.dp) // Container size to ensure enough touch area
                        .clickable(enabled = !isConnecting) {
                            deviceAddress?.let { service?.connect(it) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp) // Actual visible background circle
                            .background(Color(0xFFFFB4AB).copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, Color(0xFFFFB4AB).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothConnected,
                            contentDescription = "Reconnect",
                            tint = Color(0xFFFFB4AB),
                            modifier = Modifier.size(16.dp) // Even smaller icon
                        )
                    }
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp), // Slightly larger than the circle
                            color = Color(0xFFFFB4AB),
                            strokeWidth = 1.dp
                        )
                    }
                }
            }
        }

        // Middle Telemetry - Absolute center
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xFF181C22), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HudStatusItem(Icons.Default.BatteryFull, "${telemetry.battery}%", if (telemetry.battery < 20) Color.Red else Color(0xFF7BDB80))
            HudStatusItem(Icons.Default.SignalCellularAlt, "${telemetry.rssi}dBm", Color(0xFF7BDB80))
            HudStatusItem(Icons.Default.SatelliteAlt, "${telemetry.satellites}", Color(0xFF7BDB80))
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HudIconButton(Icons.Default.Videocam)
            HudIconButton(Icons.Default.Settings)
        }
    }
}

@Composable
fun HudStatusItem(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun HudIconButton(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color(0xFF31353C), CircleShape)
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun VehicleHudOverlays(telemetry: FlightCommands.Telemetry) {
    // Left vertical scale (e.g. Battery mapping or Throttle)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(60.dp)
            .padding(start = 32.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.fillMaxHeight(0.4f).width(2.dp)) {
                val brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF7BDB80), Color.Transparent))
                drawRect(brush)
                
                // Draw scale marks
                val step = size.height / 10
                for (i in 0..10) {
                    val y = i * step
                    drawLine(
                        color = Color(0xFF7BDB80),
                        start = Offset(0f, y),
                        end = Offset(if (i % 5 == 0) 10.dp.toPx() else 5.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw indicator for relative altitude/throttle? Let's skip for brevity.
            }
            Column(
                modifier = Modifier.fillMaxHeight(0.4f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("100", "75", "50", "25", "0").forEach { label ->
                    Text(
                        text = label,
                        color = Color(0xFF7BDB80).copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
    // Right vertical scale
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 32.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.fillMaxHeight(0.4f),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf("50m", "40m", "30m", "20m", "10m", "0m").forEach { label ->
                    Text(
                        text = label,
                        color = Color(0xFF7BDB80).copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Canvas(modifier = Modifier.fillMaxHeight(0.4f).width(2.dp)) {
                val brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF7BDB80), Color.Transparent))
                drawRect(brush)
                
                // Draw scale marks
                val step = size.height / 10
                for (i in 0..10) {
                    val y = i * step
                    drawLine(
                        color = Color(0xFF7BDB80),
                        start = Offset(0f, y),
                        end = Offset(if (i % 5 == 0) -10.dp.toPx() else -5.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleCentralHud(telemetry: FlightCommands.Telemetry) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 40.dp)) {
        // Left Attitude HUD (3D perspective)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp)
                .size(128.dp),
            contentAlignment = Alignment.Center
        ) {
            Attitude3D(telemetry.quaternion)
        }

        // Right Radar-like
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
                .size(128.dp)
                .background(Color(0xFF10141A).copy(alpha = 0.6f), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clip(CircleShape)
        ) {
            // Radar scan animation
            val transition = rememberInfiniteTransition()
            val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)))
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                rotate(angle) {
                    val brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Transparent, Color(0xFF7BDB80).copy(alpha = 0.5f), Color.Transparent))
                    drawCircle(brush)
                }
                drawCircle(Color(0xFF7BDB80), 3.dp.toPx())
            }
        }
    }
}

@Composable
fun Attitude3D(quat: FloatArray) {
    // Safety check for quat size
    val finalQuat = if (quat.size == 4) quat else floatArrayOf(1f, 0f, 0f, 0f)
    
    // Convert quaternion to Euler angles (simplified)
    // Pitch (around X), Roll (around Y), Yaw (around Z)
    val w = finalQuat[0]
    val x = finalQuat[1]
    val y = finalQuat[2]
    val z = finalQuat[3]

    val roll = atan2(2f * (w * x + y * z), 1f - 2f * (x * x + y * y)) * (180f / PI.toFloat())
    val pitch = asin((2f * (w * y - z * x)).coerceIn(-1f, 1f)) * (180f / PI.toFloat())
    val yaw = atan2(2f * (w * z + x * y), 1f - 2f * (y * y + z * z)) * (180f / PI.toFloat())

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Static Coordinated System (Fixed XYZ axes)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val axisLength = size.width * 0.45f
            val axisColor = Color.White.copy(alpha = 0.2f)
            
            // X-axis (Right)
            drawLine(axisColor, center, center.copy(x = center.x + axisLength), 1.dp.toPx())
            // Y-axis (Forward/Up in 2D projection)
            drawLine(axisColor, center, center.copy(y = center.y - axisLength), 1.dp.toPx())
            
            // Labels for axes
            drawCircle(axisColor, 2.dp.toPx(), center)
        }

        // 2. Dynamic Drone Model (Represented by a stylized 3D Drone Shape)
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    rotationX = -pitch
                    rotationY = roll
                    rotationZ = -yaw
                    cameraDistance = 12 * density
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val armLen = size.width * 0.35f
                val motorRadius = 6.dp.toPx()
                val bodyWidth = 12.dp.toPx()
                val bodyHeight = 20.dp.toPx()
                
                val accentColor = Color(0xFF7BDB80) // Drone green
                val frameColor = Color.White.copy(alpha = 0.8f)
                val bodyColor = Color(0xFF25292E)
                val bottomSurfaceColor = Color(0xFF15191E)
                val highlightColor = Color.White.copy(alpha = 0.3f)

                // Draw a shadow/depth for the body to make it look "thick"
                // This draws a darker version slightly offset to represent the sides/bottom
                drawRoundRect(
                    color = bottomSurfaceColor,
                    topLeft = Offset(center.x - bodyWidth / 2, center.y - bodyHeight / 2 + 4.dp.toPx()),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // 1. Draw central body (Battery/Flight Controller area)
                // Main body block (The Top Surface)
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(center.x - bodyWidth / 2, center.y - bodyHeight / 2),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                
                // Add a "top bulge" or protrusion (Gps/Sensor housing) with even more height
                val protrusionWidth = bodyWidth * 0.7f
                val protrusionHeight = bodyHeight * 0.4f
                // Protrusion Side/Shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(center.x - protrusionWidth / 2, center.y - protrusionHeight / 2 + 2.dp.toPx()),
                    size = Size(protrusionWidth, protrusionHeight),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                // Protrusion Top
                drawRoundRect(
                    color = Color(0xFF45494E),
                    topLeft = Offset(center.x - protrusionWidth / 2, center.y - protrusionHeight / 2),
                    size = Size(protrusionWidth, protrusionHeight),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                
                // Highlight on the protrusion to show light hitting the "top"
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(center.x - protrusionWidth / 2 + 1.dp.toPx(), center.y - protrusionHeight / 2 + 1.dp.toPx()),
                    size = Size(protrusionWidth - 2.dp.toPx(), 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )

                // Body Outline
                drawRoundRect(
                    color = frameColor,
                    topLeft = Offset(center.x - bodyWidth / 2, center.y - bodyHeight / 2),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(1.5.dp.toPx())
                )

                // 2. Draw 4 Arms (X-config)
                val angles = listOf(45f, 135f, 225f, 315f)
                angles.forEachIndexed { index, angleDeg ->
                    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                    val endX = center.x + armLen * cos(angleRad)
                    val endY = center.y + armLen * sin(angleRad)
                    
                    // Draw Arm
                    drawLine(
                        color = frameColor,
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    // Draw Motor/Propeller circle at end
                    // Front motors can be a different color to indicate heading
                    val mColor = if (index < 2) accentColor else frameColor
                    
                    // Draw propeller "disc" to show top surface - Increase visibility
                    drawCircle(
                        color = mColor.copy(alpha = 0.2f),
                        radius = motorRadius * 2f,
                        center = Offset(endX, endY)
                    )
                    
                    // Thick motor housing (3D look)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.4f),
                        radius = motorRadius,
                        center = Offset(endX, endY + 2.dp.toPx())
                    )
                    drawCircle(
                        color = mColor,
                        radius = motorRadius,
                        center = Offset(endX, endY),
                        style = Stroke(2.dp.toPx())
                    )
                    
                    // Small dot for motor center
                    drawCircle(
                        color = mColor,
                        radius = 1.dp.toPx(),
                        center = Offset(endX, endY)
                    )
                }

                // 3. Heading indicator (Forward Arrow on top of body)
                // Make it look like it's sticking up
                val headLen = 10.dp.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x, center.y - bodyHeight / 2 - 6.dp.toPx())
                    lineTo(center.x - 6.dp.toPx(), center.y - bodyHeight / 2 + headLen)
                    lineTo(center.x + 6.dp.toPx(), center.y - bodyHeight / 2 + headLen)
                    close()
                }
                drawPath(path, color = accentColor)
                
                // 4. Add a VERY obvious "TOP" label or icon
                drawCircle(
                    color = accentColor,
                    radius = 2.dp.toPx(),
                    center = center
                )
            }
        }
    }
}

@Composable
fun Joystick(
    modifier: Modifier = Modifier, 
    initialOffset: Offset = Offset.Zero, // New parameter
    isSpringy: Boolean = true,
    isSpringyY: Boolean = true, // Added Y-axis spring control
    onValueChange: (Offset) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    val radius = 88.dp
    val knobRadius = 28.dp

    // Initialize offset based on initialOffset and density
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(initialOffset) {
        val maxDist = with(density) { (radius - knobRadius).toPx() }
        // The internal state 'offset' uses top-left as (0,0), but the knob is centered.
        // In the 'onDrag' logic, 'offset' is relative to the center.
        // positive Y is DOWN in the UI.
        offset = Offset(initialOffset.x * maxDist, initialOffset.y * maxDist)
    }

    Box(
        modifier = modifier
            .size(radius * 2)
            .background(Color(0xFF10141A).copy(alpha = 0.6f), CircleShape)
            .border(1.dp, Color(0xFF7BDB80).copy(alpha = 0.1f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        val maxDist = (radius - knobRadius).toPx()
                        val newX = if (isSpringy) 0f else offset.x
                        val newY = if (isSpringyY) 0f else offset.y
                        offset = Offset(newX, newY)
                        
                        // Emit normalized value: Y up is negative, so we negate it for 'onValueChange'
                        onValueChange(Offset(offset.x / maxDist, offset.y / maxDist))
                    },
                    onDragCancel = {
                        val maxDist = (radius - knobRadius).toPx()
                        val newX = if (isSpringy) 0f else offset.x
                        val newY = if (isSpringyY) 0f else offset.y
                        offset = Offset(newX, newY)
                        
                        onValueChange(Offset(offset.x / maxDist, offset.y / maxDist))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offset + dragAmount
                        val dist = newOffset.getDistance()
                        val maxDist = (radius - knobRadius).toPx()
                        offset = if (dist <= maxDist) newOffset else newOffset * (maxDist / dist)
                        // Emit normalized value: X right is positive, Y down is positive
                        onValueChange(Offset(offset.x / maxDist, offset.y / maxDist))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(knobRadius * 2)
                .background(Color(0xFF31353C), CircleShape)
                .border(1.dp, Color(0xFF7BDB80).copy(alpha = 0.2f), CircleShape)
                .shadow(8.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(20.dp).background(Color(0xFF7BDB80).copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFF7BDB80), CircleShape))
            }
        }
    }
}

@Composable
fun VehicleControlButtonGrid(
    service: BluetoothLeService?, 
    modifier: Modifier = Modifier, 
    deviceAddress: String? = null,
    onCommandSent: (String) -> Unit = {}
) {
    // Helper to send command with potential UUIDs
    fun sendCmd(data: ByteArray) {
        if (service == null) return
        val prefs = service.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
        val savedJson = prefs.getString("saved_devices", "[]")
        val devicesArr = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
        var sUuid: UUID? = null
        var cUuid: UUID? = null
        
        for (i in 0 until devicesArr.length()) {
            val obj = devicesArr.getJSONObject(i)
            if (obj.getString("uid") == deviceAddress) {
                val sStr = obj.optString("serviceUuid", "")
                val cStr = obj.optString("charUuid", "")
                if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                    sUuid = UUID.fromString(sStr)
                    cUuid = UUID.fromString(cStr)
                }
                break
            }
        }

        if (sUuid != null && cUuid != null) {
            service.sendData(sUuid, cUuid, data)
        } else {
            service.sendData(data)
        }
        onCommandSent(data.joinToString("") { "%02X".format(it) })
    }

    val buttons = listOf(
        Triple("ARM", Icons.Default.LockOpen, Color(0xFFFFB4AB)),
        Triple("DISARM", Icons.Default.Lock, Color(0xFF7BDB80)),
        Triple("TAKEOFF", Icons.Default.FileUpload, Color(0xFF7BDB80)),
        Triple("LAND", Icons.Default.FileDownload, Color(0xFF7BDB80)),
        Triple("RTL", Icons.Default.Home, Color(0xFFBECABA)),
        Triple("E-STOP", Icons.Default.Report, Color.Red)
    )

    Row(
        modifier = modifier
            .wrapContentWidth()
            .height(56.dp)
            .background(Color(0xFF1A1E24).copy(alpha = 0.8f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { (label, icon, color) ->
            val isEstop = label == "E-STOP"
            
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 44.dp)
                    .clip(CircleShape)
                    .then(
                        if (isEstop) Modifier.background(Color.Red.copy(alpha = 0.15f))
                        else Modifier
                    )
                    .clickable { 
                        when(label) {
                            "ARM" -> sendCmd(FlightCommands.arm())
                            "DISARM" -> sendCmd(FlightCommands.disarm())
                            "TAKEOFF" -> sendCmd(FlightCommands.takeoff(1.0f)) 
                            "LAND" -> sendCmd(FlightCommands.land())
                            "E-STOP" -> sendCmd(FlightCommands.emergencyStop())
                            "RTL" -> sendCmd(FlightCommands.hover())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        icon, 
                        null, 
                        tint = if (isEstop) Color.Red else if (label == "TAKEOFF") Color(0xFF7BDB80) else color,
                        modifier = Modifier.size(18.dp) // Smaller icons from 24dp
                    )
                    Text(
                        text = label,
                        color = (if (isEstop) Color.Red else if (label == "TAKEOFF") Color(0xFF7BDB80) else color).copy(alpha = 0.7f),
                        fontSize = 6.sp, // Even smaller text
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Subtle dot divider instead of line to save space
            if (buttons.indexOf(Triple(label, icon, color)) != buttons.size - 1) {
                Box(modifier = Modifier.size(2.dp).background(Color.White.copy(alpha = 0.1f), CircleShape))
            }
        }
    }
}

@Composable
fun VehicleBottomBar(
    modifier: Modifier = Modifier, 
    telemetry: FlightCommands.Telemetry,
    service: BluetoothLeService?,
    history: List<String>
) {
    var showHistory by remember { mutableStateOf(false) }


    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFF0A0E14).copy(alpha = 0.8f))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart // Change to start-aligned
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left-aligned telemetry items
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val q = telemetry.quaternion
                TelemetryItem("QUAT", String.format(Locale.US, "W:%.2f X:%.2f Y:%.2f Z:%.2f", q[0], q[1], q[2], q[3]))
                TelemetryItem("ALT", String.format(Locale.US, "%.2fm", telemetry.altitude))
                TelemetryItem("SPD", String.format(Locale.US, "%.2fm/s", telemetry.velocity))
                if (telemetry.latitude != 0.0) {
                    TelemetryItem("GPS", String.format(Locale.US, "%.5f,%.5f", telemetry.latitude, telemetry.longitude))
                }
            }

            // Right-aligned history preview
            Box(modifier = Modifier.clickable { if (history.isNotEmpty()) showHistory = true }) {
                Text(
                    text = history.firstOrNull() ?: "NO CMD",
                    color = Color(0xFF7BDB80).copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }

    if (showHistory) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            containerColor = Color(0xFF10141A),
            contentColor = Color.White
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                item {
                    Text(
                        "Command History", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp, 
                        color = Color(0xFF7BDB80),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                items(history) { cmd ->
                    Text(
                        cmd, 
                        fontFamily = FontFamily.Monospace, 
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
fun TelemetryItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label + ":", color = Color(0xFF7BDB80).copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AfireflyTheme {
        VehicleScreen(null, "Device Name", "00:11:22:33:44:55")
    }
}