package com.damon.wifiaudit.data.oui

import android.content.Context
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.entity.OuiVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object OuiCsvImporter {
    suspend fun importIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        if (db.ouiVendorDao().count() > 0) return@withContext

        val vendors = mutableListOf<OuiVendor>()
        try {
            // Try to open oui.csv from assets
            context.assets.open("oui.csv").use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (index == 0) return@forEachIndexed // Skip header if exists
                        
                        // Parse CSV line safely (handle quotes)
                        val parts = parseCsvLine(line)
                        if (parts.size >= 2) {
                            val assignment = parts[0].trim().uppercase().replace(":", "").replace("-", "")
                            if (assignment.length >= 6) {
                                vendors.add(
                                    OuiVendor(
                                        oui = assignment.take(6),
                                        vendorName = parts[1].trim().removeSurrounding("\""),
                                        country = parts.getOrNull(2)?.trim()?.removeSurrounding("\"")
                                    )
                                )
                            }
                        }
                        
                        // Batch insert every 1000 items to avoid memory issues
                        if (vendors.size >= 1000) {
                            db.ouiVendorDao().insertAll(vendors.toList())
                            vendors.clear()
                        }
                    }
                }
            }
            // Final insert
            if (vendors.isNotEmpty()) {
                db.ouiVendorDao().insertAll(vendors)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}
