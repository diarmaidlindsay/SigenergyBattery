package com.github.diarmaidlindsay.sigenergybattery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ChargeGreen,
    onPrimary = Color(0xFF0A1F12),
    primaryContainer = Color(0xFF15301E),
    onPrimaryContainer = ChargeGreen,
    secondary = PowerCyan,
    onSecondary = Color(0xFF062226),
    secondaryContainer = Color(0xFF12343A),
    onSecondaryContainer = PowerCyan,
    tertiary = WarnYellow,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceVariant,
    surfaceContainerHighest = SurfaceBright,
    error = AlertRed,
    outline = Color(0xFF2E3A35),
    outlineVariant = Color(0xFF232C28)
)

@Composable
fun SigenergyBatteryTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
