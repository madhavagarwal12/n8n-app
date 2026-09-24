package com.app.n8n.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import com.app.n8n.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.n8n.model.ServerState
import com.app.n8n.model.SystemStats
import com.app.n8n.ui.components.QrCodeView
import com.app.n8n.ui.components.StatusBadge
import com.app.n8n.ui.components.SystemStatsCard
import com.app.n8n.ui.components.UrlCard
import com.app.n8n.ui.theme.DarkBackground
import com.app.n8n.ui.theme.DarkCardBorder
import com.app.n8n.ui.theme.DarkSurface
import com.app.n8n.ui.theme.DarkSurfaceVariant
import com.app.n8n.ui.theme.N8nOrange
import com.app.n8n.ui.theme.StatusRed
import com.app.n8n.ui.theme.StatusYellow
import com.app.n8n.ui.theme.TextMuted
import com.app.n8n.ui.theme.TextPrimary
import com.app.n8n.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    serverState: ServerState,
    systemStats: SystemStats,
    isBatteryOptimized: Boolean,
    onRequestDisableBatteryOptimization: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val scrollState = rememberScrollState()

    val isRunning = serverState == ServerState.RUNNING
    val isBusy = serverState == ServerState.STARTING || serverState == ServerState.STOPPING

    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) StatusRed else N8nOrange,
        label = "btnColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_round),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(2.dp, N8nOrange, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "n8n Server",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Self-Hosted on Android",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            StatusBadge(state = serverState)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery Optimization Warning Card (if applicable)
        if (isBatteryOptimized) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = StatusYellow.copy(alpha = 0.12f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusYellow.copy(alpha = 0.4f)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = StatusYellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Disable battery optimization to prevent background sleep.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Button(
                        onClick = onRequestDisableBatteryOptimization,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusYellow),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Fix", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Server Power Control Button
        Button(
            onClick = {
                if (isRunning) onStopServer() else onStartServer()
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                disabledContainerColor = buttonColor.copy(alpha = 0.5f)
            )
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (serverState == ServerState.STARTING) "STARTING SERVER..." else "STOPPING SERVER...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "STOP SERVER" else "START SERVER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // QR Code Connection Widget (When Running)
        if (isRunning) {
            QrCodeView(
                data = systemStats.httpUrl,
                size = 180.dp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Network URLs Card
        UrlCard(
            httpUrl = systemStats.httpUrl,
            mdnsUrl = systemStats.mdnsUrl
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System Stats
        SystemStatsCard(stats = systemStats)

        Spacer(modifier = Modifier.height(16.dp))

        // Console Logs Toggle Button
        OutlinedButton(
            onClick = onOpenLogs,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = DarkSurfaceVariant,
                contentColor = TextPrimary
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = N8nOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "View Live Console Logs",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
