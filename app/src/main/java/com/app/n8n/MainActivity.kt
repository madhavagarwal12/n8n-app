package com.app.n8n

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.app.n8n.model.ServerState
import com.app.n8n.ui.screens.DashboardScreen
import com.app.n8n.ui.screens.LogsBottomSheet
import com.app.n8n.ui.screens.SetupScreen
import com.app.n8n.ui.theme.DarkBackground
import com.app.n8n.ui.theme.N8nTheme
import com.app.n8n.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            N8nTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    val serverState by viewModel.serverState.collectAsState()
                    val systemStats by viewModel.systemStats.collectAsState()
                    val logs by viewModel.logs.collectAsState()
                    val extractionProgress by viewModel.extractionProgress.collectAsState()
                    val extractionStatusMessage by viewModel.extractionStatusMessage.collectAsState()
                    val extractionError by viewModel.extractionError.collectAsState()
                    val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsState()

                    var isLogsSheetOpen by remember { mutableStateOf(false) }

                    if (serverState == ServerState.EXTRACTING) {
                        SetupScreen(
                            progress = extractionProgress,
                            statusMessage = extractionStatusMessage,
                            errorMessage = extractionError,
                            onRetry = { viewModel.checkAndPerformInstallation() }
                        )
                    } else {
                        DashboardScreen(
                            serverState = serverState,
                            systemStats = systemStats,
                            isBatteryOptimized = isBatteryOptimized,
                            onRequestDisableBatteryOptimization = { requestIgnoreBatteryOptimizations() },
                            onStartServer = { viewModel.startServer() },
                            onStopServer = { viewModel.stopServer() },
                            onOpenLogs = { isLogsSheetOpen = true }
                        )
                    }

                    if (isLogsSheetOpen) {
                        LogsBottomSheet(
                            logs = logs,
                            onDismiss = { isLogsSheetOpen = false },
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryOptimizationStatus()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to battery optimization settings page
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }
}
