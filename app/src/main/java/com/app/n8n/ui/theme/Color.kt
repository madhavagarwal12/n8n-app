package com.app.n8n.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand Colors
val N8nCoral = Color(0xFFFF5A79)
val N8nOrange = Color(0xFFFF7A59)
val N8nOrangeLight = Color(0xFFFF9E7D)

// Text Colors for Light Glassmorphic Theme
val TextDarkPrimary = Color(0xFF0F172A)
val TextDarkSecondary = Color(0xFF475569)
val TextDarkMuted = Color(0xFF64748B)

// Glassmorphism Surface Colors
val GlassCardBackground = Color(0xB8FFFFFF)       // 72% opacity white
val GlassCardBackgroundLight = Color(0xD9FFFFFF)  // 85% opacity white
val GlassInnerCard = Color(0x66F1F5F9)            // 40% opacity light slate
val GlassCardBorder = Color(0x80FFFFFF)           // 50% opacity white border

// Status Colors
val StatusRunningGreen = Color(0xFF10B981)
val StatusStoppedBlue = Color(0xFF6366F1)
val StatusStartingYellow = Color(0xFFF59E0B)
val StatusErrorRed = Color(0xFFEF4444)

// Icon Badge Tint Colors
val PinkBadgeBg = Color(0xFFFFE4E6)
val PinkBadgeTint = Color(0xFFF43F5E)

val BlueBadgeBg = Color(0xFFE0E7FF)
val BlueBadgeTint = Color(0xFF4F46E5)

val GreenBadgeBg = Color(0xFFDCFCE7)
val GreenBadgeTint = Color(0xFF10B981)

// Gradient Brushes
val PrimaryButtonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF5A79), Color(0xFFFF7A59), Color(0xFFFF8E72))
)

val StopButtonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFFB91C1C))
)

val BackgroundMeshGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFE2E8F0),
        Color(0xFFEFF6FF),
        Color(0xFFFFEDD5),
        Color(0xFFFCE7F3),
        Color(0xFFE0E7FF)
    )
)
