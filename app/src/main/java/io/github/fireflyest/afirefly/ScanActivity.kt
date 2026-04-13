package io.github.fireflyest.afirefly

import android.os.Bundle
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
// ...existing imports...
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import io.github.fireflyest.afirefly.ui.theme.AfPrimary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.github.fireflyest.afirefly.ui.theme.AfireflyTheme
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

data class ScannedDevice(
    val name: String?,
    val address: String,
    var rssi: Int,
    // bluetooth device type (BluetoothDevice.DEVICE_TYPE_*) if available
    val deviceType: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN,
    // service UUIDs observed in the scan record (strings)
    val serviceUuids: List<String> = emptyList()
)

// Map device metadata to a vector drawable icon.
@DrawableRes
fun getDeviceIconRes(device: ScannedDevice): Int {
    val name = device.name?.lowercase() ?: ""
    val uuids = device.serviceUuids.joinToString(separator = " ") { it.lowercase() }

    return when {
        device.deviceType == BluetoothDevice.DEVICE_TYPE_CLASSIC -> when {
            name.contains("audio") || name.contains("speaker") || name.contains("headset") || name.contains("headphones") || name.contains("sound") -> R.drawable.ic_device_audio
            name.contains("phone") -> R.drawable.ic_device_phone
            name.contains("bridge") || name.contains("gateway") || name.contains("link") || name.contains("router") -> R.drawable.ic_device_gateway
            else -> R.drawable.ic_device_classic
        }
        device.deviceType == BluetoothDevice.DEVICE_TYPE_LE -> when {
            uuids.contains("0000180f") || name.contains("battery") -> R.drawable.ic_device_battery
            uuids.contains("0000180d") || name.contains("heart") || name.contains("hrm") -> R.drawable.ic_device_sensor
            name.contains("temp") || name.contains("therm") || name.contains("sensor") -> R.drawable.ic_device_sensor
            name.contains("gps") || name.contains("location") || name.contains("nav") -> R.drawable.ic_device_gateway
            name.contains("drone") || name.contains("vtol") || name.contains("fly") -> R.drawable.ic_device_unknown
            name.contains("watch") || name.contains("fit") || name.contains("band") -> R.drawable.ic_device_classic
            else -> R.drawable.ic_device_le
        }
        device.deviceType == BluetoothDevice.DEVICE_TYPE_DUAL -> when {
            name.contains("audio") || name.contains("speaker") || name.contains("headset") || name.contains("headphones") -> R.drawable.ic_device_audio
            name.contains("sensor") || name.contains("temp") || name.contains("therm") -> R.drawable.ic_device_sensor
            name.contains("bridge") || name.contains("gateway") || name.contains("link") || name.contains("router") -> R.drawable.ic_device_gateway
            else -> R.drawable.ic_device_dual
        }
        else -> when {
            // Common BLE service UUID substrings
            uuids.contains("0000180f") || name.contains("battery") -> R.drawable.ic_device_battery
            uuids.contains("0000180d") || name.contains("heart") || name.contains("hrm") -> R.drawable.ic_device_sensor
            name.contains("audio") || name.contains("speaker") || name.contains("mona") || name.contains("sound") -> R.drawable.ic_device_audio
            name.contains("watch") || name.contains("fit") || name.contains("band") -> R.drawable.ic_device_classic
            name.contains("phone") || name.contains("headset") || name.contains("headphones") -> R.drawable.ic_device_phone
            name.contains("gps") || name.contains("location") || name.contains("nav") -> R.drawable.ic_device_gateway
            name.contains("temp") || name.contains("therm") || name.contains("sensor") -> R.drawable.ic_device_sensor
            name.contains("bridge") || name.contains("gateway") || name.contains("link") || name.contains("router") -> R.drawable.ic_device_gateway
            name.contains("drone") || name.contains("vtol") || name.contains("fly") -> R.drawable.ic_device_unknown
            else -> R.drawable.ic_device_unknown
        }
    }
}


class ScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        runCatching {
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
        }
        setContent {
            AfireflyTheme {
                Scaffold { contentPadding ->
                    @Suppress("UNUSED_VARIABLE") val ignore = contentPadding
                    ScanScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(modifier: Modifier = Modifier) {
    // Use nullable activity for preview safety
    val ctx = LocalContext.current
    val activity = ctx as? Activity

    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    // pulse used visually in title; handled below with a local transition

    // scanned devices state
    val scannedDevices = remember { mutableStateListOf<ScannedDevice>() }
    val isScanning = rememberSaveable { mutableStateOf(false) }
    // no coroutine scope needed here

    // Bluetooth adapter and scanner (nullable for previews)
    val btManager = (ctx.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? BluetoothManager)
    val btAdapter = btManager?.adapter
    val btScanner: BluetoothLeScanner? = btAdapter?.bluetoothLeScanner

    // Permission handling
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // permission launcher + flag
    val permissionGranted = remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted.value = result.entries.all { it.value }
    }

    // Scan callback (declared before start/stop helpers so lambdas can reference it)
    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                val addr = d.address ?: return
                val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    result.scanRecord?.deviceName
                } else {
                    d.name ?: result.scanRecord?.deviceName
                }
                // ignore devices without a discoverable name (per request)
                if (name.isNullOrBlank()) return
                val rssi = result.rssi
                val deviceType = try { d.type } catch (_: Exception) { BluetoothDevice.DEVICE_TYPE_UNKNOWN }
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                Handler(Looper.getMainLooper()).post {
                    val idx = scannedDevices.indexOfFirst { it.address == addr }
                    if (idx >= 0) {
                        scannedDevices[idx].rssi = rssi
                    } else {
                        scannedDevices.add(ScannedDevice(name, addr, rssi, deviceType, uuids))
                    }
                }
            }
        }
    }

// ...existing code...

    // helper to start/stop scanning
    val startScanAction: () -> Unit = {
        btScanner?.let { scanner ->
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    // missing permission, request
                    permissionLauncher.launch(permissions)
                    return@let
                }
                scanner.startScan(null, settings, scanCallback)
                isScanning.value = true
            } catch (e: SecurityException) {
                // request permissions
                permissionLauncher.launch(permissions)
            }
        }
    }

    val stopScanAction: () -> Unit = {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // nothing
            } else {
                btScanner?.stopScan(scanCallback)
            }
        } catch (_: Exception) {}
        isScanning.value = false
    }

    // request enable bluetooth if disabled
    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (btAdapter?.isEnabled == true) {
            startScanAction()
        }
    }

    

    // request permissions on enter and start scanning automatically
    LaunchedEffect(Unit) {
        // if permissions already granted, proceed
        val missing = permissions.any { p -> ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED }
        if (missing) {
            permissionLauncher.launch(permissions)
        } else {
            // ensure bluetooth enabled
            if (btAdapter != null && !btAdapter.isEnabled) {
                val enableIntent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableIntent)
            } else {
                startScanAction()
            }
        }
    }

    // when permission result arrives, start scanning (or request enable if disabled)
    LaunchedEffect(permissionGranted.value) {
        if (permissionGranted.value) {
            if (btAdapter != null && !btAdapter.isEnabled) {
                val enableIntent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableIntent)
            } else {
                startScanAction()
            }
        }
    }

    // auto-stop scanning after 10 seconds when started
    LaunchedEffect(isScanning.value) {
        if (isScanning.value) {
            delay(10_000L)
            // ensure we stop scanning after the timeout
            stopScanAction()
        }
    }

    // stop scanning when composable leaves
    DisposableEffect(Unit) {
        onDispose {
            stopScanAction()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // set system bars to match the surface so header/footer feel immersive
        val view = LocalView.current
        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        if (!view.isInEditMode) {
            val systemUiController = rememberSystemUiController()
            SideEffect {
                // Make both bars transparent; use dark icons on light backgrounds.
                systemUiController.setStatusBarColor(color = Color.Transparent, darkIcons = !isDarkTheme)
                systemUiController.setNavigationBarColor(color = Color.Transparent, darkIcons = false)
            }
            SideEffect {
                val window = activity?.window ?: return@SideEffect
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // TopAppBar: match HTML: fixed height (h-20 ~ 80dp), translucent background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(colors.surface.copy(alpha = 0.4f))
                    .height(80.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // left: back + title
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = colors.onSurface)
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val titleText = if (isScanning.value) "Scanning" else "Scanner"
                            Text(text = titleText, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        }
                    }
                }

                // right: radar + nodes badge
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // radar
                    val spin = rememberInfiniteTransition()
                    val angle by spin.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)))
                    // more accurate radar using Canvas sector sweep
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(40.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = size.minDimension / 2f
                        // outer rings
                        drawCircle(colors.primary.copy(alpha = 0.2f), radius = radius, center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                        drawCircle(colors.primary.copy(alpha = 0.1f), radius = radius - 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                        // sweeping sector: only when actively scanning
                        if (isScanning.value) {
                            drawArc(
                                brush = Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.28f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(cx, cy), radius = radius * 1.2f),
                                startAngle = angle - 30f,
                                sweepAngle = 60f,
                                useCenter = true,
                                topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
                                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                            )
                        }
                        // center dot
                        drawCircle(colors.primary, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
                    }

                    // nodes badge (dynamic)
                    Box(modifier = Modifier
                        .background(colors.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
                        .border(1.dp, colors.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column { Text(text = scannedDevices.size.toString(), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = colors.primary); }
                            Column { Text(text = "Nodes", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = colors.onSurface.copy(alpha = 0.7f)) }
                        }
                    }
                }
            }

            // (Removed hero title block to match requested layout)
            Spacer(modifier = Modifier.height(8.dp))

            // small spacer between header and list
            Spacer(modifier = Modifier.height(4.dp))

            // device list
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = scannedDevices, key = { it.address }) { device ->
                    val appeared = remember(device.address) { mutableStateOf(false) }
                    LaunchedEffect(device.address) {
                        appeared.value = false
                        delay(16)
                        appeared.value = true
                    }
                    val enterAlpha by animateFloatAsState(targetValue = if (appeared.value) 1f else 0f, animationSpec = tween(280), label = "itemAlpha")
                    val enterOffset by animateFloatAsState(targetValue = if (appeared.value) 0f else 18f, animationSpec = tween(280), label = "itemOffset")

                    // replicate card style from HTML for scanned devices; animate placement when reordering
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .graphicsLayer {
                            alpha = enterAlpha
                            translationY = enterOffset
                        }
                        .background(colors.surface.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, colors.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // Left area: icon + name/address. Give it weight so the right controls keep fixed space
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(36.dp).background(colors.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = getDeviceIconRes(device)),
                                            contentDescription = null,
                                            tint = colors.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                }
                                // Make the text column take remaining space and ellipsize long names so the right-side controls
                                // (signal + Connect) keep their fixed width and are not pushed out.
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name ?: "Unknown Device",
                                        color = colors.onSurface,
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = device.address,
                                        color = colors.onSurface.copy(alpha = 0.6f),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // right-side controls: compact signal bars + Connect with reduced spacing
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // compute strength from RSSI and render compact bars (no RSSI text)
                                val strength = when {
                                    device.rssi >= -60 -> 4
                                    device.rssi >= -75 -> 3
                                    device.rssi >= -90 -> 2
                                    device.rssi >= -105 -> 1
                                    else -> 0
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.width(16.dp)) {
                                    // four vertical bars with increasing height (narrower bars)
                                    val heights = listOf(6.dp, 10.dp, 14.dp, 18.dp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                        heights.forEachIndexed { i, h ->
                                            val filled = i < strength
                                            Box(modifier = Modifier
                                                .width(2.dp)
                                                .height(h)
                                                .background(if (filled) colors.primary else colors.surfaceVariant, shape = RoundedCornerShape(2.dp)))
                                        }
                                    }
                                }
                                // glass Connect button with stable (reduced) width so it's not pushed out
                                Box(modifier = Modifier
                                    .width(80.dp)
                                    .background(AfPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(10.dp))
                                    .border(1.dp, AfPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Connect", color = AfPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(96.dp)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScanPreview() {
    AfireflyTheme { ScanScreenPreviewContent() }
}

// Lightweight preview-only UI that doesn't request permissions or access Bluetooth.
// Keeps the preview working in Android Studio by rendering the same layout with
// sample data instead of running runtime scanning logic.
@Composable
fun ScanScreenPreviewContent() {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val sample = listOf(
        ScannedDevice("Mona-Audio-X1", "00:1A:2B:3C:4D:5E", -42),
        ScannedDevice("Architect-Pro", "B4:F2:35:91:0A:CC", -72),
        ScannedDevice("Mona Watch 4", "FE:88:21:44:BC:90", -88)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header (static for preview)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(colors.surface.copy(alpha = 0.4f))
                    .height(80.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = colors.onSurface)
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Scanning", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        }
                    }
                }

                // nodes badge
                Box(modifier = Modifier
                    .background(colors.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
                    .border(1.dp, colors.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column { Text(text = sample.size.toString(), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = colors.primary); }
                        Column { Text(text = "Nodes", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = colors.onSurface.copy(alpha = 0.7f)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // device list (sample)
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sample) { device ->
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, colors.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(40.dp).background(colors.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = getDeviceIconRes(device)),
                                        contentDescription = null,
                                        tint = colors.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name ?: "Unknown Device",
                                        color = colors.onSurface,
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = device.address,
                                        color = colors.onSurface.copy(alpha = 0.6f),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // right-side compact controls
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // simple static bars for preview (all full)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.width(20.dp)) {
                                    val heights = listOf(6.dp, 10.dp, 14.dp, 18.dp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                        heights.forEach { h ->
                                            Box(modifier = Modifier
                                                .width(2.dp)
                                                .height(h)
                                                .background(colors.primary, shape = RoundedCornerShape(2.dp)))
                                        }
                                    }
                                }
                                Box(modifier = Modifier
                                    .width(64.dp)
                                    .background(AfPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(10.dp))
                                    .border(1.dp, AfPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Connect", color = AfPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(96.dp)) }
            }
        }
    }
}