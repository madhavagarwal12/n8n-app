package com.app.n8n.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.n8n.model.LogEntry
import com.app.n8n.model.LogLevel
import com.app.n8n.ui.theme.DarkCardBorder
import com.app.n8n.ui.theme.DarkSurface
import com.app.n8n.ui.theme.N8nOrange
import com.app.n8n.ui.theme.StatusBlue
import com.app.n8n.ui.theme.StatusGreen
import com.app.n8n.ui.theme.StatusRed
import com.app.n8n.ui.theme.StatusYellow
import com.app.n8n.ui.theme.TerminalBackground
import com.app.n8n.ui.theme.TerminalText
import com.app.n8n.ui.theme.TextMuted
import com.app.n8n.ui.theme.TextPrimary
import com.app.n8n.ui.theme.TextSecondary

@Composable
fun TerminalLogViewer(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, searchQuery, selectedLevel) {
        logs.filter { entry ->
            val matchesQuery = searchQuery.isBlank() || entry.message.contains(searchQuery, ignoreCase = true)
            val matchesLevel = selectedLevel == null || entry.level == selectedLevel
            matchesQuery && matchesLevel
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Console Output (${filteredLogs.size})",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row {
                IconButton(
                    onClick = {
                        val allLogsText = logs.joinToString("\n") { "[${it.formattedTime}] [${it.level}] ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("n8n Console Logs", allLogsText))
                        Toast.makeText(context, "All logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy All Logs",
                        tint = N8nOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onClearLogs) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        tint = StatusRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search logs...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Search",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = N8nOrange,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Severity Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LevelFilterChip(label = "ALL", isSelected = selectedLevel == null) {
                selectedLevel = null
            }
            LevelFilterChip(label = "ERROR", isSelected = selectedLevel == LogLevel.ERROR, color = StatusRed) {
                selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR
            }
            LevelFilterChip(label = "WARN", isSelected = selectedLevel == LogLevel.WARN, color = StatusYellow) {
                selectedLevel = if (selectedLevel == LogLevel.WARN) null else LogLevel.WARN
            }
            LevelFilterChip(label = "INFO", isSelected = selectedLevel == LogLevel.INFO, color = StatusGreen) {
                selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Terminal Output Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(TerminalBackground)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Text(
                    text = "No log output recorded yet.",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs) { entry ->
                        LogLineItem(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelFilterChip(
    label: String,
    isSelected: Boolean,
    color: Color = N8nOrange,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color,
            containerColor = Color.Transparent,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = DarkCardBorder,
            selectedBorderColor = color
        )
    )
}

@Composable
private fun LogLineItem(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> StatusRed
        LogLevel.WARN -> StatusYellow
        LogLevel.INFO -> StatusGreen
        LogLevel.DEBUG -> StatusBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "${entry.formattedTime} ",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Text(
            text = "[${entry.level.name.take(4)}] ",
            color = levelColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Text(
            text = entry.message,
            color = if (entry.level == LogLevel.ERROR) StatusRed else TerminalText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}
