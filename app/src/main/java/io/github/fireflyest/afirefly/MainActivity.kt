package io.github.fireflyest.afirefly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.fireflyest.afirefly.ui.theme.AfireflyTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import io.github.fireflyest.afirefly.ui.theme.AfBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // set system bars to match app background so status/navigation bars blend with UI
//        window.statusBarColor = AfBackground.toArgb()
//        window.navigationBarColor = AfBackground.toArgb()
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // false = dark icons disabled -> use light icons suitable for dark background
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        setContent {
            AfireflyTheme {
                MainScreen()
            }
        }
    }
}
