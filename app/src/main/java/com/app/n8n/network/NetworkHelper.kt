package com.app.n8n.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkHelper(private val context: Context) {

    companion object {
        private const val TAG = "NetworkHelper"
        const val MDNS_SERVICE_TYPE = "_http._tcp."
        const val MDNS_SERVICE_NAME = "n8n-android"
        const val DEFAULT_PORT = 5678
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _currentIp = MutableStateFlow(getInitialLocalIp())
    val currentIp: StateFlow<String> = _currentIp.asStateFlow()

    private val _isMdnsRegistered = MutableStateFlow(false)
    val isMdnsRegistered: StateFlow<Boolean> = _isMdnsRegistered.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateIpAddress()
            }

            override fun onLost(network: Network) {
                updateIpAddress()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                updateIpAddress()
            }
        }

        try {
            networkCallback?.let {
                connectivityManager.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }

        updateIpAddress()
    }

    fun stopMonitoring() {
        try {
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
        networkCallback = null
    }

    fun registerMdns(port: Int = DEFAULT_PORT) {
        if (nsdManager == null || _isMdnsRegistered.value) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = MDNS_SERVICE_NAME
            serviceType = MDNS_SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.i(TAG, "mDNS Service registered: ${NsdServiceInfo.serviceName}")
                _isMdnsRegistered.value = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "mDNS Registration failed: $errorCode")
                _isMdnsRegistered.value = false
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i(TAG, "mDNS Service unregistered")
                _isMdnsRegistered.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "mDNS Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call nsdManager.registerService", e)
        }
    }

    fun unregisterMdns() {
        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering mDNS service", e)
            }
        }
        registrationListener = null
        _isMdnsRegistered.value = false
    }

    private fun updateIpAddress() {
        val ip = getInitialLocalIp()
        _currentIp.value = ip
    }

    private fun getInitialLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                // Prefer wlan0 or active wifi interfaces
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving local IP", e)
        }
        return "127.0.0.1"
    }
}
