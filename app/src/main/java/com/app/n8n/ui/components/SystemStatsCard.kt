package com.app.n8n.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.n8n.model.SystemStats
import com.app.n8n.ui.theme.BlueBadgeBg
import com.app.n8n.ui.theme.BlueBadgeTint
import com.app.n8n.ui.theme.GlassCardBackground
import com.app.n8n.ui.theme.GlassCardBorder
import com.app.n8n.ui.theme.GreenBadgeBg
import com.app.n8n.ui.theme.GreenBadgeTint
import com.app.n8n.ui.theme.PinkBadgeBg
import com.app.n8n.ui.theme.PinkBadgeTint
import com.app.n8n.ui.theme.TextDarkMuted
import com.app.n8n.ui.theme.TextDarkPrimary

@Composable
fun SystemStatsCard(
    stats: SystemStats,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        backgroundColor = GlassCardBackground,
        borderColor = GlassCardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1: Uptime
            StatColumnItem(
                icon = Icons.Default.AvTimer,
                iconBg = PinkBadgeBg,
                iconTint = PinkBadgeTint,
                value = stats.formattedUptime,
                label = "Uptime",
                modifier = Modifier.weight(1f)
            )

            // Divider 1
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(Color(0xFFCBD5E1).copy(alpha = 0.5f))
            )

            // Column 2: Memory
            StatColumnItem(
                icon = Icons.Default.Memory,
                iconBg = BlueBadgeBg,
                iconTint = BlueBadgeTint,
                value = "${stats.memoryUsageMb} MB",
                label = "Memory",
                modifier = Modifier.weight(1f)
            )

            // Divider 2
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(Color(0xFFCBD5E1).copy(alpha = 0.5f))
            )

            // Column 3: Heap Limit
            StatColumnItem(
                icon = Icons.Default.Speed,
                iconBg = GreenBadgeBg,
                iconTint = GreenBadgeTint,
                value = "${stats.totalMemoryMb} MB",
                label = "Heap Limit",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatColumnItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg)
                .border(1.dp, Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            color = TextDarkPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            color = TextDarkMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
