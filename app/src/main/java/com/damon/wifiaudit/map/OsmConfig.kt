package com.damon.wifiaudit.map

import android.content.Context
import org.osmdroid.config.Configuration

object OsmConfig {
    fun initialize(context: Context) {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
            
            // OSM tile servers are EXTREMELY picky about User-Agent.
            // Some block anything with "Android" in it if it doesn't look like a browser.
            // Let's use a very standard-looking one.
            userAgentValue = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            
            // Set a dedicated cache path
            val osmdroidCache = java.io.File(context.cacheDir, "osmdroid")
            if (!osmdroidCache.exists()) osmdroidCache.mkdirs()
            osmdroidTileCache = osmdroidCache
            
            // Increase thread count for tile loading
            tileDownloadThreads = 4
        }
    }
}
