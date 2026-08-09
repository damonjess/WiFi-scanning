package com.damon.wifiaudit.export

import android.content.Context
import com.damon.wifiaudit.data.AppDatabase
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object KmlExporter {

    suspend fun exportToKml(context: Context, outputFile: File) {
        val db = AppDatabase.getInstance(context)
        val scans = db.apScanDao().getAll()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        FileWriter(outputFile).use { writer ->
            writer.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""")
            writer.appendLine("<name>WiFi Audit Survey</name>")

            for (s in scans) {
                val safeSsid = escapeXml(s.ssid)
                val timestamp = sdf.format(Date(s.timestampMillis))
                writer.appendLine("<Placemark>")
                writer.appendLine("<name>$safeSsid</name>")
                writer.appendLine("<description>BSSID: ${s.bssid} | RSSI: ${s.rssi} dBm | Enc: ${s.encryptionType} | $timestamp</description>")
                writer.appendLine("<Point><coordinates>${s.longitude},${s.latitude},${s.altitude}</coordinates></Point>")
                writer.appendLine("</Placemark>")
            }

            writer.appendLine("</Document></kml>")
        }
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
