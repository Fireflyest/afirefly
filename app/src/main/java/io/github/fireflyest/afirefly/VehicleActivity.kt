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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.tooling.preview.Devices
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
fun VehicleScreen(
    service: BluetoothLeService?,
    deviceName: String?,
    deviceAddress: String?
) {
    var leftOffset by remember { mutableStateOf(Offset(0f, 1f)) }
    var rightOffset by remember { mutableStateOf(Offset.Zero) }

    val connectionStatePair by (
            service?.connectionState
                ?: MutableStateFlow(null to BluetoothLeService.STATE_DISCONNECTED)
            ).collectAsState()
    val isCorrectDevice = connectionStatePair.first == deviceAddress
    val connectionState = if (isCorrectDevice) connectionStatePair.second
    else BluetoothLeService.STATE_DISCONNECTED

    val cmdHistory = remember { mutableStateListOf<String>() }
    var remoteTelemetry by remember { mutableStateOf(FlightCommands.Telemetry()) }

    LaunchedEffect(service) {
        service?.receivedData?.collect { data ->
            val telemetry = FlightCommands.parseTelemetryDelta(data, remoteTelemetry)
                ?: FlightCommands.parseTelemetry(data)
            telemetry?.let { remoteTelemetry = it }
        }
    }

    LaunchedEffect(leftOffset, rightOffset, connectionState) {
        if (connectionState == BluetoothLeService.STATE_CONNECTED && service != null) {
            val throttle = ((-leftOffset.y + 1f) / 2f * 100f).coerceIn(0f, 100f)
            val prefs = service.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
            val savedJson = prefs.getString("saved_devices", "[]")
            val devicesArr = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
            var sUuid: UUID? = null; var cUuid: UUID? = null
            for (i in 0 until devicesArr.length()) {
                val obj = devicesArr.getJSONObject(i)
                if (obj.getString("uid") == deviceAddress) {
                    val s = obj.optString("serviceUuid", "")
                    val c = obj.optString("charUuid", "")
                    if (s.isNotEmpty() && c.isNotEmpty()) {
                        sUuid = UUID.fromString(s); cUuid = UUID.fromString(c)
                    }; break
                }
            }
            fun sendCmd(data: ByteArray) {
                if (sUuid != null && cUuid != null) service.sendData(sUuid, cUuid, data)
                else service.sendData(data)
                val hex = data.joinToString("") { "%02X".format(it) }
                if (cmdHistory.firstOrNull() != hex) {
                    cmdHistory.add(0, hex)
                    if (cmdHistory.size > 20) cmdHistory.removeAt(cmdHistory.size - 1)
                }
            }
            kotlinx.coroutines.delay(50)
            sendCmd(FlightCommands.setThrottle(throttle))
            kotlinx.coroutines.delay(50)
            sendCmd(FlightCommands.move(
                (-rightOffset.y).coerceIn(-1f, 1f),
                rightOffset.x.coerceIn(-1f, 1f)
            ))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0E14))) {

        // 1. 全屏透明姿态 HUD
        AttitudeHUD(
            quaternion = remoteTelemetry.quaternion,
            modifier = Modifier.fillMaxSize()
        )

        // 2. 顶栏
        VehicleTopBar(service, deviceName, deviceAddress, connectionState, remoteTelemetry) {
            cmdHistory.add(0, it)
            if (cmdHistory.size > 20) cmdHistory.removeAt(cmdHistory.size - 1)
        }

        // 3. 右上角：罗盘 + 卫星 + 高度
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            HeadingRadar(
                yaw = getYaw(remoteTelemetry.quaternion),
                modifier = Modifier.size(64.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SatelliteAlt,
                    contentDescription = null,
                    tint = Color(0xFF7BDB80),
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = "${remoteTelemetry.satellites}",
                    color = Color(0xFF7BDB80),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )
                Text(
                    text = String.format(Locale.US, "%.1fm", remoteTelemetry.altitude),
                    color = Color(0xFF7BDB80),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 4. 摇杆
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 100.dp, bottom = 80.dp)
        ) {
            Joystick(
                modifier = Modifier.align(Alignment.BottomStart),
                initialOffset = leftOffset,
                isSpringy = true,
                isSpringyY = false,
                onValueChange = { leftOffset = it }
            )
            Joystick(
                modifier = Modifier.align(Alignment.BottomEnd),
                isSpringy = true,
                isSpringyY = true,
                onValueChange = { rightOffset = it }
            )
        }

        // 5. 底部栏（按钮 + 遥测 + 历史 同一行）
        VehicleBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            telemetry = remoteTelemetry,
            service = service,
            deviceAddress = deviceAddress,
            history = cmdHistory,
            onCommandSent = {
                cmdHistory.add(0, it)
                if (cmdHistory.size > 20) cmdHistory.removeAt(cmdHistory.size - 1)
            }
        )
    }
}


private fun getYaw(quaternion: FloatArray): Float {
    val q = if (quaternion.size >= 4) quaternion else floatArrayOf(1f, 0f, 0f, 0f)
    val yawRad = atan2(2f * (q[0] * q[3] + q[1] * q[2]),
        1f - 2f * (q[2] * q[2] + q[3] * q[3]))
    return ((yawRad * 180f / PI.toFloat()) % 360f + 360f) % 360f
}

@Composable
fun AttitudeHUD(
    quaternion: FloatArray,
    modifier: Modifier = Modifier
) {
    val q = if (quaternion.size >= 4) quaternion else floatArrayOf(1f, 0f, 0f, 0f)
    val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]

    val rollRad  = atan2(2f * (w * x + y * z), 1f - 2f * (x * x + y * y))
    val pitchRad = asin((2f * (w * y - z * x)).coerceIn(-1f, 1f))
    val yawRad   = atan2(2f * (w * z + x * y), 1f - 2f * (y * y + z * z))

    val targetRoll  = rollRad * 180f / PI.toFloat()
    val targetPitch = pitchRad * 180f / PI.toFloat()
    val yawDeg = ((yawRad * 180f / PI.toFloat()) % 360f + 360f) % 360f

    val smoothRoll by animateFloatAsState(
        targetValue = targetRoll,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f)
    )
    val smoothPitch by animateFloatAsState(
        targetValue = targetPitch,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f)
    )

    val hudGreen = Color(0xFF00FF88)
    val hudAmber = Color(0xFFFFB422)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val ppd = r / 40f
        val nc = drawContext.canvas.nativeCanvas

        // 预创建 Paint 对象
        val degreePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00FF88")
            textSize = 8.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            alpha = 120
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.NORMAL
            )
        }

        val headingPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00FF88")
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            alpha = 200
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD
            )
        }

        val infoPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00FF88")
            textSize = 7.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            alpha = 140
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.NORMAL
            )
        }

        // ═══════════════════════════════════════════════════
        // 第1层：旋转的天空/地面
        // ═══════════════════════════════════════════════════
        rotate(-smoothRoll, Offset(cx, cy)) {
            val horizonY = cy + smoothPitch * ppd

            // 天空
            drawRect(
                color = Color(0xFF1A3A5C).copy(alpha = 0.12f),
                topLeft = Offset.Zero,
                size = Size(size.width, horizonY.coerceIn(0f, size.height))
            )

            // 地面
            drawRect(
                color = Color(0xFF5D4037).copy(alpha = 0.10f),
                topLeft = Offset(0f, horizonY),
                size = Size(size.width, (size.height - horizonY).coerceAtLeast(0f))
            )

            // 俯仰阶梯线
            for (deg in -40..40 step 10) {
                if (deg == 0) continue
                val lineY = horizonY - deg * ppd
                if (lineY < -r || lineY > size.height + r) continue

                val isMajor = deg % 20 == 0
                val alpha = if (isMajor) 0.25f else 0.12f
                val sw = if (isMajor) 1.2f else 0.6f
                val halfW = if (isMajor) r * 0.30f else r * 0.18f
                val gap = r * 0.10f

                drawLine(hudGreen.copy(alpha = alpha),
                    Offset(cx - halfW, lineY), Offset(cx - gap, lineY), sw.dp.toPx())
                drawLine(hudGreen.copy(alpha = alpha),
                    Offset(cx + gap, lineY), Offset(cx + halfW, lineY), sw.dp.toPx())

                if (isMajor) {
                    nc.drawText(
                        "${abs(deg)}°",
                        cx - halfW - 12.dp.toPx(),
                        lineY + 3.dp.toPx(),
                        degreePaint
                    )
                }
            }

            // 地平线
            drawLine(hudGreen, Offset(cx - r * 1.5f, horizonY),
                Offset(cx + r * 1.5f, horizonY), 1.5.dp.toPx())

            // 地面透视网格线
            for (i in -6..6) {
                val gx = cx + i * (r * 0.12f)
                drawLine(
                    hudGreen.copy(alpha = 0.08f),
                    Offset(cx + i * r * 0.02f, horizonY),
                    Offset(gx, size.height),
                    0.5.dp.toPx()
                )
            }

            // 横滚弧形刻度
            val arcR = r * 0.92f
            for (deg in -60..60 step 5) {
                val angleRad = Math.toRadians((deg - 90).toDouble()).toFloat()
                val isMajor = deg % 30 == 0
                val isMid = deg % 10 == 0
                val tickLen = when {
                    isMajor -> 10.dp.toPx()
                    isMid   -> 6.dp.toPx()
                    else    -> 3.dp.toPx()
                }
                val a = when {
                    isMajor -> 0.8f
                    isMid   -> 0.4f
                    else    -> 0.2f
                }

                drawLine(
                    hudGreen.copy(alpha = a),
                    Offset(cx + (arcR - tickLen) * cos(angleRad), cy + (arcR - tickLen) * sin(angleRad)),
                    Offset(cx + arcR * cos(angleRad), cy + arcR * sin(angleRad)),
                    (if (isMajor) 1.5f else 0.8f).dp.toPx()
                )
            }
        }

        // ═══════════════════════════════════════════════════
        // 第2层：固定飞机参考符号
        // ═══════════════════════════════════════════════════
        val wingLen = r * 0.22f
        val wingGap = r * 0.06f

        drawLine(hudAmber, Offset(cx - wingGap, cy), Offset(cx - wingLen, cy), 2.dp.toPx())
        drawLine(hudAmber, Offset(cx - wingLen, cy), Offset(cx - wingLen, cy - 3.dp.toPx()), 2.dp.toPx())
        drawLine(hudAmber, Offset(cx + wingGap, cy), Offset(cx + wingLen, cy), 2.dp.toPx())
        drawLine(hudAmber, Offset(cx + wingLen, cy), Offset(cx + wingLen, cy - 3.dp.toPx()), 2.dp.toPx())
        drawCircle(hudAmber, 2.5.dp.toPx(), Offset(cx, cy))

        // ═══════════════════════════════════════════════════
        // 第3层：固定横滚指针
        // ═══════════════════════════════════════════════════
        val triR = r * 0.92f
        val triY = cy - triR + 2.dp.toPx()
        drawPath(
            Path().apply {
                moveTo(cx, triY)
                lineTo(cx - 4.dp.toPx(), triY + 7.dp.toPx())
                lineTo(cx + 4.dp.toPx(), triY + 7.dp.toPx())
                close()
            },
            hudAmber
        )

        // ═══════════════════════════════════════════════════
        // 第4层：信息文字（直接用 nc.drawText）
        // ═══════════════════════════════════════════════════
        nc.drawText(
            String.format(Locale.US, "%03d°", yawDeg.toInt()),
            cx, cy + r * 0.55f, headingPaint
        )
        nc.drawText(
            String.format(Locale.US, "R:%+.0f°  P:%+.0f°", smoothRoll, smoothPitch),
            cx, cy + r * 0.55f + 12.dp.toPx(), infoPaint
        )
    }
}


@Composable
fun HeadingRadar(
    yaw: Float,
    modifier: Modifier = Modifier
) {
    val smoothYaw by animateFloatAsState(
        targetValue = yaw,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 100f)
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF0D1117))
            .border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(cx, cy)

            // 同心圆
            for (i in 1..3) {
                drawCircle(
                    Color(0xFF7BDB80).copy(alpha = 0.1f),
                    r * i / 3f,
                    center = Offset(cx, cy),
                    style = Stroke(0.5.dp.toPx())
                )
            }

            // 十字线
            val crossAlpha = 0.15f
            drawLine(Color(0xFF7BDB80).copy(alpha = crossAlpha),
                Offset(cx, cy - r), Offset(cx, cy + r), 0.5.dp.toPx())
            drawLine(Color(0xFF7BDB80).copy(alpha = crossAlpha),
                Offset(cx - r, cy), Offset(cx + r, cy), 0.5.dp.toPx())

            // 航向刻度盘（旋转）
            rotate(-smoothYaw, Offset(cx, cy)) {
                val cardinalDirs = mapOf(
                    0f to "N", 90f to "E", 180f to "S", 270f to "W"
                )
                val tickPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#7BDB80")
                    textSize = 7.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.MONOSPACE
                }

                for (deg in 0..359 step 10) {
                    val angleRad = Math.toRadians((deg - 90).toDouble()).toFloat()
                    val isCardinal = deg % 90 == 0
                    val tickLen = if (isCardinal) 8.dp.toPx() else 4.dp.toPx()
                    val alpha = if (isCardinal) 0.9f else 0.3f

                    val outerX = cx + r * cos(angleRad)
                    val outerY = cy + r * sin(angleRad)
                    val innerX = cx + (r - tickLen) * cos(angleRad)
                    val innerY = cy + (r - tickLen) * sin(angleRad)

                    drawLine(
                        Color(0xFF7BDB80).copy(alpha = alpha),
                        Offset(innerX, innerY),
                        Offset(outerX, outerY),
                        strokeWidth = (if (isCardinal) 1.5f else 0.8f).dp.toPx()
                    )

                    // 标注 N/S/E/W
                    if (isCardinal) {
                        val label = cardinalDirs[deg.toFloat()] ?: ""
                        val labelR = r - tickLen - 8.dp.toPx()
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            cx + labelR * cos(angleRad),
                            cy + labelR * sin(angleRad) + 3.dp.toPx(),
                            tickPaint
                        )
                    }
                }
            }

            // 固定三角指针（顶部）
            val triY = cy - r + 4.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(cx, triY)
                    lineTo(cx - 4.dp.toPx(), triY + 6.dp.toPx())
                    lineTo(cx + 4.dp.toPx(), triY + 6.dp.toPx())
                    close()
                },
                Color(0xFFFFB422)
            )

            // 航向数字
            drawContext.canvas.nativeCanvas.apply {
                val numPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                drawText(
                    String.format(Locale.US, "%03d°", smoothYaw.toInt()),
                    cx, cy + 4.dp.toPx(), numPaint
                )
            }
        }
    }
}



@Composable
fun VehicleTopBar(
    service: BluetoothLeService?,
    deviceName: String?,
    deviceAddress: String?,
    connectionState: Int,
    telemetry: FlightCommands.Telemetry,
    onCommandSent: (String) -> Unit = {}
) {
    var modeExpanded by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf("DIRECT") }
    val modes = listOf(
        "DIRECT" to FlightCommands.CONTROL_MODE_DIRECT,
        "STABILIZED" to FlightCommands.CONTROL_MODE_STABILIZED,
        "ALTITUDE" to FlightCommands.CONTROL_MODE_ALTITUDE,
        "VELOCITY" to FlightCommands.CONTROL_MODE_VELOCITY,
        "POSITION" to FlightCommands.CONTROL_MODE_POSITION
    )

    val isConnected = connectionState == BluetoothLeService.STATE_CONNECTED

    // 电量颜色
    val batteryColor = when {
        telemetry.battery < 20 -> Color.Red
        telemetry.battery < 50 -> Color(0xFFFFCC00)
        else -> Color(0xFF7BDB80)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF10141A).copy(alpha = 0.6f))
            .padding(horizontal = 24.dp)
    ) {
        // ── 左侧：电量图标 + 设备名 + 模式切换 ──
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 电量图标 + 设备名
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 电量图标（替换原来的 FlightTakeoff）
                Icon(
                    imageVector = when {
                        telemetry.battery < 20 -> Icons.Default.BatteryAlert
                        telemetry.battery < 50 -> Icons.Default.BatteryStd
                        else -> Icons.Default.BatteryFull
                    },
                    contentDescription = "Battery",
                    tint = batteryColor,
                    modifier = Modifier.size(20.dp)
                )
                // 电量百分比
                Text(
                    text = "${telemetry.battery}%",
                    color = batteryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // 分隔线
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // 设备名
                Text(
                    text = deviceName ?: "Unknown Device",
                    color = if (isConnected) Color(0xFF7BDB80) else Color(0xFFFFB4AB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // 连接状态：模式切换
            if (isConnected) {
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
                                    var sUuid: UUID? = null; var cUuid: UUID? = null
                                    for (i in 0 until devicesArr.length()) {
                                        val obj = devicesArr.getJSONObject(i)
                                        if (obj.getString("uid") == deviceAddress) {
                                            val sStr = obj.optString("serviceUuid", "")
                                            val cStr = obj.optString("charUuid", "")
                                            if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                                                sUuid = UUID.fromString(sStr); cUuid = UUID.fromString(cStr)
                                            }; break
                                        }
                                    }
                                    val data = FlightCommands.setMode(modeValue)
                                    if (sUuid != null && cUuid != null) service?.sendData(sUuid, cUuid, data)
                                    else service?.sendData(data)
                                    onCommandSent(data.joinToString("") { "%02X".format(it) })
                                }
                            )
                        }
                    }
                }
            } else {
                val isConnecting = connectionState == BluetoothLeService.STATE_CONNECTING
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = !isConnecting) {
                            deviceAddress?.let { service?.connect(it) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFFFFB4AB).copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, Color(0xFFFFB4AB).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothConnected,
                            contentDescription = "Reconnect",
                            tint = Color(0xFFFFB4AB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(0xFFFFB4AB),
                            strokeWidth = 1.dp
                        )
                    }
                }
            }
        }

        // ── 右侧：PID + 摄像头 + 设置 ──
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PidMiniChart("RATE PID", telemetry, Color(0xFF7BDB80))
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

        // 左侧：姿态指示器（人工地平仪）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp)
                .size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            AttitudeIndicator(telemetry.quaternion)
        }

        // 右侧：雷达
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
                .size(128.dp)
                .background(Color(0xFF10141A).copy(alpha = 0.6f), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clip(CircleShape)
        ) {
            val transition = rememberInfiniteTransition()
            val angle by transition.animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(4000, easing = LinearEasing))
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                rotate(angle) {
                    val brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFF7BDB80).copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                    drawCircle(brush)
                }
                drawCircle(Color(0xFF7BDB80), 3.dp.toPx())
            }
        }
    }
}


@Composable
fun AttitudeIndicator(
    quaternion: FloatArray,
    modifier: Modifier = Modifier
) {
    val q = if (quaternion.size >= 4) quaternion else floatArrayOf(1f, 0f, 0f, 0f)
    val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]

    // ── 四元数 → 欧拉角（航空航天约定：FRD 机体 / NED 世界）──
    // Roll  (φ): 绕机体 X 轴（机头方向），正 = 右倾
    // Pitch (θ): 绕机体 Y 轴（右方向），正 = 抬头
    // Yaw   (ψ): 绕机体 Z 轴（下方向），正 = 右转
    val rollRad  = atan2(2f * (w * x + y * z), 1f - 2f * (x * x + y * y))
    val pitchRad = asin((2f * (w * y - z * x)).coerceIn(-1f, 1f))
    val yawRad   = atan2(2f * (w * z + x * y), 1f - 2f * (y * y + z * z))

    val targetRoll  = rollRad * 180f / PI.toFloat()
    val targetPitch = pitchRad * 180f / PI.toFloat()
    val yawDeg = ((yawRad * 180f / PI.toFloat()) % 360f + 360f) % 360f

    // ── 弹簧动画平滑抖动 ──
    val smoothRoll by animateFloatAsState(
        targetValue = targetRoll,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f)
    )
    val smoothPitch by animateFloatAsState(
        targetValue = targetPitch,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f)
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF0D1117))
            .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(cx, cy)
            val ppd = r / 45f // 每度对应像素数（45°填满半径）

            // ═══════════════════════════════════════════════════════
            // 第1层：旋转的世界坐标系
            //   画布旋转 -roll，天空/地面反向倾斜
            // ═══════════════════════════════════════════════════════
            rotate(-smoothRoll, Offset(cx, cy)) {
                // 地平线 Y 位置：pitch 正 → 抬头 → 地平线下移 → 天空区域增大
                val horizonY = cy + smoothPitch * ppd

                // 天空（地平线以上）
                drawRect(
                    color = Color(0xFF1565C0),
                    topLeft = Offset.Zero,
                    size = Size(size.width, horizonY.coerceIn(0f, size.height))
                )

                // 地面（地平线以下）
                drawRect(
                    color = Color(0xFF5D4037),
                    topLeft = Offset(0f, horizonY),
                    size = Size(size.width, (size.height - horizonY).coerceAtLeast(0f))
                )

                // 地平线
                drawLine(
                    color = Color.White,
                    start = Offset(cx - r * 1.2f, horizonY),
                    end = Offset(cx + r * 1.2f, horizonY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // ── 俯仰阶梯线 ──
                for (deg in -40..40 step 10) {
                    if (deg == 0) continue
                    val lineY = horizonY - deg * ppd
                    if (lineY < -r * 1.5f || lineY > size.height + r * 1.5f) continue

                    val isMajor = deg % 20 == 0
                    val halfW = if (isMajor) r * 0.35f else r * 0.2f
                    val gap   = if (isMajor) 0f else r * 0.12f
                    var alpha = if (isMajor) 0.6f else 0.35f
                    val sw    = if (isMajor) 1.5f else 1f

                    // 左侧线段
                    drawLine(
                        Color.White.copy(alpha = alpha),
                        Offset(cx - halfW, lineY),
                        Offset(cx - gap, lineY),
                        strokeWidth = sw.dp.toPx()
                    )
                    // 右侧线段
                    drawLine(
                        Color.White.copy(alpha = alpha),
                        Offset(cx + gap, lineY),
                        Offset(cx + halfW, lineY),
                        strokeWidth = sw.dp.toPx()
                    )

                    // 度数标签
                    if (isMajor) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 7.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                alpha = 160F
                            }
                            drawText(
                                "${abs(deg)}",
                                cx - halfW - 10.dp.toPx(),
                                lineY + 3.dp.toPx(),
                                paint
                            )
                        }
                    }
                }

                // ── 横滚弧形刻度（随世界旋转）──
                val arcR = r * 0.88f
                for (deg in -60..60 step 10) {
                    val angleRad = Math.toRadians((deg - 90).toDouble()).toFloat()
                    val isMajor = deg % 30 == 0
                    val tickLen = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                    val alpha   = if (isMajor) 0.8f else 0.4f

                    val outerX = cx + arcR * cos(angleRad)
                    val outerY = cy + arcR * sin(angleRad)
                    val innerX = cx + (arcR - tickLen) * cos(angleRad)
                    val innerY = cy + (arcR - tickLen) * sin(angleRad)

                    drawLine(
                        Color.White.copy(alpha = alpha),
                        Offset(innerX, innerY),
                        Offset(outerX, outerY),
                        strokeWidth = (if (isMajor) 1.5f else 1f).dp.toPx()
                    )
                }
            }

            // ═══════════════════════════════════════════════════════
            // 第2层：固定飞机参考符号（不随世界旋转）
            // ═══════════════════════════════════════════════════════
            val wingLen = r * 0.28f
            val wingGap = r * 0.08f
            val wingColor = Color(0xFFFFB422)

            // 左翼
            drawLine(wingColor, Offset(cx - wingGap, cy), Offset(cx - wingLen, cy), 2.dp.toPx())
            drawLine(wingColor, Offset(cx - wingLen, cy), Offset(cx - wingLen, cy - 4.dp.toPx()), 2.dp.toPx())
            // 右翼
            drawLine(wingColor, Offset(cx + wingGap, cy), Offset(cx + wingLen, cy), 2.dp.toPx())
            drawLine(wingColor, Offset(cx + wingLen, cy), Offset(cx + wingLen, cy - 4.dp.toPx()), 2.dp.toPx())
            // 中心点
            drawCircle(wingColor, 3.dp.toPx(), Offset(cx, cy))

            // ═══════════════════════════════════════════════════════
            // 第3层：固定横滚指针（顶部三角）
            // ═══════════════════════════════════════════════════════
            val arcR2 = r * 0.88f
            val pointerBaseY = cy - arcR2 + 8.dp.toPx()
            val triHalf = 5.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(cx, pointerBaseY)
                    lineTo(cx - triHalf, pointerBaseY + triHalf * 1.5f)
                    lineTo(cx + triHalf, pointerBaseY + triHalf * 1.5f)
                    close()
                },
                wingColor
            )

            // ═══════════════════════════════════════════════════════
            // 第4层：航向显示（底部）
            // ═══════════════════════════════════════════════════════
            drawContext.canvas.nativeCanvas.apply {
                // 航向数字
                val headingPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 9.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.MONOSPACE
                    alpha = 200
                }
                drawText(
                    String.format(Locale.US, "%03d°", yawDeg.toInt()),
                    cx, size.height - 8.dp.toPx(), headingPaint
                )

                // Roll/Pitch 数值
                val infoPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 6.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.MONOSPACE
                    alpha = 120
                }
                drawText(
                    String.format(Locale.US, "R:%+.0f° P:%+.0f°", smoothRoll, smoothPitch),
                    cx, size.height - 1.dp.toPx(), infoPaint
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
    telemetry: FlightCommands.Telemetry,
    onCommandSent: (String) -> Unit = {}
) {
    fun sendCmd(data: ByteArray) {
        if (service == null) return
        val prefs = service.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
        val savedJson = prefs.getString("saved_devices", "[]")
        val devicesArr = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
        var sUuid: UUID? = null; var cUuid: UUID? = null
        for (i in 0 until devicesArr.length()) {
            val obj = devicesArr.getJSONObject(i)
            if (obj.getString("uid") == deviceAddress) {
                val sStr = obj.optString("serviceUuid", "")
                val cStr = obj.optString("charUuid", "")
                if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                    sUuid = UUID.fromString(sStr); cUuid = UUID.fromString(cStr)
                }; break
            }
        }
        if (sUuid != null && cUuid != null) service.sendData(sUuid, cUuid, data)
        else service.sendData(data)
        onCommandSent(data.joinToString("") { "%02X".format(it) })
    }

    val isArmed = telemetry.armState != 0
    val armButton = if (isArmed) {
        Triple("DIS", Icons.Default.Lock, Color(0xFFFFB4AB))
    } else {
        Triple("ARM", Icons.Default.LockOpen, Color(0xFF7BDB80))
    }

    val buttons = listOf(
        armButton,
        Triple("TKOF", Icons.Default.FileUpload, Color(0xFF7BDB80)),
        Triple("LAND", Icons.Default.FileDownload, Color(0xFF7BDB80)),
        Triple("RTL", Icons.Default.Home, Color(0xFFBECABA)),
        Triple("STOP", Icons.Default.Report, Color.Red)
    )

    // 水平排列，紧凑
    Row(
        modifier = modifier
            .wrapContentWidth()
            .height(40.dp)
            .background(Color(0xFF1A1E24).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { (label, icon, color) ->
            val isEstop = label == "STOP"

            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 32.dp)
                    .clip(CircleShape)
                    .then(
                        if (isEstop) Modifier.background(Color.Red.copy(alpha = 0.15f))
                        else Modifier
                    )
                    .clickable {
                        when (label) {
                            "ARM" -> sendCmd(FlightCommands.arm())
                            "DIS" -> sendCmd(FlightCommands.disarm())
                            "TKOF" -> sendCmd(FlightCommands.takeoff(1.0f))
                            "LAND" -> sendCmd(FlightCommands.land())
                            "STOP" -> sendCmd(FlightCommands.emergencyStop())
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
                        icon, null,
                        tint = if (isEstop) Color.Red else color,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = label,
                        color = (if (isEstop) Color.Red else color).copy(alpha = 0.7f),
                        fontSize = 5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}



@Composable
fun VehicleBottomBar(
    modifier: Modifier = Modifier,
    telemetry: FlightCommands.Telemetry,
    service: BluetoothLeService?,
    deviceAddress: String?,
    history: List<String>,
    onCommandSent: (String) -> Unit = {}
) {
    var showHistory by remember { mutableStateOf(false) }

    // 按钮发送命令
    fun sendCmd(data: ByteArray) {
        if (service == null) return
        val prefs = service.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
        val savedJson = prefs.getString("saved_devices", "[]")
        val devicesArr = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
        var sUuid: UUID? = null; var cUuid: UUID? = null
        for (i in 0 until devicesArr.length()) {
            val obj = devicesArr.getJSONObject(i)
            if (obj.getString("uid") == deviceAddress) {
                val sStr = obj.optString("serviceUuid", "")
                val cStr = obj.optString("charUuid", "")
                if (sStr.isNotEmpty() && cStr.isNotEmpty()) {
                    sUuid = UUID.fromString(sStr); cUuid = UUID.fromString(cStr)
                }; break
            }
        }
        if (sUuid != null && cUuid != null) service.sendData(sUuid, cUuid, data)
        else service.sendData(data)
        onCommandSent(data.joinToString("") { "%02X".format(it) })
    }

    val isArmed = telemetry.armState != 0
    val armButton = if (isArmed) {
        Triple("DIS", Icons.Default.Lock, Color(0xFFFFB4AB))
    } else {
        Triple("ARM", Icons.Default.LockOpen, Color(0xFF7BDB80))
    }
    val buttons = listOf(
        armButton,
        Triple("TKOF", Icons.Default.FileUpload, Color(0xFF7BDB80)),
        Triple("LAND", Icons.Default.FileDownload, Color(0xFF7BDB80)),
        Triple("RTL", Icons.Default.Home, Color(0xFFBECABA)),
        Triple("STOP", Icons.Default.Report, Color.Red)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF0A0E14).copy(alpha = 0.85f))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 左侧：控制按钮（紧凑水平排列） ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                buttons.forEach { (label, icon, color) ->
                    val isEstop = label == "STOP"
                    Box(
                        modifier = Modifier
                            .size(width = 30.dp, height = 28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (isEstop) Modifier.background(Color.Red.copy(alpha = 0.15f))
                                else Modifier.background(Color.White.copy(alpha = 0.05f))
                            )
                            .clickable {
                                when (label) {
                                    "ARM" -> sendCmd(FlightCommands.arm())
                                    "DIS" -> sendCmd(FlightCommands.disarm())
                                    "TKOF" -> sendCmd(FlightCommands.takeoff(1.0f))
                                    "LAND" -> sendCmd(FlightCommands.land())
                                    "STOP" -> sendCmd(FlightCommands.emergencyStop())
                                    "RTL" -> sendCmd(FlightCommands.hover())
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                icon, null,
                                tint = if (isEstop) Color.Red else color,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = label,
                                color = (if (isEstop) Color.Red else color).copy(alpha = 0.8f),
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // ── 分隔线 ──
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // ── 中间：遥测数据 ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val q = telemetry.quaternion
                TelemetryItem("Q",
                    String.format(Locale.US, "W:%.2f X:%.2f Y:%.2f Z:%.2f",
                        q[0], q[1], q[2], q[3]))
                if (telemetry.latitude != 0.0) {
                    TelemetryItem("GPS",
                        String.format(Locale.US, "%.5f,%.5f",
                            telemetry.latitude, telemetry.longitude))
                }
            }

            // ── 右侧：最近命令 ──
            Box(modifier = Modifier.clickable { if (history.isNotEmpty()) showHistory = true }) {
                Text(
                    text = history.firstOrNull() ?: "NO CMD",
                    color = Color(0xFF7BDB80).copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }

    // 命令历史弹窗
    if (showHistory) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            containerColor = Color(0xFF10141A),
            contentColor = Color.White
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                item {
                    Text("Command History", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = Color(0xFF7BDB80), modifier = Modifier.padding(bottom = 16.dp))
                }
                items(history) { cmd ->
                    Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
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

@Composable
fun PidMiniChart(label: String, telemetry: FlightCommands.Telemetry, baseColor: Color) {
    // 使用 SideEffect 或每次重组时显式检查，避开 LaunchedEffect 的重启延迟
    val historyRoll = remember { mutableStateListOf<Float>() }
    val historyPitch = remember { mutableStateListOf<Float>() }
    val historyYaw = remember { mutableStateListOf<Float>() }

    // 记录上一笔数据的哈希，用于严格对比是否是新数据包
    var lastHash by remember { mutableStateOf(0) }
    val currentHash = telemetry.hashCode()

    if (currentHash != lastHash) {
        SideEffect {
            historyRoll.add(telemetry.rollPID[0]); if (historyRoll.size > 50) historyRoll.removeAt(0)
            historyPitch.add(telemetry.pitchPID[0]); if (historyPitch.size > 50) historyPitch.removeAt(0)
            historyYaw.add(telemetry.yawPID[0]); if (historyYaw.size > 50) historyYaw.removeAt(0)
            lastHash = currentHash
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            Text(
                "Roll  ${String.format(Locale.US, "% .2f", telemetry.rollPID[0])}",
                color = Color(0xFF7BDB80), // Roll - Green
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 6.sp
            )
            Text(
                "Pitch ${String.format(Locale.US, "% .2f", telemetry.pitchPID[0])}",
                color = Color(0xFF007AFF), // Pitch - Blue
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 6.sp
            )
            Text(
                "Yaw   ${String.format(Locale.US, "% .2f", telemetry.yawPID[0])}",
                color = Color(0xFFFFCC00), // Yaw - Yellow
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 6.sp
            )
        }
        Box(
            modifier = Modifier.size(width = 100.dp, height = 32.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerLine = size.height / 2f

                // Draw center axis
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(0f, centerLine),
                    end = Offset(size.width, centerLine),
                    strokeWidth = 1.dp.toPx()
                )

                fun drawHistory(history: List<Float>, color: Color) {
                    if (history.size > 1) {
                        val range = 80f
                        val path = androidx.compose.ui.graphics.Path()
                        history.forEachIndexed { i, v ->
                            val x = i * (size.width / (history.size - 1))
                            // Center symmetric scale
                            val y = centerLine - (v / range * (size.height / 2f))
                            val clampedY = y.coerceIn(0f, size.height)
                            if (i == 0) path.moveTo(x, clampedY) else path.lineTo(x, clampedY)
                        }
                        drawPath(path, color, style = Stroke(1.dp.toPx()))
                    }
                }

                drawHistory(historyRoll, Color(0xFF7BDB80)) // Roll - Green
                drawHistory(historyPitch, Color(0xFF007AFF)) // Pitch - Blue
                drawHistory(historyYaw, Color(0xFFFFCC00)) // Yaw - Yellow
            }
        }
    }
}

@Preview(showBackground = true, device = Devices.AUTOMOTIVE_1024p, widthDp = 1080, heightDp = 480)
@Composable
fun GreetingPreview() {
    AfireflyTheme {
        VehicleScreen(null, "Device Name", "00:11:22:33:44:55")
    }
}


