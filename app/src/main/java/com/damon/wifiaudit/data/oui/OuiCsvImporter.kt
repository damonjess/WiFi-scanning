package com.damon.wifiaudit.data.oui

import android.content.Context
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.entity.OuiVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Imports the compact OUI master export into Room once per bundled dataset
 * revision. It keeps history queries fast and refreshes old installations that
 * already contain the smaller legacy CSV.
 */
object OuiCsvImporter {
    private const val PREFS = "oui_import"
    private const val IMPORT_VERSION_KEY = "master_import_version"
    private const val MASTER_IMPORT_VERSION = 20260811
    private const val MASTER_ASSET = "oui_master.txt"
    private const val LEGACY_CSV_ASSET = "oui.csv"
    private const val BATCH_SIZE = 1_000

    suspend fun importIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getInt(IMPORT_VERSION_KEY, 0) >= MASTER_IMPORT_VERSION) {
            return@withContext
        }

        val db = AppDatabase.getInstance(context)
        try {
            // A full refresh is deliberately used: a master assignment may
            // replace the vendor associated with an old prefix. The app keeps
            // scanning and existing sightings intact; only lookup metadata is refreshed.
            db.ouiVendorDao().clearAll()
            val imported = importMasterText(context, db)
            if (imported == 0) {
                importLegacyCsv(context, db)
            }
            preferences.edit().putInt(IMPORT_VERSION_KEY, MASTER_IMPORT_VERSION).apply()
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    private suspend fun importMasterText(context: Context, db: AppDatabase): Int {
        var imported = 0
        val batch = ArrayList<OuiVendor>(BATCH_SIZE)
        try {
            context.assets.open(MASTER_ASSET).use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach

                        val parts = trimmed.split(Regex("\\s+"), limit = 2)
                        if (parts.size < 2) return@forEach
                        val assignment = normalizeAssignment(parts[0]) ?: return@forEach
                        val vendorName = parts[1].trim()
                        if (vendorName.isEmpty()) return@forEach

                        batch += OuiVendor(oui = assignment, vendorName = vendorName)
                        if (batch.size >= BATCH_SIZE) {
                            db.ouiVendorDao().insertAll(batch)
                            imported += batch.size
                            batch.clear()
                        }
                    }
                }
            }
            if (batch.isNotEmpty()) {
                db.ouiVendorDao().insertAll(batch)
                imported += batch.size
            }
        } catch (_: Exception) {
            // The legacy asset remains available for projects that omit the master export.
            return 0
        }
        return imported
    }

    private suspend fun importLegacyCsv(context: Context, db: AppDatabase) {
        val batch = ArrayList<OuiVendor>(BATCH_SIZE)
        context.assets.open(LEGACY_CSV_ASSET).use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (index == 0) return@forEachIndexed
                    val parts = parseCsvLine(line)
                    if (parts.size < 2) return@forEachIndexed
                    val assignment = normalizeAssignment(parts[0]) ?: return@forEachIndexed
                    val vendorName = parts[1].trim().removeSurrounding("\"")
                    if (vendorName.isEmpty()) return@forEachIndexed

                    batch += OuiVendor(
                        oui = assignment,
                        vendorName = vendorName,
                        country = parts.getOrNull(2)?.trim()?.removeSurrounding("\"")
                    )
                    if (batch.size >= BATCH_SIZE) {
                        db.ouiVendorDao().insertAll(batch)
                        batch.clear()
                    }
                }
            }
        }
        if (batch.isNotEmpty()) db.ouiVendorDao().insertAll(batch)
    }

    private fun normalizeAssignment(value: String): String? {
        val token = value.substringBefore('/')
        val hex = token.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        val bits = value.substringAfter('/', missingDelimiterValue = "24").toIntOrNull() ?: 24
        val length = when (bits) {
            24 -> 6
            28 -> 7
            36 -> 9
            else -> 6
        }
        return hex.takeIf { it.length >= length }?.take(length)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}
