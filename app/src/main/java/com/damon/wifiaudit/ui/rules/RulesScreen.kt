package com.damon.wifiaudit.ui.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.data.entity.ProximityRule
import com.damon.wifiaudit.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: RulesViewModel = viewModel()) {
    val rules by viewModel.rules.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Home & Security", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F15))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* navigate to device picker or handled in scanning screen */ },
                containerColor = Color(0xFF8C9EFF),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
            }
        },
        containerColor = Color(0xFF0F0F15)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                Text(
                    "Active Monitors",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            items(rules) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { viewModel.toggle(rule) },
                    onDelete = { viewModel.delete(rule) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        "No rules yet. Tap a BLE device in the scanner to create a proximity action.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: ProximityRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when (rule.ruleType) {
        "TIME_SYNC" -> Color(0xFF81C784)
        "SECURITY_KEY" -> Color(0xFFE57373)
        else -> Color(0xFF8C9EFF)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A23)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${rule.targetMac}  ·  ${rule.ruleType.replace("_", " ")}",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
                rule.rssiThreshold?.let {
                    Text(
                        text = "Trigger when RSSI < ${it} dBm",
                        fontSize = 11.sp,
                        color = typeColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = typeColor,
                    checkedTrackColor = typeColor.copy(alpha = 0.5f)
                )
            )
        }
    }
}
