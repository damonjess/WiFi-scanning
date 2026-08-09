package com.damon.wifiaudit.map

import android.content.Context
import org.osmdroid.config.Configuration

object OsmConfig {
    fun initialize(context: Context) {
        Configuration.getInstance().apply {
            // MUST be your real applicationId, not a placeholder —
            // OSM tile servers block generic/example package names
            userAgentValue = context.packageName
            load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
        }
    }
}
