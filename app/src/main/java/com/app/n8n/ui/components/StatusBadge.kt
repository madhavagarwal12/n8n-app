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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.n8n.model.ServerState
import com.app.n8n.ui.theme.StatusBlue
import com.app.n8n.ui.theme.StatusGreen
import com.app.n8n.ui.theme.StatusRed
import com.app.n8n.ui.theme.StatusYellow

@Composable
fun StatusBadge(
    state: ServerState,
    modifier: Modifier = Modifier
) {
    val (statusColor, label) = when (state) {
        ServerState.RUNNING -> StatusGreen to "RUNNING"
        ServerState.STARTING -> StatusYellow to "STARTING..."
        ServerState.STOPPING -> StatusYellow to "STOPPING..."
        ServerState.EXTRACTING -> StatusBlue to "INSTALLING"
        ServerState.ERROR -> StatusRed to "ERROR"
        ServerState.STOPPED -> Color.Gray to "STOPPED"
        ServerState.NOT_INSTALLED -> Color.Gray to "NOT INSTALLED"
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
            .clip(RoundedCornerShape(50.dp))
            .background(statusColor.copy(alpha = 0.12f))
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(50.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}
