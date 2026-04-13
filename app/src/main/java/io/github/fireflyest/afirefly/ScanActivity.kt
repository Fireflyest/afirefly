package io.github.fireflyest.afirefly

import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.fireflyest.afirefly.ui.theme.AfPrimary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.github.fireflyest.afirefly.ui.theme.AfireflyTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack


class ScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AfireflyTheme {
                Scaffold { innerPadding ->
                    ScanScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ScanScreen(modifier: Modifier = Modifier) {
    // Use nullable activity for preview safety
    val ctx = LocalContext.current
    val activity = ctx as? Activity

    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    // pulse used visually in title; handled below with a local transition

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TopAppBar: match HTML: fixed height (h-20 ~ 80dp), translucent background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.4f))
                    .height(80.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // left: back + title
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "back", tint = colors.onSurface)
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Scanning", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.onSurface)
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
                        // sweeping sector: drawArc with rotating startAngle
                        drawArc(
                            brush = Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.28f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(cx, cy), radius = radius * 1.2f),
                            startAngle = angle - 30f,
                            sweepAngle = 60f,
                            useCenter = true,
                            topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                        )
                        // center dot
                        drawCircle(colors.primary, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
                    }

                    // nodes badge
                    Box(modifier = Modifier
                        .background(colors.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
                        .border(1.dp, colors.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column { Text(text = "07", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = colors.primary); }
                            Column { Text(text = "Nodes", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = colors.onSurface.copy(alpha = 0.7f)) }
                        }
                    }
                }
            }

            // (Removed hero title block to match requested layout)
            Spacer(modifier = Modifier.height(8.dp))

            // device list
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sampleDevices()) { device ->
                    // replicate card style from HTML
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, colors.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(40.dp).background(colors.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    // placeholder icon (use text glyphs for now)
                                    Text(text = "🔊")
                                }
                                Column {
                                    Text(text = device.name, color = colors.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                    Text(text = device.uid, color = colors.onSurface.copy(alpha = 0.6f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // signal bars
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.width(4.dp).height(8.dp).background(if (device.signal.contains("-%")) colors.surfaceVariant else colors.primary, shape = RoundedCornerShape(2.dp)))
                                    Box(modifier = Modifier.width(4.dp).height(12.dp).background(colors.primary, shape = RoundedCornerShape(2.dp)))
                                    Box(modifier = Modifier.width(4.dp).height(14.dp).background(colors.primary, shape = RoundedCornerShape(2.dp)))
                                    Box(modifier = Modifier.width(4.dp).height(16.dp).background(colors.primary, shape = RoundedCornerShape(2.dp)))
                                }
                                // glass Connect button
                                Box(modifier = Modifier
                                    .background(AfPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(10.dp))
                                    .border(1.dp, AfPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(text = "Connect", color = AfPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.size(120.dp))
                }
            }
        }
    }
}

@Composable
fun DeviceRow(device: Device) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).background(colors.surfaceVariant, shape = RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    // placeholder icon
                    Text(text = "🔊")
                }
                Column {
                    Text(text = device.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(text = device.uid, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "-62 dBm", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Button(onClick = { /* connect action placeholder */ }) {
                    Text("CONNECT")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScanPreview() {
    AfireflyTheme { ScanScreen() }
}