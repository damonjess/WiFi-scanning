package com.damon.wifiaudit.map

import android.content.Context
import org.osmdroid.config.Configuration

object OsmConfig {
    fun initialize(context: Context) {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
            
            // OSM tile servers require a descriptive User-Agent.
            userAgentValue = "${context.packageName} (wardriving app; contact@damon.com)"
            
            // Set a dedicated cache path
            val osmdroidCache = java.io.File(context.cacheDir, "osmdroid")
            if (!osmdroidCache.exists()) osmdroidCache.mkdirs()
            osmdroidTileCache = osmdroidCache
            
            // Increase thread count for tile loading
            tileDownloadThreads = 4
        }
    }
}
