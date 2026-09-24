package com.app.n8n.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun FluidBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEBF1FA))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-left sky blue orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFBAE6FD).copy(alpha = 0.85f), Color.Transparent),
                    center = Offset(width * 0.15f, height * 0.12f),
                    radius = width * 0.65f
                ),
                center = Offset(width * 0.15f, height * 0.12f),
                radius = width * 0.65f
            )

            // Top-right soft peach/lavender orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFDDD6FE).copy(alpha = 0.70f), Color.Transparent),
                    center = Offset(width * 0.85f, height * 0.20f),
                    radius = width * 0.60f
                ),
                center = Offset(width * 0.85f, height * 0.20f),
                radius = width * 0.60f
            )

            // Middle coral warm glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD1C7).copy(alpha = 0.65f), Color.Transparent),
                    center = Offset(width * 0.10f, height * 0.45f),
                    radius = width * 0.55f
                ),
                center = Offset(width * 0.10f, height * 0.45f),
                radius = width * 0.55f
            )

            // Bottom vibrant fluid gradients (Coral, Pink, Deep Sky)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF8E7E).copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(width * 0.20f, height * 0.88f),
                    radius = width * 0.75f
                ),
                center = Offset(width * 0.20f, height * 0.88f),
                radius = width * 0.75f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(width * 0.85f, height * 0.80f),
                    radius = width * 0.80f
                ),
                center = Offset(width * 0.85f, height * 0.80f),
                radius = width * 0.80f
            )
        }

        content()
    }
}
