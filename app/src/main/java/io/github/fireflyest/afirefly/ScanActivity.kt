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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.fireflyest.afirefly.ui.theme.AfPrimary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.github.fireflyest.afirefly.ui.theme.AfireflyTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth


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
    val pulseTransition = rememberInfiniteTransition()
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing))
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top navigation (header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { activity?.finish() }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "back")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AfPrimary)
                                .then(Modifier)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "SCANNING", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                    Text(text = "14 DEVICES IDENTIFIED", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }

                IconButton(onClick = { /* refresh or open system bt settings */ }) {
                    Icon(imageVector = Icons.Default.Bluetooth, contentDescription = "bt")
                }
            }

            // Hero / status section
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "SYSTEM_LINK.LOG", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                        Text(text = "NEURAL_INTERFACE // RF_ACTIVE", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "ID: 0x8F2A", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Text(text = "2.4GHz ACTIVE", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.size(12.dp))
                // divider with aurora glow imitation
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.surfaceVariant)) {
                    Box(modifier = Modifier
                        .width(120.dp)
                        .fillMaxWidth(0.25f)
                        .height(1.dp)
                        .background(AfPrimary.copy(alpha = 0.25f)))
                }
            }

            // Device list
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sampleDevices()) { device ->
                    DeviceRow(device = device)
                }
                // spacer at end to make room for floating widget
                item {
                    Spacer(modifier = Modifier.size(120.dp))
                }
            }
        }

        // Decorative radar and bottom floating status similar to HTML
        Box(modifier = Modifier
            .fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            // Floating bottom right status
            Card(modifier = Modifier
                .padding(end = 16.dp, bottom = 24.dp)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFB4AB), shape = RoundedCornerShape(6.dp)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Encrypted channel only", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
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