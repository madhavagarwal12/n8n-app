package com.app.n8n.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.n8n.extractor.ExtractionProgress
import com.app.n8n.extractor.FileExtractor
import com.app.n8n.model.LogEntry
import com.app.n8n.model.LogLevel
import com.app.n8n.model.ServerState
import com.app.n8n.model.SystemStats
import com.app.n8n.service.ServerForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = FileExtractor(application)

    private val _serverState = MutableStateFlow(
        if (extractor.isInstalled()) ServerState.STOPPED else ServerState.NOT_INSTALLED
    )
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _systemStats = MutableStateFlow(SystemStats())
    val systemStats: StateFlow<SystemStats> = _systemStats.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _extractionProgress = MutableStateFlow(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()

    private val _extractionStatusMessage = MutableStateFlow("Preparing setup...")
    val extractionStatusMessage: StateFlow<String> = _extractionStatusMessage.asStateFlow()

    private val _extractionError = MutableStateFlow<String?>(null)
    val extractionError: StateFlow<String?> = _extractionError.asStateFlow()

    private val _isBatteryOptimized = MutableStateFlow(checkBatteryOptimization())
    val isBatteryOptimized: StateFlow<Boolean> = _isBatteryOptimized.asStateFlow()

    private var boundService: ServerForegroundService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ServerForegroundService.LocalBinder
            boundService = binder?.getService()
            isBound = true

            boundService?.let { s ->
                viewModelScope.launch {
                    s.serverState.collect { state ->
                        _serverState.value = state
                    }
                }
                viewModelScope.launch {
                    s.systemStats.collect { stats ->
                        _systemStats.value = stats
                    }
                }
                viewModelScope.launch {
                    s.logFlow.collect { entry ->
                        _logs.value = _logs.value + entry
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
        }
    }

    init {
        checkAndPerformInstallation()
        bindService()
    }

    fun checkAndPerformInstallation() {
        if (!extractor.isInstalled()) {
            _serverState.value = ServerState.EXTRACTING
            _extractionError.value = null
            viewModelScope.launch {
                extractor.extractPayload().collect { progress ->
                    when (progress) {
                        is ExtractionProgress.Progress -> {
                            _extractionProgress.value = progress.percentage
                            _extractionStatusMessage.value = progress.currentTask
                        }
                        is ExtractionProgress.Completed -> {
                            _serverState.value = ServerState.STOPPED
                            addLog(LogEntry(message = "Automated rootfs environment initialized.", level = LogLevel.INFO))
                        }
                        is ExtractionProgress.Failed -> {
                            _serverState.value = ServerState.ERROR
                            _extractionError.value = progress.error.localizedMessage ?: "Installation failed"
                            addLog(LogEntry(message = "Extraction failure: ${progress.error.message}", level = LogLevel.ERROR))
                        }
                    }
                }
            }
        } else {
            _serverState.value = ServerState.STOPPED
        }
    }

    fun startServer() {
        val app = getApplication<Application>()
        val startIntent = Intent(app, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(startIntent)
        } else {
            app.startService(startIntent)
        }
    }

    fun stopServer() {
        boundService?.stopServer() ?: run {
            val app = getApplication<Application>()
            val stopIntent = Intent(app, ServerForegroundService::class.java).apply {
                action = ServerForegroundService.ACTION_STOP
            }
            app.startService(stopIntent)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun refreshBatteryOptimizationStatus() {
        _isBatteryOptimized.value = checkBatteryOptimization()
    }

    private fun checkBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        }
        return false
    }

    private fun bindService() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, ServerForegroundService::class.java)
            app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error binding service", e)
        }
    }

    private fun addLog(entry: LogEntry) {
        _logs.value = _logs.value + entry
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                // ignore unbind errors on teardown
            }
            isBound = false
        }
    }
}
