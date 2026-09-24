package com.app.n8n.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.app.n8n.model.LogEntry
import com.app.n8n.ui.components.TerminalLogViewer
import com.app.n8n.ui.theme.DarkSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsBottomSheet(
    logs: List<LogEntry>,
    onDismiss: () -> Unit,
    onClearLogs: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f)
    ) {
        TerminalLogViewer(
            logs = logs,
            onClearLogs = onClearLogs,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
