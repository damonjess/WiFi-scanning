package com.damon.wifiaudit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.damon.wifiaudit.util.DeveloperOptionsHelper

@Composable
fun ThrottleWarningBanner(isLikelyThrottled: Boolean) {
    if (!isLikelyThrottled) return
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scans appear throttled", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Android is limiting how often this app can scan Wi-Fi. " +
                "For continuous scanning, enable Developer Options and disable " +
                "\"Wi-Fi scanning throttling\" under Networking.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { DeveloperOptionsHelper.openDeveloperOptionsOrSettings(context) }) {
                Text("Open Settings")
            }
        }
    }
}
