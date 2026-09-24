package com.app.n8n.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = N8nCoral,
    onPrimary = Color.White,
    primaryContainer = N8nOrangeLight,
    onPrimaryContainer = TextDarkPrimary,
    secondary = BlueBadgeTint,
    onSecondary = Color.White,
    background = Color(0xFFF1F5F9),
    onBackground = TextDarkPrimary,
    surface = Color.White,
    onSurface = TextDarkPrimary,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = TextDarkSecondary,
    outline = GlassCardBorder
)

@Composable
fun N8nTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
