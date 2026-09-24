package com.app.n8n.model

data class SystemStats(
    val memoryUsageMb: Long = 0L,
    val totalMemoryMb: Long = 0L,
    val uptimeSeconds: Long = 0L,
    val localIp: String = "127.0.0.1",
    val port: Int = 5678,
    val mdnsHost: String = "n8n-android.local"
) {
    val httpUrl: String
        get() = "http://$localIp:$port"

    val mdnsUrl: String
        get() = "http://$mdnsHost:$port"

    val formattedUptime: String
        get() {
            val hours = uptimeSeconds / 3600
            val minutes = (uptimeSeconds % 3600) / 60
            val seconds = uptimeSeconds % 60
            return if (hours > 0) {
                String.format(java.util.Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.getDefault(), "%02dm %02ds", minutes, seconds)
            }
        }
}
