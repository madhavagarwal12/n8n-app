package com.app.n8n.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Coral / Orange Liquid Brand Gradients
val N8nCoral = Color(0xFFFF5A79)
val N8nCoralDark = Color(0xFFE11D48)
val N8nOrange = Color(0xFFFF7253)
val N8nOrangeLight = Color(0xFFFF8B6B)

// Text Colors (High-Contrast Slate / Neutral)
val TextDarkPrimary = Color(0xFF0F172A)     // Deep slate navy
val TextDarkSecondary = Color(0xFF334155)   // Muted slate
val TextDarkMuted = Color(0xFF64748B)       // Soft caption slate

// Backward-compatible Text Aliases
val TextPrimary = TextDarkPrimary
val TextSecondary = TextDarkSecondary
val TextMuted = TextDarkMuted

// Liquid Glass Surface Tokens
val GlassSurface = Color(0xCCFFFFFF)            // 80% opacity white
val GlassSurfaceSubtle = Color(0x99FFFFFF)      // 60% opacity white
val GlassSurfaceHighlight = Color(0xE6FFFFFF)   // 90% opacity white
val GlassInnerSurface = Color(0x66F8FAFC)       // 40% opacity soft slate
val GlassBorder = Color(0x99FFFFFF)             // 60% opacity white specular border
val GlassBorderSubtle = Color(0x4DFFFFFF)       // 30% opacity subtle border

// Legacy Aliases
val GlassCardBackground = GlassSurface
val GlassCardBackgroundLight = GlassSurfaceHighlight
val GlassInnerCard = GlassInnerSurface
val GlassCardBorder = GlassBorder

// Terminal & Dark Surface Tokens (For Console Drawer)
val DarkBackground = Color(0xFF0B0D11)
val DarkSurface = Color(0xFF14171F)
val DarkSurfaceVariant = Color(0xFF1E232E)
val DarkCardBorder = Color(0xFF2A3140)

val TerminalBackground = Color(0xFF07090D)
val TerminalText = Color(0xFFE2E8F0)

// System Status Colors
val StatusRunningGreen = Color(0xFF10B981)
val StatusStoppedBlue = Color(0xFF6366F1)
val StatusStartingYellow = Color(0xFFF59E0B)
val StatusErrorRed = Color(0xFFEF4444)

val StatusGreen = StatusRunningGreen
val StatusBlue = StatusStoppedBlue
val StatusYellow = StatusStartingYellow
val StatusRed = StatusErrorRed

// Icon Badge Tint Tokens
val PinkBadgeBg = Color(0xFFFFE4E6)
val PinkBadgeTint = Color(0xFFF43F5E)

val BlueBadgeBg = Color(0xFFE0E7FF)
val BlueBadgeTint = Color(0xFF4F46E5)

val GreenBadgeBg = Color(0xFFDCFCE7)
val GreenBadgeTint = Color(0xFF10B981)

val NeutralBadgeBg = Color(0xFFF1F5F9)
val NeutralBadgeTint = Color(0xFF64748B)

// Fluid Button & Component Grushes
val PrimaryButtonGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFF5A79),
        Color(0xFFFF7253),
        Color(0xFFFF8B6B)
    )
)

val StopButtonGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFEF4444),
        Color(0xFFDC2626),
        Color(0xFFB91C1C)
    )
)

val PrimaryButtonSpecularGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x66FFFFFF),
        Color(0x00FFFFFF)
    )
)
