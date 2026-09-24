package com.app.n8n.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.n8n.model.SystemStats
import com.app.n8n.ui.theme.DarkCardBorder
import com.app.n8n.ui.theme.DarkSurface
import com.app.n8n.ui.theme.N8nOrange
import com.app.n8n.ui.theme.TextMuted
import com.app.n8n.ui.theme.TextPrimary
import com.app.n8n.ui.theme.TextSecondary

@Composable
fun SystemStatsCard(
    stats: SystemStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(
            icon = Icons.Default.Timer,
            label = "Uptime",
            value = stats.formattedUptime
        )
        StatItem(
            icon = Icons.Default.Memory,
            label = "Memory",
            value = "${stats.memoryUsageMb} MB"
        )
        StatItem(
            icon = Icons.Default.Speed,
            label = "Heap Limit",
            value = "${stats.totalMemoryMb} MB"
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = N8nOrange,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}
