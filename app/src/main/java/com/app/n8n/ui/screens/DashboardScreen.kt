package com.app.n8n.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.n8n.R
import com.app.n8n.model.ServerState
import com.app.n8n.model.SystemStats
import com.app.n8n.ui.components.FluidBackground
import com.app.n8n.ui.components.GlassCard
import com.app.n8n.ui.components.QrCodeView
import com.app.n8n.ui.components.StatusBadge
import com.app.n8n.ui.components.SystemStatsCard
import com.app.n8n.ui.components.UrlCard
import com.app.n8n.ui.theme.GlassCardBackground
import com.app.n8n.ui.theme.GlassCardBorder
import com.app.n8n.ui.theme.PrimaryButtonGradient
import com.app.n8n.ui.theme.StatusStartingYellow
import com.app.n8n.ui.theme.StopButtonGradient
import com.app.n8n.ui.theme.TextDarkMuted
import com.app.n8n.ui.theme.TextDarkPrimary

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

    FluidBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. App Header
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
                            .size(46.dp)
                            .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = Color(0x26FF5A79))
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.5.dp, Color(0x99FFFFFF), RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "n8n Server",
                            color = TextDarkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "Self-Hosted on Android",
                            color = TextDarkMuted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                StatusBadge(state = serverState)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Battery Warning Banner (if enabled)
            if (isBatteryOptimized) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0xF2FEF3C7),
                    borderColor = Color(0x66F59E0B)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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
                                tint = StatusStartingYellow,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Disable battery optimization to keep n8n active in background.",
                                color = TextDarkPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(StatusStartingYellow)
                                .clickable { onRequestDisableBatteryOptimization() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Fix", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Primary Glowing Action Button (Start / Stop Server)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .shadow(
                        elevation = if (isRunning) 14.dp else 16.dp,
                        shape = RoundedCornerShape(26.dp),
                        spotColor = if (isRunning) Color(0x66EF4444) else Color(0x66FF5A79),
                        ambientColor = Color(0x26FF5A79)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(brush = if (isRunning) StopButtonGradient else PrimaryButtonGradient)
                    .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(26.dp))
                    .clickable(enabled = !isBusy) {
                        if (isRunning) onStopServer() else onStartServer()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (serverState == ServerState.STARTING) "Starting Server..." else "Stopping Server...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .border(1.dp, Color(0x40FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isRunning) "Stop Server" else "Start Server",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. QR Code Card (When Running)
            if (isRunning) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Scan to Pair",
                            color = TextDarkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        QrCodeView(
                            data = systemStats.httpUrl,
                            size = 170.dp,
                            backgroundColor = Color.White
                        )
                    }
                }
            }

            // 5. Local Network Access Card
            UrlCard(
                httpUrl = systemStats.httpUrl,
                mdnsUrl = systemStats.mdnsUrl
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 6. System Stats Card (Uptime, Memory, Heap Limit)
            SystemStatsCard(stats = systemStats)

            Spacer(modifier = Modifier.height(16.dp))

            // 7. View Live Console Logs Card Button
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLogs() },
                shape = RoundedCornerShape(24.dp),
                backgroundColor = GlassCardBackground,
                borderColor = GlassCardBorder,
                elevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "View Live Console Logs",
                            color = TextDarkPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xD9FFFFFF))
                            .border(1.dp, Color(0x80FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Logs",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
