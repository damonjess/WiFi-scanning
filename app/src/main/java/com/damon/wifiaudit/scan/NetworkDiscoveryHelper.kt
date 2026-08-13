package com.damon.wifiaudit.scan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URL

class NetworkDiscoveryHelper(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    data class NetworkSummary(
        val ssid: String,
        val localIp: String,
        val gateway: String,
        val dns: String,
        val publicIp: String = "Detecting..."
    )

    fun getNetworkSummary(): NetworkSummary {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

        val ssid = if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            wifiManager.connectionInfo.ssid.removeSurrounding("\"")
        } else "Not a WiFi Network"

        val localIp = linkProperties?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress ?: "Unknown"
        val gateway = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "Unknown"
        val dns = linkProperties?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" } ?: "Unknown"

        return NetworkSummary(ssid, localIp, gateway, dns)
    }

    suspend fun getPublicIp(): String = withContext(Dispatchers.IO) {
        try {
            URL("https://api.ipify.org").readText()
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun discoverMDNS() = callbackFlow {
        val serviceTypes = listOf(
            "_onvif._tcp",
            "_axis-video._tcp",
            "_http._tcp",
            "_rtsp._tcp",
            "_printer._tcp",
            "_services._dns-sd._udp"
        )

        val listeners = serviceTypes.map { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            trySend(resolvedServiceInfo)
                        }
                    })
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(regType: String) {}
                override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {}
            }
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        }

        awaitClose {
            listeners.forEach { nsdManager.stopServiceDiscovery(it) }
        }
    }
}
