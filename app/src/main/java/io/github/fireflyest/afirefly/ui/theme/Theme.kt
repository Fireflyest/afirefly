package io.github.fireflyest.afirefly.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AfPrimary,
    onPrimary = AfOnPrimary,
    primaryContainer = AfPrimaryContainer,
    onPrimaryContainer = AfOnPrimaryContainer,

    secondary = AfSecondary,
    onSecondary = AfOnSecondary,
    secondaryContainer = AfSecondaryContainer,
    onSecondaryContainer = AfOnSecondary,

    tertiary = AfTertiary,
    onTertiary = AfOnTertiary,
    tertiaryContainer = AfTertiaryContainer,

    background = AfBackground,
    onBackground = AfOnBackground,

    surface = AfSurface,
    onSurface = AfOnSurface,
    surfaceVariant = AfSurfaceContainer,
    onSurfaceVariant = AfOnSurfaceVariant,

    outline = AfOutline,

    error = AfError,
    errorContainer = AfErrorContainer,
    onError = AfOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = AfPrimary,
    onPrimary = AfOnPrimary,
    primaryContainer = AfPrimaryContainer,
    onPrimaryContainer = AfOnPrimaryContainer,

    secondary = AfSecondary,
    onSecondary = AfOnSecondary,
    secondaryContainer = AfSecondaryContainer,
    onSecondaryContainer = AfOnSecondary,

    tertiary = AfTertiary,
    onTertiary = AfOnTertiary,
    tertiaryContainer = AfTertiaryContainer,

    background = AfBackgroundLight,
    onBackground = AfOnBackgroundLight,

    surface = AfSurfaceLight,
    onSurface = AfOnSurfaceLight,
    surfaceVariant = AfSurfaceVariantLight,
    onSurfaceVariant = AfOnSurfaceVariantLight,

    outline = AfOutline,

    error = AfError,
    errorContainer = AfErrorContainer,
    onError = AfOnErrorContainer
)

@Composable
fun AfireflyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}