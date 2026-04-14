package io.github.fireflyest.afirefly

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import io.github.fireflyest.afirefly.ui.theme.AfireflyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Device(
    val name: String,
    val subtitle: String,
    val uid: String,
    val status: String,
    val signal: String,
    val battery: String,
    val locked: Boolean,
    val gps: Boolean,
    val serviceUuid: String? = null,
    val charUuid: String? = null
)


@Composable
fun MainScreen() {
    // use system color scheme (dark/light) via MaterialTheme
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val view = LocalView.current
    val ctx = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val devices = remember {
        mutableStateListOf<Device>()
    }
    var selectedDeviceUid by remember { mutableStateOf<String?>(null) }

    val quickCommands = remember {
        mutableStateListOf<String>()
    }

    var bluetoothService by remember { mutableStateOf<BluetoothLeService?>(null) }
    val logs = remember { mutableStateListOf<String>() }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as BluetoothLeService.LocalBinder
                bluetoothService = binder.getService()
                bluetoothService?.initialize()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bluetoothService = null
            }
        }
    }

    LaunchedEffect(Unit) {
        val intent = Intent(ctx, BluetoothLeService::class.java)
        ctx.bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
    }

    val serviceState by (bluetoothService?.connectionState ?: MutableStateFlow(BluetoothLeService.STATE_DISCONNECTED)).collectAsState()

    // Load saved devices and quick commands on startup
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
        val savedJson = prefs.getString("saved_devices", null)
        if (savedJson != null) {
            try {
                val jsonArray = JSONArray(savedJson)
                val loadedDevices = mutableListOf<Device>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    loadedDevices.add(Device(
                        name = obj.getString("name"),
                        subtitle = obj.getString("subtitle"),
                        uid = obj.getString("uid"),
                        status = "OFFLINE", // Always start as offline
                        signal = obj.optString("signal", "--"),
                        battery = obj.optString("battery", "--"),
                        locked = obj.optBoolean("locked", false),
                        gps = obj.optBoolean("gps", false),
                        serviceUuid = if (obj.isNull("serviceUuid")) null else obj.optString("serviceUuid"),
                        charUuid = if (obj.isNull("charUuid")) null else obj.optString("charUuid")
                    ))
                }
                devices.clear()
                devices.addAll(loadedDevices)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val savedQuicks = prefs.getString("quick_commands", null)
        if (savedQuicks != null) {
            try {
                val jsonArray = JSONArray(savedQuicks)
                quickCommands.clear()
                for (i in 0 until jsonArray.length()) {
                    quickCommands.add(jsonArray.getString(i))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Save a single device to permanent storage
    fun saveDevice(device: Device) {
        scope.launch {
            val prefs = ctx.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
            val savedJson = prefs.getString("saved_devices", "[]")
            val jsonArray = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
            
            val newObj = JSONObject().apply {
                put("name", device.name)
                put("subtitle", device.subtitle)
                put("uid", device.uid)
                put("signal", device.signal)
                put("battery", device.battery)
                put("locked", device.locked)
                put("gps", device.gps)
                put("serviceUuid", device.serviceUuid)
                put("charUuid", device.charUuid)
            }

            // Find if already exists and update, else add
            var found = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("uid") == device.uid) {
                    jsonArray.put(i, newObj)
                    found = true
                    break
                }
            }
            if (!found) {
                jsonArray.put(newObj)
            }
            
            prefs.edit().putString("saved_devices", jsonArray.toString()).apply()
            
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logs.add("$timestamp INFO: Device saved: ${device.name}")
        }
    }

    // Save a quick command to permanent storage
    fun saveQuickCommand(command: String) {
        if (command.isBlank()) return
        scope.launch {
            if (!quickCommands.contains(command)) {
                quickCommands.add(0, command)
                val prefs = ctx.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
                val jsonArray = JSONArray(quickCommands)
                prefs.edit().putString("quick_commands", jsonArray.toString()).apply()

                Toast.makeText(ctx, "指令已保存 (Command saved)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "指令已存在 (Command already exists)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Remove a quick command from permanent storage
    fun removeQuickCommand(command: String) {
        scope.launch {
            if (quickCommands.remove(command)) {
                val prefs = ctx.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
                val jsonArray = JSONArray(quickCommands)
                prefs.edit().putString("quick_commands", jsonArray.toString()).apply()

                Toast.makeText(ctx, "指令已删除 (Command removed)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Track previous state to avoid redundant logs on startup
    var previousServiceState by remember { mutableStateOf<Int?>(null) }
    
    // Global Hex toggle state - can be lifted or kept here if it affects both display and sending
    var isHexGlobal by remember { mutableStateOf(false) }

    LaunchedEffect(bluetoothService, isHexGlobal) {
        bluetoothService?.receivedData?.collect { data ->
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val displayData = if (isHexGlobal) {
                data.joinToString("") { "%02X ".format(it) }
            } else {
                String(data, Charsets.UTF_8)
            }
            logs.add("$timestamp RX: $displayData")
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val info = result.data?.toConnectedDeviceInfo() ?: return@rememberLauncherForActivityResult
            val newDevice = info.toMainDevice()
            
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logs.add("$timestamp INFO: Device discovered: ${newDevice.name} [${newDevice.uid}]")

            val idx = devices.indexOfFirst { it.uid == newDevice.uid }
            if (idx >= 0) {
                devices[idx] = newDevice
            } else {
                devices.add(0, newDevice)
            }
            // Removed automatic save here
            // Auto-select the newly added device
            selectedDeviceUid = newDevice.uid
            // Automatically connect to the scanned device
            bluetoothService?.connect(newDevice.uid)
        }
    }

    // Move device in list
    fun moveDevice(uid: String, up: Boolean) {
        val idx = devices.indexOfFirst { it.uid == uid }
        if (idx == -1) return
        val newIdx = if (up) idx - 1 else idx + 1
        if (newIdx in 0 until devices.size) {
            val temp = devices[idx]
            devices.removeAt(idx)
            devices.add(newIdx, temp)
        }
    }

    // Long press to remove device from UI and local storage
    fun removeDevice(uid: String) {
        val idx = devices.indexOfFirst { it.uid == uid }
        if (idx >= 0) {
            val deviceToRemove = devices[idx]
            // If the device is currently connected/linking, disconnect it first
            if (deviceToRemove.status == "ONLINE" || deviceToRemove.status == "LINKING") {
                bluetoothService?.disconnect()
            }
            
            devices.removeAt(idx)
            if (selectedDeviceUid == uid) {
                selectedDeviceUid = if (devices.isNotEmpty()) devices[0].uid else null
            }
            
            // Also remove from permanent storage
            scope.launch {
                val prefs = ctx.getSharedPreferences("afirefly_prefs", Context.MODE_PRIVATE)
                val savedJson = prefs.getString("saved_devices", "[]")
                val jsonArray = try { JSONArray(savedJson) } catch (e: Exception) { JSONArray() }
                val newArray = JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.getString("uid") != uid) {
                        newArray.put(obj)
                    }
                }
                prefs.edit().putString("saved_devices", newArray.toString()).apply()
                
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                logs.add("$timestamp INFO: Device removed: ${deviceToRemove.name}")
            }
        }
    }

    // Update device status based on Bluetooth service state
    LaunchedEffect(serviceState) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        when (serviceState) {
            BluetoothLeService.STATE_CONNECTED -> {
                logs.add("$timestamp INFO: GATT connection established.")
                devices.forEachIndexed { index, device ->
                    if (device.uid == selectedDeviceUid || device.status == "LINKING") {
                        devices[index] = device.copy(status = "ONLINE")
                    }
                }
            }
            BluetoothLeService.STATE_CONNECTING -> {
                logs.add("$timestamp INFO: Attempting to connect...")
                devices.forEachIndexed { index, device ->
                    if (device.uid == selectedDeviceUid) {
                        devices[index] = device.copy(status = "LINKING")
                    }
                }
            }
            BluetoothLeService.STATE_DISCONNECTED -> {
                // Only log if it's a real transition from a connected/connecting state, not at startup
                if (previousServiceState != null && previousServiceState != BluetoothLeService.STATE_DISCONNECTED) {
                    logs.add("$timestamp INFO: GATT disconnected.")
                }
                devices.forEachIndexed { index, device ->
                    if (device.uid == selectedDeviceUid || device.status == "ONLINE") {
                        devices[index] = device.copy(status = "OFFLINE")
                    }
                }
            }
        }
        previousServiceState = serviceState
    }

    // set system bars to match app surface so status/navigation areas blend
    if (!view.isInEditMode) {
        val systemUiController = rememberSystemUiController()
        SideEffect {
            systemUiController.setStatusBarColor(color = colors.surface, darkIcons = !isDark)
            systemUiController.setNavigationBarColor(color = colors.surface.copy(alpha = 0.9f), darkIcons = !isDark)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Box(modifier = Modifier
            .fillMaxSize()
            // subtle grid overlay like the HTML background
            .drawBehind {
                val step = 40.dp.toPx()
                val stroke = 1.dp.toPx()
                val c = colors.primary.copy(alpha = 0.03f)
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        c,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = stroke
                    )
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        c,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = stroke
                    )
                    y += step
                }
            }) {

            Column(modifier = Modifier.fillMaxSize()) {
                TopHeader(
                    onOpenScan = { scanLauncher.launch(Intent(ctx, ScanActivity::class.java)) },
                    bluetoothService = bluetoothService,
                    selectedDeviceUid = selectedDeviceUid,
                    devices = devices,
                    onDeviceSave = { saveDevice(it) }
                )
                MainContent(
                    modifier = Modifier.weight(1f), 
                    devices = devices, 
                    logs = logs,
                    selectedDeviceUid = selectedDeviceUid,
                    onDeviceSelected = { selectedDeviceUid = it },
                    onDeviceSave = { saveDevice(it) },
                    onDeviceRemove = { removeDevice(it) },
                    onDeviceConnect = { 
                        selectedDeviceUid = it
                        bluetoothService?.connect(it) 
                    },
                    onDeviceDisconnect = { bluetoothService?.disconnect() },
                    onDeviceMove = { uid, up -> moveDevice(uid, up) }
                )
                FooterBar(
                    quickCommands = quickCommands,
                    isHex = isHexGlobal,
                    onHexChanged = { isHexGlobal = it },
                    onSendMessage = { msg ->
                        val currentDevice = devices.find { it.uid == selectedDeviceUid }
                        val sUuidStr = currentDevice?.serviceUuid
                        val cUuidStr = currentDevice?.charUuid
                        
                        val dataToSend = if (isHexGlobal) {
                            try {
                                msg.filter { !it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            } catch (e: Exception) {
                                msg.toByteArray()
                            }
                        } else {
                            msg.toByteArray()
                        }

                        if (sUuidStr != null && cUuidStr != null) {
                            bluetoothService?.sendData(UUID.fromString(sUuidStr), UUID.fromString(cUuidStr), dataToSend)
                        } else {
                            bluetoothService?.sendData(dataToSend)
                        }
                        
                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        logs.add("$timestamp TX: $msg")
                    },
                    onSaveCommand = { 
                        saveQuickCommand(it) 
                    },
                    onRemoveCommand = { removeQuickCommand(it) }
                )
            }
        }
    }
}

// Slim outlined field with reduced vertical padding. Used for compact top/bottom inputs.
@Composable
fun SlimOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {},
    singleLine: Boolean = false,
    // support multiline behavior: minLines and maxLines control sizing; when singleLine=false
    // the field will wrap content and grow as text expands (unless caller forces height).
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    // avoid imposing a large lineHeight here; vertical centering for single-line inputs
    // is handled by the container. For multi-line inputs we align top and add small padding.
    textStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface // Force use of onSurface color for readability
    ),
    shape: androidx.compose.foundation.shape.CornerBasedShape = RoundedCornerShape(8.dp),
    borderColor: Color,
    backgroundColor: Color
    ,
    // Optional callback to report measured TextLayoutResult so callers can react to wrapping
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    // Use a box whose vertical alignment depends on whether we should center the first line.
    // Center when singleLine or when there is no newline yet; switch to top-start after user
    // inserts a newline so the field grows from the top.
    val useCenter = singleLine || !value.contains('\n')
    Box(
        modifier = modifier
            .border(1.dp, borderColor, shape)
            .background(backgroundColor, shape)
            // only horizontal padding here; vertical alignment handled by contentAlignment below
            .padding(horizontal = 10.dp),
        contentAlignment = if (useCenter) Alignment.CenterStart else Alignment.TopStart
    ) {
        val mergedStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            textStyle = mergedStyle,
            cursorBrush = SolidColor(borderColor),
            // allow multi-line to wrap content; single-line will be vertically centered by
            // the decoration box filling available height
            modifier = Modifier.then(if (singleLine) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().wrapContentHeight()),

            decorationBox = { innerTextField ->
                // For single-line fields we center vertically by filling the height; for multi-line
                // we let the inner box wrap its height and align top with small padding so text starts
                // near the top and the outer caller can grow the box as content expands.
                val baseInner = if (useCenter) {
                    // don't expand to parent's max height when centered; keep reasonable min height
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 6.dp)
                }
                // if caller requested a minLines > 1, ensure a minimum height when not centered
                val innerModifier = if (!useCenter && minLines > 1) {
                    baseInner.heightIn(min = (minLines * 20).dp)
                } else baseInner
                val alignment = if (useCenter) Alignment.CenterStart else Alignment.TopStart
                Box(modifier = innerModifier, contentAlignment = alignment) {
                        if (value.isEmpty()) {
                            placeholder()
                        }
                        innerTextField()
                    }
            }
        )
    }
}

@Composable
fun TopHeader(
    onOpenScan: () -> Unit,
    bluetoothService: BluetoothLeService?,
    selectedDeviceUid: String?,
    devices: List<Device>,
    onDeviceSave: (Device) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // Collapsible capsule search: shows a small pill with icon+label, expands to full search field on tap
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Dropdown states for Service and Characteristic
    var serviceExpanded by remember { mutableStateOf(false) }
    var charExpanded by remember { mutableStateOf(false) }

    // Selected UUIDs
    var selectedServiceUuid by remember(selectedDeviceUid) { 
        mutableStateOf<String?>(devices.find { it.uid == selectedDeviceUid }?.serviceUuid) 
    }
    var selectedCharUuid by remember(selectedDeviceUid) { 
        mutableStateOf<String?>(devices.find { it.uid == selectedDeviceUid }?.charUuid) 
    }
    
    // Internal cache for services to prevent transient clearing
    var cachedServices by remember(selectedDeviceUid) { mutableStateOf<List<android.bluetooth.BluetoothGattService>>(emptyList()) }
    
    // Collect the services from the bluetooth service
    val latestServices by (bluetoothService?.discoveredServices ?: MutableStateFlow(emptyList())).collectAsState()

    // Update the cache only if new services are found
    LaunchedEffect(latestServices) {
        if (latestServices.isNotEmpty()) {
            cachedServices = latestServices
        }
    }

    val currentService = remember(selectedServiceUuid, cachedServices) {
        cachedServices.find { it.uuid.toString() == selectedServiceUuid }
    }
    val characteristics = remember(currentService) {
        currentService?.characteristics ?: emptyList()
    }

    // Helper to get short hex from full UUID string
    fun getShortUuid(uuid: String?): String? = uuid?.substring(4, 8)?.uppercase()

    fun getServiceUsage(uuid: String?): String {
        val short = getShortUuid(uuid) ?: return "未知服务"
        return when (short) {
            "1800" -> "通用访问"
            "1811" -> "闹钟通知"
            "1815" -> "自动化IO"
            "180F" -> "电池数据"
            "183B" -> "二元传感器"
            "1810" -> "血压"
            "181B" -> "身体组成"
            "181E" -> "设备绑定管理"
            "181F" -> "动态血糖检测"
            "1805" -> "当前时间"
            "1818" -> "骑行能量"
            "1816" -> "循环速度和节奏"
            "180A" -> "设备信息"
            "183C" -> "应急配置"
            "181A" -> "环境传感"
            "1826" -> "健康设备"
            "1801" -> "通用属性"
            "1808" -> "葡萄糖"
            "1809" -> "温度计"
            "180D" -> "心率"
            "1823" -> "HTTP代理"
            "1812" -> "HID设备"
            "1802" -> "即时闹钟"
            "1821" -> "室内定位"
            "183A" -> "胰岛素给药"
            "1820" -> "互联网协议支持"
            "1803" -> "连接丢失"
            "1819" -> "定位及导航"
            "1827" -> "节点配置"
            "1828" -> "节点代理"
            "1807" -> "夏令时更改"
            "1825" -> "对象传输"
            "180E" -> "手机报警状态"
            "1822" -> "脉搏血氧计"
            "1829" -> "重连配置"
            "1806" -> "参照时间更新"
            "1814" -> "跑步速度和节奏"
            "1813" -> "扫描参数"
            "1824" -> "传输发现"
            "1804" -> "发送功率"
            "181C" -> "用户数据"
            "181D" -> "体重秤"
            else -> "自定义服务"
        }
    }

    fun getCharacteristicUsage(uuid: String?): String {
        val short = getShortUuid(uuid) ?: return "未知功能"
        return when (short) {
            "2A7E" -> "有氧心律下限"
            "2A84" -> "有氧心率上限"
            "2A7F" -> "有氧运动阈值"
            "2A80" -> "年龄"
            "2A5A" -> "集合"
            "2A43" -> "报警类别ID"
            "2A42" -> "报警类别ID位掩码"
            "2A06" -> "报警等级"
            "2A44" -> "报警通知控制点"
            "2A3F" -> "报警状态"
            "2AB3" -> "海拔"
            "2A81" -> "无氧心率下限"
            "2A82" -> "无氧心率上限"
            "2A83" -> "无氧运动阈值"
            "2A58" -> "模拟"
            "2A59" -> "模拟输出"
            "2A73" -> "视风向"
            "2A72" -> "视风速"
            "2A01" -> "外观"
            "2AA3" -> "气压趋势"
            "2A19" -> "电池电量"
            "2A1B" -> "电池电量状态"
            "2A1A" -> "电池电量状态"
            "2A49" -> "血压功能"
            "2A35" -> "血压测量"
            "2A9B" -> "身体组成特征"
            "2A9C" -> "身体组成测量"
            "2A38" -> "人体感应器位置"
            "2AA4" -> "绑定管理控制点"
            "2AA5" -> "绑定管理功能"
            "2A22" -> "启动键盘输入报告"
            "2A32" -> "启动键盘输出报告"
            "2A33" -> "启动鼠标输入报告"
            "2B2B" -> "BSS控制点"
            "2B2C" -> "BSS回应"
            "2AA8" -> "CGM功能"
            "2AA7" -> "CGM测量"
            "2AAB" -> "CGM会话运行时间"
            "2AAA" -> "CGM会话开始时间"
            "2AAC" -> "CGM特定操作控制点"
            "2AA9" -> "CGM状态"
            "2ACE" -> "交叉训练员数据"
            "2A5C" -> "CSC功能"
            "2A5B" -> "CSC测量"
            "2A2B" -> "当前时间"
            "2A66" -> "骑行能量控制点"
            "2A65" -> "骑行能量功能"
            "2A63" -> "骑行能量测量"
            "2A64" -> "骑行能量矢量"
            "2A99" -> "数据库更改增量"
            "2A85" -> "出生日期"
            "2A86" -> "阈值评估日期"
            "2A08" -> "日期时间"
            "2AED" -> "UTC时间"
            "2A0A" -> "日期时间天"
            "2A09" -> "星期几"
            "2A7D" -> "描述符值已更改"
            "2A7B" -> "露点温度"
            "2A56" -> "数字"
            "2A57" -> "数字输出"
            "2A0D" -> "日光节约时间偏移"
            "2A6C" -> "海拔"
            "2A87" -> "电子邮件地址"
            "2B2D" -> "突发事件ID"
            "2B2E" -> "突发事件内容"
            "2A0B" -> "具体时间100"
            "2A0C" -> "具体时间256"
            "2A88" -> "脂肪燃烧心率下限"
            "2A89" -> "脂肪燃烧心率上限"
            "2A26" -> "固件修订字符"
            "2A8A" -> "名字"
            "2AD9" -> "健身设备控制点"
            "2ACC" -> "健身设备功能"
            "2ADA" -> "健身设备状态"
            "2A8B" -> "五区心率限制"
            "2AB2" -> "楼层号"
            "2AA6" -> "中央地址解析"
            "2A00" -> "设备名称"
            "2A04" -> "外围设备首选连接参数"
            "2A02" -> "周边隐私标志"
            "2A03" -> "重新连接地址"
            "2A05" -> "服务已更改"
            "2A8C" -> "性别"
            "2A51" -> "葡萄糖功能"
            "2A18" -> "血糖测量"
            "2A34" -> "葡萄糖测量环境"
            "2A74" -> "阵风系数"
            "2A27" -> "硬件修订字符"
            "2A39" -> "心率控制点"
            "2A8D" -> "最大心率"
            "2A37" -> "心率测量"
            "2A7A" -> "热度指数"
            "2A8E" -> "高度"
            "2A4C" -> "HID控制点"
            "2A4A" -> "HID信息"
            "2A8F" -> "臀围"
            "2ABA" -> "HTTP控制点"
            "2AB9" -> "HTTP实体主体"
            "2AB7" -> "HTTP头"
            "2AB8" -> "HTTP状态码"
            "2ABB" -> "HTTPS安全性"
            "2A6F" -> "湿度"
            "2B22" -> "IDD通告状态"
            "2B25" -> "IDD命令控制点"
            "2B26" -> "IDD命令数据"
            "2B23" -> "IDD功能"
            "2B28" -> "IDD历史数据"
            "2B27" -> "IDD记录访问控制点"
            "2B21" -> "IDD状态"
            "2B20" -> "IDD状态已更改"
            "2B24" -> "IDD状态读取器控制点"
            "2A2A" -> "IEEE法则认证数据列表"
            "2AD2" -> "室内自行车数据"
            "2AAD" -> "室内定位配置"
            "2A36" -> "中间的气囊压力"
            "2A1E" -> "中间的温度"
            "2A77" -> "辐照度"
            "2AA2" -> "语言"
            "2A90" -> "姓"
            "2AAE" -> "纬度"
            "2A6B" -> "LN控制点"
            "2A6A" -> "LN功能"
            "2AB1" -> "当地东部坐标"
            "2AB0" -> "当地北部坐标"
            "2A0F" -> "当地时间信息"
            "2A67" -> "位置和速度特征"
            "2AB5" -> "地点名称"
            "2AAF" -> "经度"
            "2A2C" -> "磁偏角"
            "2AA0" -> "磁通密度 – 2D"
            "2AA1" -> "磁通密度 – 3D"
            "2A29" -> "制造商名称字符"
            "2A91" -> "推荐最大心率"
            "2A21" -> "测量间隔"
            "2A24" -> "型号字符"
            "2A68" -> "导航"
            "2A3E" -> "网络可用性"
            "2A46" -> "新警报"
            "2AC5" -> "对象动作控制点"
            "2AC8" -> "对象已更改"
            "2AC1" -> "对象首先创建"
            "2AC3" -> "对象ID"
            "2AC2" -> "上次修改的对象"
            "2AC6" -> "对象列表控制点"
            "2AC7" -> "对象列表过滤器"
            "2ABE" -> "对象名称"
            "2AC4" -> "对象属性"
            "2AC0" -> "对象大小"
            "2ABF" -> "对象类型"
            "2ABD" -> "OTS功能"
            "2A5F" -> "PLX连续测量特性"
            "2A60" -> "PLX功能"
            "2A5E" -> "PLX抽查检查"
            "2A50" -> "即插即用ID"
            "2A75" -> "花粉浓度"
            "2A2F" -> "位置2D"
            "2A30" -> "位置3D"
            "2A69" -> "位置质量"
            "2A6D" -> "压力"
            "2A4E" -> "协议模式"
            "2A62" -> "脉搏血氧饱和度控制点"
            "2A78" -> "雨量"
            "2B1D" -> "RC功能"
            "2B1E" -> "RC设置"
            "2B1F" -> "重新连接配置控制点"
            "2A52" -> "记录访问控制点"
            "2A14" -> "参考时间信息"
            "2B37" -> "注册用户特征"
            "2A3A" -> "可移动的"
            "2A4D" -> "报告"
            "2A4B" -> "报告地图"
            "2AC9" -> "仅可解析的私有地址"
            "2A92" -> "静息心率"
            "2A40" -> "铃声控制点"
            "2A41" -> "铃声设置"
            "2AD1" -> "桨手数据"
            "2A54" -> "RSC功能"
            "2A53" -> "RSC测量"
            "2A55" -> "SC控制点"
            "2A4F" -> "扫描间隔窗口"
            "2A31" -> "扫描刷新"
            "2A3C" -> "科学温度"
            "2A10" -> "次要时区"
            "2A5D" -> "传感器位置"
            "2A25" -> "序列号字符"
            "2A3B" -> "所需服务"
            "2A28" -> "软件修订版字符"
            "2A93" -> "运动类型"
            "2AD0" -> "攀登楼梯数"
            "2ACF" -> "攀登者步数"
            "2A3D" -> "字符串"
            "2AD7" -> "支持的心率范围"
            "2AD5" -> "支持的倾斜范围"
            "2A47" -> "支持的新警报类别"
            "2AD8" -> "支持的功率范围"
            "2AD6" -> "支持的电阻水平范围"
            "2AD4" -> "支持的速度范围"
            "2A48" -> "支持的未读警报类别"
            "2A23" -> "系统编号"
            "2ABC" -> "TDS控制点"
            "2A6E" -> "温度"
            "2A1F" -> "温度摄氏"
            "2A20" -> "温度华氏度"
            "2A1C" -> "温度测量"
            "2A1D" -> "温度类型"
            "2A94" -> "三区心率限制"
            "2A12" -> "时间精度"
            "2A15" -> "时间广播"
            "2A13" -> "时间来源"
            "2A16" -> "时间更新控制点"
            "2A17" -> "时间更新状态"
            "2A11" -> "夏令时"
            "2A0E" -> "时区"
            "2AD3" -> "训练状况"
            "2ACD" -> "跑步机数据"
            "2A71" -> "真风向"
            "2A70" -> "真风速"
            "2A95" -> "两区心率限制"
            "2A07" -> "发射功率等级"
            "2AB4" -> "不确定"
            "2A45" -> "未读警报状态"
            "2AB6" -> "URI链接"
            "2A9F" -> "用户控制点"
            "2A9A" -> "用户索引"
            "2A76" -> "紫外线指数"
            "2A96" -> "最大摄氧量"
            "2A97" -> "腰围"
            "2A98" -> "重量"
            "2A9D" -> "重量测量"
            "2A9E" -> "体重秤功能"
            "2A79" -> "风寒系数"
            else -> "自定义功能"
        }
    }

    fun getProperties(p: Int): String {
        val builder = mutableListOf<String>()
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0) builder.add("广播")
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ) != 0) builder.add("读取")
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) builder.add("无回应写入")
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) builder.add("写入")
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) builder.add("反馈信息")
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) builder.add("指示")
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) builder.add("带签名写入")
        if ((p and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0) builder.add("扩展属性")
        return if (builder.isEmpty()) "" else "[${builder.joinToString(",")}]"
    }

    Row(modifier = Modifier
        .statusBarsPadding()
        .fillMaxWidth()
        .background(colors.surface)
        .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // container with animated content size so collapsed width is just the capsule size
        Box(modifier = Modifier
            .weight(1f)
            .animateContentSize()) {
            if (!expanded) {
                // collapsed capsule: only wrap content width so it stays small
                Row(modifier = Modifier
                    .wrapContentWidth()
                    .height(40.dp)
                    .clickable { expanded = true }
                    .background(colors.surfaceVariant, shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "search", tint = colors.outline)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search", color = colors.outline, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            } else {
                // expanded: real input takes full width of the container
                var searchText by remember { mutableStateOf("") }
                SlimOutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("FILTER...", color = colors.onSurface.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 14.sp),
                    borderColor = colors.outline,
                    backgroundColor = colors.surface,
                    onTextLayout = {}
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Service Dropdown
        Box {
            TextButton(
                onClick = { serviceExpanded = true },
                modifier = Modifier.height(40.dp).width(72.dp), // widened to fit hex
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = colors.surfaceVariant,
                    contentColor = if (selectedServiceUuid != null) colors.primary else colors.onSurfaceVariant
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text(
                    text = getShortUuid(selectedServiceUuid) ?: "SRV",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selectedServiceUuid != null) FontWeight.Bold else FontWeight.Normal
                )
            }
            DropdownMenu(expanded = serviceExpanded, onDismissRequest = { serviceExpanded = false }) {
                if (cachedServices.isEmpty()) {
                    DropdownMenuItem(text = { Text("No Services", fontSize = 12.sp) }, onClick = { serviceExpanded = false })
                }
                cachedServices.forEach { srv ->
                    val uuidStr = srv.uuid.toString()
                    val shortUuid = getShortUuid(uuidStr)!!
                    val usage = getServiceUsage(uuidStr)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("0x$shortUuid", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text(usage, fontSize = 10.sp, color = colors.outline)
                            }
                        },
                        onClick = {
                            selectedServiceUuid = uuidStr
                            serviceExpanded = false
                            // Auto-save selection to device
                            devices.find { it.uid == selectedDeviceUid }?.let { dev ->
                                onDeviceSave(dev.copy(serviceUuid = uuidStr))
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Characteristic Dropdown
        Box {
            TextButton(
                onClick = { charExpanded = true },
                modifier = Modifier.height(40.dp).width(72.dp), // widened to fit hex
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = colors.surfaceVariant,
                    contentColor = if (selectedCharUuid != null) colors.primary else colors.onSurfaceVariant
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text(
                    text = getShortUuid(selectedCharUuid) ?: "CHR",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selectedCharUuid != null) FontWeight.Bold else FontWeight.Normal
                )
            }
            DropdownMenu(expanded = charExpanded, onDismissRequest = { charExpanded = false }) {
                if (characteristics.isEmpty()) {
                    val msg = if (selectedServiceUuid == null) "Select SRV" else "No Chars"
                    DropdownMenuItem(text = { Text(msg, fontSize = 12.sp) }, onClick = { charExpanded = false })
                }
                characteristics.forEach { chr ->
                    val uuidStr = chr.uuid.toString()
                    val shortUuid = getShortUuid(uuidStr)!!
                    val usage = getCharacteristicUsage(uuidStr)
                    val props = getProperties(chr.properties)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("0x$shortUuid", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("$usage $props", fontSize = 10.sp, color = colors.outline)
                            }
                        },
                        onClick = {
                            selectedCharUuid = uuidStr
                            charExpanded = false
                            // Auto-save selection to device
                            devices.find { it.uid == selectedDeviceUid }?.let { dev ->
                                onDeviceSave(dev.copy(serviceUuid = selectedServiceUuid, charUuid = uuidStr))
                            }
                            // Enable notifications for the selected characteristic
                            if (selectedServiceUuid != null) {
                                bluetoothService?.enableNotifications(UUID.fromString(selectedServiceUuid), UUID.fromString(uuidStr))
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        // make bluetooth icon open ScanActivity when tapped
        IconButton(onClick = onOpenScan, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "bt",
                tint = colors.primary,
            )
        }
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier, 
    devices: List<Device>, 
    logs: List<String>,
    selectedDeviceUid: String?,
    onDeviceSelected: (String) -> Unit,
    onDeviceSave: (Device) -> Unit,
    onDeviceRemove: (String) -> Unit,
    onDeviceConnect: (String) -> Unit,
    onDeviceDisconnect: () -> Unit,
    onDeviceMove: (String, Boolean) -> Unit
) {
    Column(modifier = modifier
        .fillMaxSize()
        // remove bottom padding so logs can reach the footer without an extra gap
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)) {
        // Device selector (horizontal scroll)
        DeviceSelector(
            devices = devices, 
            selectedDeviceUid = selectedDeviceUid,
            onDeviceSelected = onDeviceSelected,
            onDeviceSave = onDeviceSave,
            onDeviceRemove = onDeviceRemove,
            onDeviceConnect = onDeviceConnect,
            onDeviceDisconnect = onDeviceDisconnect,
            onDeviceMove = onDeviceMove
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Terminal area (header + log + footer info)
        TerminalView(modifier = Modifier.weight(1f), logs = logs)
    }
}

@Composable
fun DeviceSelector(
    devices: List<Device>,
    selectedDeviceUid: String?,
    onDeviceSelected: (String) -> Unit,
    onDeviceSave: (Device) -> Unit,
    onDeviceRemove: (String) -> Unit,
    onDeviceConnect: (String) -> Unit,
    onDeviceDisconnect: () -> Unit,
    onDeviceMove: (String, Boolean) -> Unit
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(devices, key = { it.uid }) { d ->
                DeviceCard(
                    device = d, 
                    isSelected = d.uid == selectedDeviceUid,
                    onClick = { onDeviceSelected(d.uid) },
                    onSave = { onDeviceSave(d) },
                    onRemove = { onDeviceRemove(d.uid) },
                    onConnect = { onDeviceConnect(d.uid) },
                    onDisconnect = onDeviceDisconnect,
                    onMove = { up -> onDeviceMove(d.uid, up) },
                    isFirst = devices.firstOrNull()?.uid == d.uid,
                    isLast = devices.lastOrNull()?.uid == d.uid
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeviceCard(
    device: Device, 
    isSelected: Boolean, 
    onClick: () -> Unit, 
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onMove: (Boolean) -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val isOnline = device.status == "ONLINE"
    val isLinking = device.status == "LINKING"

    var showMenu by remember { mutableStateOf(false) }

    // Selection feedback: color and border intensity
    val containerColor = when {
        isOnline -> colors.primary.copy(alpha = 0.25f)
        isLinking -> colors.tertiary.copy(alpha = 0.20f)
        else -> colors.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = when {
        isOnline -> colors.primary.copy(alpha = 0.8f)
        isLinking -> colors.tertiary.copy(alpha = 0.7f)
        else -> colors.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .width(192.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier
            .border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)) {

            // Dropdown Menu for Actions
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (isOnline || isLinking) {
                    DropdownMenuItem(
                        text = { Text("断开 (Disconnect)") },
                        onClick = { onDisconnect(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = null) }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("连接 (Connect)") },
                        onClick = { onConnect(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.BluetoothConnected, contentDescription = null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("保存设备 (Save Device)") },
                    onClick = { onSave(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                )
                if (!isFirst) {
                    DropdownMenuItem(
                        text = { Text("向左移动 (Move Left)") },
                        onClick = { onMove(true); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                    )
                }
                if (!isLast) {
                    DropdownMenuItem(
                        text = { Text("向右移动 (Move Right)") },
                        onClick = { onMove(false); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.ArrowForward, contentDescription = null) }
                    )
                }
                Divider()
                DropdownMenuItem(
                    text = { Text("删除 (Remove)", color = colors.error) },
                    onClick = { onRemove(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error) }
                )
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name, 
                        color = if (isOnline) colors.primary else colors.onSurface, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.uid, 
                        color = colors.outline, 
                        fontFamily = FontFamily.Monospace, 
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // status dot: solid when selected, hollow with border when not selected
                val dotColor = when {
                    isOnline -> colors.primary
                    isLinking -> colors.tertiary
                    else -> colors.outline
                }
                Box(modifier = Modifier
                    .size(10.dp)
                    .then(
                        if (isSelected) {
                            Modifier.background(dotColor, shape = RoundedCornerShape(10.dp))
                        } else {
                            Modifier.border(1.dp, dotColor, shape = RoundedCornerShape(10.dp))
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // icons row: lock, battery, signal, gps (grouped with consistent spacing)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // lock/unlock group
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lockIcon = if (device.locked) Icons.Default.Lock else Icons.Default.LockOpen
                    Icon(imageVector = lockIcon, contentDescription = if (device.locked) "locked" else "unlocked", tint = if (device.locked) colors.error else colors.primary, modifier = Modifier.size(16.dp))
                }
                // gps group
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "gps", tint = if (device.gps) colors.primary else colors.outline, modifier = Modifier.size(16.dp))
                }
                // battery group
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.BatteryFull, contentDescription = "battery", tint = colors.onSurface, modifier = Modifier.size(16.dp))
                    Text(text = device.battery, color = colors.onSurface, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                // signal group
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.SignalCellular4Bar, contentDescription = "signal", tint = colors.primary, modifier = Modifier.size(16.dp))
                    Text(text = device.signal, color = colors.primary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TerminalView(modifier: Modifier = Modifier, logs: List<String>) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize()) {
        // logs area
        LazyColumn(modifier = Modifier
            .weight(1f)
            .background(colors.surface)
            .padding(vertical = 0.dp, horizontal = 0.dp)) {
            items(logs) { entry ->
                LogRow(entry)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun LogRow(text: String) {
    // improved colouring: parse time, label token, and message; highlight keywords and animate cursor
    val timeColor = Color(0xFF007AFF)
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val onSurface = colors.onSurface
    val infinite = rememberInfiniteTransition()
    val blink by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing))
    )

    // match lines like: [14:02:31] LABEL: message
    // match [time] LABEL: message  -- escape only opening bracket to avoid warning
    val regex = Regex("""^(.*?)\s*([A-Z_>]+):?\s*(.*)""")
    val match = regex.matchEntire(text)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (match != null) {
            val time = match.groupValues[1]
            val label = match.groupValues[2]
            var rest = match.groupValues[3]

            // if cursor present (ends with _), strip it and render separately with blink
            val hasCursor = rest.endsWith("_")
            if (hasCursor) rest = rest.removeSuffix("_").trimEnd()

            // build annotated string: time + space + label + rest
            val annotated = buildAnnotatedString {
                withStyle(style = SpanStyle(color = timeColor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)) {
                    append(time)
                }
                append(" ")
                val labelColor = when {
                    label.contains("WARN") || label.contains("ERROR") -> colors.error
                    label.contains("KERN") || label.contains("NET") || label.contains("TELEMETRY") -> colors.primary
                    label.contains(">>") -> colors.onSurface
                    else -> colors.outline
                }
                withStyle(style = SpanStyle(color = labelColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                    append(label)
                    append(" ")
                }
                withStyle(style = SpanStyle(color = onSurface, fontFamily = FontFamily.Monospace, fontSize = 14.sp)) {
                    append(rest)
                }
            }
            Text(text = annotated)
            if (hasCursor) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "_", color = colors.primary, modifier = Modifier.alpha(blink), style = TextStyle(fontSize = 14.sp))
            }
        } else {
            Text(text = text, color = onSurface, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun FooterBar(
    quickCommands: List<String>,
    isHex: Boolean,
    onHexChanged: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit = {},
    onSaveCommand: (String) -> Unit = {},
    onRemoveCommand: (String) -> Unit = {}
) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Row(modifier = Modifier
        .navigationBarsPadding()
        .fillMaxWidth()
        .background(colors.surface.copy(alpha = 0.9f))
        .padding(6.dp), verticalAlignment = Alignment.Bottom) {
        // left toggle button (attached to input)
        // input state
        var inputText by remember { mutableStateOf("") }
        var showQuickDialog by remember { mutableStateOf(false) }

        // Hex validation: Check if input is valid hex (allowing spaces)
        val isInputValid = remember(inputText, isHex) {
            if (isHex && inputText.isNotBlank()) {
                // Remove spaces and check if remaining is even length and contains only 0-9, A-F
                val filtered = inputText.filter { !it.isWhitespace() }
                filtered.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } && filtered.length % 2 == 0
            } else {
                true
            }
        }

        val leftBg = if (isHex) colors.primary else colors.surfaceVariant
        // make the 0x label highly legible when active
        val leftTextColor = if (isHex) colors.onPrimary else colors.outline

        Box(modifier = Modifier
            .height(44.dp)
            .padding(start = 6.dp)
            .background(
                leftBg,
                shape = RoundedCornerShape(
                    topStart = 8.dp,
                    bottomStart = 8.dp,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp
                )
            )
            .clickable { onHexChanged(!isHex) }
            .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "0x",
                color = leftTextColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = if (isHex) FontWeight.Bold else FontWeight.Normal
            )
        }
        // input directly adjacent to left button, matching height and straight shared edge
        // measure wrapped line count from the text layout (so soft-wrapping increases height)
        var measuredLines by remember { mutableStateOf(1) }
        val lineCount = measuredLines.coerceAtLeast(1)
        val perLineDp = 20.dp
        val targetHeight = (44.dp + perLineDp * (lineCount - 1)).coerceAtMost(150.dp)
        val animHeight by animateDpAsState(targetHeight, animationSpec = tween(durationMillis = 220))

        // Input area takes the remaining width. The clear icon will overlay the input (aligned)
        // instead of reserving horizontal padding so the text field doesn't get squeezed.
        Box(modifier = Modifier
            .weight(1f)
            .height(animHeight)
            .padding(start = 0.dp, end = 4.dp)) {
            SlimOutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("ENTER COMMAND", color = colors.outline, fontSize = 14.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animHeight),
                onTextLayout = { layout -> measuredLines = layout.lineCount },
                // enable multi-line input so Enter creates new lines; allow up to 6 lines visually
                singleLine = false,
                minLines = 1,
                maxLines = 6,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 18.sp),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                borderColor = if (isInputValid) colors.outline else colors.error,
                backgroundColor = colors.surface
            )

            // (clear icon moved outside as sibling) no overlay here to avoid duplicate icons
        }

        // clear icon placed to the left of the Send/Add button as a sibling.
        // animate the sibling's width to 0 when hidden so it doesn't reserve space.
        val clearTargetWidth = if (inputText.isNotBlank()) 36.dp else 0.dp
        val clearWidth by animateDpAsState(targetValue = clearTargetWidth, animationSpec = tween(durationMillis = 180))
        Box(modifier = Modifier
            .width(clearWidth)
            .height(animHeight)
            .padding(horizontal = if (inputText.isNotBlank()) 4.dp else 0.dp), contentAlignment = Alignment.Center) {
            if (inputText.isNotBlank()) {
                IconButton(onClick = { inputText = "" }, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "clear", tint = colors.outline)
                }
            }
        }

        // SEND vs QUICK-ADD behaviour with animated size sync with clear icon
        val targetButtonWidth = if (inputText.isBlank()) 50.dp else 100.dp
        val animButtonWidth by animateDpAsState(targetButtonWidth, animationSpec = tween(durationMillis = 220))
        Box(
            modifier = Modifier
                .width(animButtonWidth)
                .height(44.dp) // Fixed height to match overall row alignment
                .padding(end = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = inputText.isNotBlank(), label = "send_add_crossfade") { hasText ->
                if (!hasText) {
                    IconButton(
                        onClick = { showQuickDialog = true },
                        modifier = Modifier.size(44.dp) // Maintain consistent clickable size
                    ) {
                        // outlined circular add (no fill)
                        Box(modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, colors.outline, shape = RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "add", tint = colors.primary)
                        }
                    }
                } else {
                    // Use a Box as the container for the button logic to ensure combinedClickable
                    // handles both events without the Button's internal onClick taking precedence.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp) // Explicitly set height
                            .alpha(if (isInputValid) 1f else 0.5f)
                            .background(
                                if (isInputValid) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.3f), 
                                shape = RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                enabled = isInputValid,
                                onClick = {
                                    onSendMessage(inputText)
                                    inputText = ""
                                },
                                onLongClick = {
                                    onSaveCommand(inputText)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SEND",
                            color = if (isInputValid) colors.onSurface else colors.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // quick commands bottom sheet (appears from bottom)
        if (showQuickDialog) {
            ModalBottomSheet(onDismissRequest = { showQuickDialog = false }) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)) {
                    Text("Quick Commands", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    quickCommands.forEach { cmd ->
                        // Wrap in a box or just use the combinedClickable modifier properly
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onSendMessage(cmd)
                                        showQuickDialog = false
                                    },
                                    onLongClick = {
                                        onRemoveCommand(cmd)
                                    }
                                )
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        ) {
                            Text(cmd, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showQuickDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }
}



// Sample logs for preview
fun sampleLogs(): List<String> = emptyList()

@Preview(showBackground = true, widthDp = 480, heightDp = 1080)
@Composable
fun MainScreenPreview() {
    AfireflyTheme {
        MainScreen()
    }
}




