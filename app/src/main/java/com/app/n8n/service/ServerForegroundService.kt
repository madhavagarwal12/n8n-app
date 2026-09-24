package com.app.n8n.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.n8n.MainActivity
import com.app.n8n.R
import com.app.n8n.model.LogEntry
import com.app.n8n.model.ServerState
import com.app.n8n.model.SystemStats
import com.app.n8n.network.NetworkHelper
import com.app.n8n.process.N8nProcessSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ServerForegroundService : Service() {

    companion object {
        private const val TAG = "ServerForegroundService"
        const val CHANNEL_ID = "n8n_server_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.app.n8n.ACTION_START"
        const val ACTION_STOP = "com.app.n8n.ACTION_STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): ServerForegroundService = this@ServerForegroundService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var supervisor: N8nProcessSupervisor
    private lateinit var networkHelper: NetworkHelper

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var statsJob: Job? = null

    private var startTimeMillis: Long = 0L

    private val _systemStats = MutableStateFlow(SystemStats())
    val systemStats: StateFlow<SystemStats> = _systemStats.asStateFlow()

    val serverState: StateFlow<ServerState> get() = supervisor.serverState
    val logFlow: SharedFlow<LogEntry> get() = supervisor.logFlow

    override fun onCreate() {
        super.onCreate()
        supervisor = N8nProcessSupervisor(applicationContext)
        networkHelper = NetworkHelper(applicationContext)
        networkHelper.startMonitoring()

        createNotificationChannel()
        acquireLocks()

        // Observe network changes to update stats and notification
        serviceScope.launch {
            networkHelper.currentIp.collect { ip ->
                updateStats()
                if (supervisor.serverState.value == ServerState.RUNNING) {
                    updateNotification()
                }
            }
        }

        // Observe server state changes
        serviceScope.launch {
            supervisor.serverState.collect { state ->
                when (state) {
                    ServerState.RUNNING -> {
                        startTimeMillis = System.currentTimeMillis()
                        startStatsMonitoring()
                        networkHelper.registerMdns(5678)
                        updateNotification()
                    }
                    ServerState.STOPPED, ServerState.ERROR -> {
                        stopStatsMonitoring()
                        networkHelper.unregisterMdns()
                        updateNotification()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                serviceScope.launch {
                    val ip = networkHelper.currentIp.value
                    supervisor.startServer(localIp = ip, port = 5678)
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    supervisor.stopServer()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            supervisor.stopServer()
        }
        stopStatsMonitoring()
        networkHelper.stopMonitoring()
        networkHelper.unregisterMdns()
        releaseLocks()
    }

    fun startServer() {
        startForegroundWithNotification()
        serviceScope.launch {
            val ip = networkHelper.currentIp.value
            supervisor.startServer(localIp = ip, port = 5678)
        }
    }

    fun stopServer() {
        serviceScope.launch {
            supervisor.stopServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "n8n:ServerCpuWakeLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max fallback
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "n8n:ServerWifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
    }

    private fun startStatsMonitoring() {
        statsJob?.cancel()
        statsJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                updateStats()
                delay(1000)
            }
        }
    }

    private fun stopStatsMonitoring() {
        statsJob?.cancel()
        statsJob = null
        val current = _systemStats.value
        _systemStats.value = current.copy(uptimeSeconds = 0L)
    }

    private fun updateStats() {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val uptime = if (startTimeMillis > 0 && supervisor.serverState.value == ServerState.RUNNING) {
            (System.currentTimeMillis() - startTimeMillis) / 1000
        } else {
            0L
        }

        _systemStats.value = SystemStats(
            memoryUsageMb = usedMem,
            totalMemoryMb = maxMem,
            uptimeSeconds = uptime,
            localIp = networkHelper.currentIp.value,
            port = 5678,
            mdnsHost = "n8n-android.local"
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ServerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isRunning = supervisor.serverState.value == ServerState.RUNNING
        val ip = networkHelper.currentIp.value
        val url = "http://$ip:5678"

        val title = if (isRunning) "n8n Server Running" else "n8n Server Initializing"
        val content = if (isRunning) url else "Preparing background automation engine..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_stop), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }
}
