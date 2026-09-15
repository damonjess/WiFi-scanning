package com.damon.wifiaudit.scan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive

class MdnsDiscoveryHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val TAG = "MdnsDiscoveryHelper"

    fun discoverServices(serviceType: String = "_http._tcp.") = callbackFlow<Pair<String, String>> {
        // Queue of discovered services waiting to be resolved. NsdManager only
        // allows a limited number of concurrent resolveService calls; resolving
        // all found services at once causes onResolveFailed(FAILURE_ALREADY_ACTIVE).
        val resolveQueue = Channel<NsdServiceInfo>(capacity = Channel.UNLIMITED)

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS Discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service found: ${serviceInfo.serviceName}")
                // Enqueue for sequential resolution instead of resolving immediately.
                resolveQueue.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS Discovery start failed: $errorCode")
                resolveQueue.close()
                close(Exception("Discovery failed: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        // Process resolve requests one at a time. Each resolve must complete
        // (success or failure) before we start the next one, otherwise
        // NsdManager rejects with FAILURE_ALREADY_ACTIVE.
        val resolverJob = launch {
            for (serviceInfo in resolveQueue) {
                if (!isActive) break
                val resolved = CompletableDeferred<NsdServiceInfo?>()

                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "mDNS Resolve failed: $errorCode for ${si.serviceName}")
                            resolved.complete(null)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            resolved.complete(serviceInfo)
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "mDNS resolve exception", e)
                    continue
                }

                // Wait for this resolve to finish (with timeout) before starting the next.
                val result = withTimeoutOrNull(5000L) { resolved.await() }
                if (result != null) {
                    val host = result.host?.hostAddress
                    if (host != null) {
                        val info = "${result.serviceName} (${result.serviceType})"
                        trySend(host to info)
                    } else {
                        Log.w(TAG, "mDNS resolved but host is null: ${result.serviceName}")
                    }
                }
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            resolveQueue.close()
            resolverJob.cancel()
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {}
        }
    }
}
