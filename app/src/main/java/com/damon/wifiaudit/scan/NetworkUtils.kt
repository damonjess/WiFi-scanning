package com.damon.wifiaudit.scan

import android.content.Context
import android.net.wifi.WifiManager
import java.util.Locale

object NetworkUtils {

    /**
     * Getting the subnet base from WifiManager
     */
    fun getLocalSubnetBase(context: Context): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val ipInt = dhcpInfo.ipAddress
        // Android stores this little-endian
        val ip = String.format(
            Locale.US,
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
        return if (ip != "0.0.0.0") ip.substringBeforeLast(".") else null
    }
}
