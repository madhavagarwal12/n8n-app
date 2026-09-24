package com.app.n8n.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.n8n.model.ServerState
import com.app.n8n.ui.theme.GlassBorder
import com.app.n8n.ui.theme.GlassSurfaceHighlight
import com.app.n8n.ui.theme.StatusErrorRed
import com.app.n8n.ui.theme.StatusRunningGreen
import com.app.n8n.ui.theme.StatusStartingYellow
import com.app.n8n.ui.theme.StatusStoppedBlue
import com.app.n8n.ui.theme.TextDarkSecondary

@Composable
fun StatusBadge(
    state: ServerState,
    modifier: Modifier = Modifier
) {
    val (statusColor, textColor, label) = when (state) {
        ServerState.RUNNING -> Triple(StatusRunningGreen, Color(0xFF059669), "Running")
        ServerState.STARTING -> Triple(StatusStartingYellow, Color(0xFFD97706), "Starting...")
        ServerState.STOPPING -> Triple(StatusStartingYellow, Color(0xFFD97706), "Stopping...")
        ServerState.EXTRACTING -> Triple(StatusStoppedBlue, Color(0xFF4338CA), "Setting Up")
        ServerState.ERROR -> Triple(StatusErrorRed, Color(0xFFDC2626), "Error")
        ServerState.STOPPED -> Triple(StatusStoppedBlue, TextDarkSecondary, "Stopped")
        ServerState.NOT_INSTALLED -> Triple(StatusStoppedBlue, TextDarkSecondary, "Stopped")
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val dotAlpha = if (state == ServerState.RUNNING || state == ServerState.STARTING || state == ServerState.EXTRACTING) {
        alphaAnim
    } else {
        1.0f
    }

    Row(
        modifier = modifier
            .shadow(4.dp, CircleShape, clip = false, spotColor = Color(0x14000000))
            .clip(CircleShape)
            .background(GlassSurfaceHighlight)
            .border(1.5.dp, GlassBorder, CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.5.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}
