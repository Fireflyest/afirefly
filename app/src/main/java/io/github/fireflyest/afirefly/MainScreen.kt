package io.github.fireflyest.afirefly

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
// Icons used: Bluetooth, Signal
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
 
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
// OutlinedTextField replaced by SlimOutlinedTextField for compact inputs
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
// MaterialTheme import not needed in this file
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateContentSize
 
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
 
import io.github.fireflyest.afirefly.ui.theme.AfOutline
import io.github.fireflyest.afirefly.ui.theme.AfPrimary
import io.github.fireflyest.afirefly.ui.theme.AfireflyTheme

@Composable
fun MainScreen() {

    // use system color scheme (dark/light) via MaterialTheme
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val view = LocalView.current
    val ctx = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val devices = remember {
        mutableStateListOf<Device>().apply { addAll(sampleDevices()) }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val info = result.data?.toConnectedDeviceInfo() ?: return@rememberLauncherForActivityResult
            val newDevice = info.toMainDevice()
            val idx = devices.indexOfFirst { it.uid == newDevice.uid }
            if (idx >= 0) {
                devices[idx] = newDevice
            } else {
                devices.add(0, newDevice)
            }
        }
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
                TopHeader(onOpenScan = { scanLauncher.launch(Intent(ctx, ScanActivity::class.java)) })
                MainContent(modifier = Modifier.weight(1f), devices = devices)
                FooterBar()
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
    textStyle: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
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
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            textStyle = textStyle,
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
fun TopHeader(onOpenScan: () -> Unit) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    // Collapsible capsule search: shows a small pill with icon+label, expands to full search field on tap
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }


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
                    placeholder = { Text("FILTER_LOGS_BY_UUID...", color = colors.onSurface.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
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

        Spacer(modifier = Modifier.width(12.dp))
        // make bluetooth icon open ScanActivity when tapped
        IconButton(onClick = onOpenScan) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "bt",
                tint = colors.primary,
            )
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier, devices: List<Device>) {
    Column(modifier = modifier
        .fillMaxSize()
        // remove bottom padding so logs can reach the footer without an extra gap
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)) {
        // Device selector (horizontal scroll)
        DeviceSelector(devices = devices)

        Spacer(modifier = Modifier.height(6.dp))

        // Terminal area (header + log + footer info)
        TerminalView(modifier = Modifier.weight(1f))
    }
}

@Composable
fun DeviceSelector(devices: List<Device>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .size(8.dp)
                .background(AfPrimary, shape = RoundedCornerShape(8.dp)))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Active_Nodes", color = AfOutline, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(devices) { d ->
                DeviceCard(device = d)
            }
        }
    }
}

@Composable
fun DeviceCard(device: Device) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val isOnline = device.status == "ONLINE"
    val isLinking = device.status == "LINKING"
    val containerColor = when {
        isOnline -> colors.primary.copy(alpha = 0.10f)
        isLinking -> colors.tertiary.copy(alpha = 0.10f)
        else -> colors.surfaceVariant
    }
    val borderColor = when {
        isOnline -> colors.primary.copy(alpha = 0.35f)
        isLinking -> colors.tertiary.copy(alpha = 0.35f)
        else -> colors.surfaceVariant
    }

    Card(
        modifier = Modifier
            .width(192.dp)
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier
            .border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = device.name, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = device.uid, color = colors.outline, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Box(modifier = Modifier
                    .size(10.dp)
                    .background(
                        when {
                            isOnline -> colors.primary
                            isLinking -> colors.tertiary
                            else -> colors.outline
                        },
                        shape = RoundedCornerShape(10.dp)
                    ))
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
fun TerminalView(modifier: Modifier = Modifier) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize()) {
        // logs area
        LazyColumn(modifier = Modifier
            .weight(1f)
            .background(colors.surface)
            .padding(vertical = 0.dp, horizontal = 0.dp)) {
            items(sampleLogs()) { entry ->
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
@OptIn(ExperimentalMaterial3Api::class)
fun FooterBar() {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Row(modifier = Modifier
        .navigationBarsPadding()
        .fillMaxWidth()
        .background(colors.surface.copy(alpha = 0.9f))
        .padding(6.dp), verticalAlignment = Alignment.Bottom) {
        // left toggle button (attached to input) - visual toggle
        var isHex by remember { mutableStateOf(false) }
        // input state (was previously a fixed empty string) -> now editable
        var inputText by remember { mutableStateOf("") }
        var showQuickDialog by remember { mutableStateOf(false) }
        val leftBg = if (isHex) colors.primary else colors.surfaceVariant
        // make the 0x label highly legible when active: use onPrimary (contrasting with colors.primary)
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
            .clickable { isHex = !isHex }
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
                borderColor = colors.outline,
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
        Box(modifier = Modifier.width(animButtonWidth)) {
            Crossfade(targetState = inputText.isNotBlank(), label = "send_add_crossfade") { hasText ->
                if (!hasText) {
                    IconButton(onClick = { showQuickDialog = true }, modifier = Modifier
                        .size(44.dp)
                        .padding(end = 6.dp)) {
                        // outlined circular add (no fill)
                        Box(modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, colors.outline, shape = RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "add", tint = colors.primary)
                        }
                    }
                } else {
                    Button(
                        onClick = { /* TODO: send the command */ },
                        modifier = Modifier
                            .height(44.dp)
                            .padding(end = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.onSurface
                        )
                    ) {
                        Text("SEND", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // quick commands bottom sheet (appears from bottom)
        if (showQuickDialog) {
            val quicks = listOf("AT+POLL_TELEMETRY=1", "AT+RESYNC=4", "AT+THRUST_COMP=0.98")
            ModalBottomSheet(onDismissRequest = { showQuickDialog = false }) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)) {
                    Text("Quick Commands", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    quicks.forEach { cmd ->
                        TextButton(onClick = { inputText = cmd; showQuickDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(cmd, fontFamily = FontFamily.Monospace)
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

data class Device(
    val name: String,
    val subtitle: String,
    val uid: String,
    val status: String,
    val signal: String,
    val battery: String,
    val locked: Boolean = false,
    val gps: Boolean = false
)

fun sampleDevices(): List<Device> {
    val listOf = listOf(
        Device("Alpha-1", "STABLE", "001", "ONLINE", "85%", "94%", locked = false, gps = true),
        Device("Beta-2", "LINKING", "002", "LINKING", "40%", "48%", locked = true, gps = false),
        Device("Gamma-9", "OFFLINE", "003", "OFFLINE", "--", "--", locked = true, gps = false)
    )
    return listOf
}

fun sampleLogs(): List<String> = listOf(
    "14:02:31 SYSTEM_BOOT: Initializing Mona Protocol v4.2.0...",
    "14:02:32 KERN: ARM-64 Architecture detected.",
    "14:02:33 NET: Bluetooth handshake with Alpha-1 successful.",
    "14:02:35 TELEMETRY: Altitude: 42.1m | Pitch: 2.1° | Roll: 0.0°",
    "14:02:45 WARN: Proximity sensor (Rear) blocked by debris.",
    "14:03:02 >> Listening for user input... _",
    "14:03:05 DIAG: Battery health 94% | Voltage 15.2V",
    "14:03:10 NET: RSSI -45dBm | LATENCY 12.4ms",
    "14:03:12 TELEMETRY: GPS Lock 12 satellites",
    "14:03:15 CMD_EXEC: Running 'mona --diagnostic'",
    "14:03:18 DIAG: Rotors 1-4 functional status: OK",
    "14:03:21 INFO: Writing flight log to sector 0x7F2",
    "14:03:25 ALERT: Temp spike detected: 42C"
)

@Preview(showBackground = true, widthDp = 480, heightDp = 1080)
@Composable
fun MainScreenPreview() {
    AfireflyTheme {
        MainScreen()
    }
}

