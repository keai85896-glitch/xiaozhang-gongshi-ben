package com.java.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, onPrimary = Color(0xFF0B1D33), primaryContainer = PrimaryContainerDark, onPrimaryContainer = Color(0xFFD3E3FD),
    background = BackgroundDark, surface = SurfaceDark, surfaceVariant = SurfaceVariantDark, onSurface = Color(0xFFE6E6E6), onSurfaceVariant = Color(0xFFA0A0A0), error = Color(0xFFF28B82)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, onPrimary = Color.White, primaryContainer = PrimaryContainerLight, onPrimaryContainer = Color(0xFF21005D),
    background = BackgroundLight, surface = SurfaceLight, surfaceVariant = SurfaceVariantLight, onSurface = Color(0xFF1C1B1F), onSurfaceVariant = Color(0xFF666666), error = Color(0xFFB3261E)
)

@Composable
fun MyApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
