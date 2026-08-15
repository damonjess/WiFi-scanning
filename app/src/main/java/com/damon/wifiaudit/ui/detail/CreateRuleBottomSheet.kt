package com.damon.wifiaudit.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.ble.ProximityMonitorService
import com.damon.wifiaudit.data.entity.ProximityRule
import com.damon.wifiaudit.ui.DeviceDetailViewModel
import com.damon.wifiaudit.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRuleBottomSheet(
    mac: String,
    deviceName: String,
    onDismiss: () -> Unit,
    viewModel: DeviceDetailViewModel
) {
    val scope = rememberCoroutineScope()
    var ruleName by remember { mutableStateOf(deviceName.take(20)) }
    var ruleType by remember { mutableStateOf("PROXIMITY_ACTION") }
    var rssiThreshold by remember { mutableStateOf("-75") }
    var actionHex by remember { mutableStateOf("") }
    var lockOnExit by remember { mutableStateOf(false) }
    var enableNotification by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A23)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Create Proximity Rule",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Rule Type
            Text("Rule Type", fontSize = 12.sp, color = TextMuted)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                RuleTypeChip("Smart Home", ruleType == "PROXIMITY_ACTION") {
                    ruleType = "PROXIMITY_ACTION"
                }
                Spacer(modifier = Modifier.width(8.dp))
                RuleTypeChip("Time Sync", ruleType == "TIME_SYNC") {
                    ruleType = "TIME_SYNC"
                }
                Spacer(modifier = Modifier.width(8.dp))
                RuleTypeChip("Security Key", ruleType == "SECURITY_KEY") {
                    ruleType = "SECURITY_KEY"
                }
            }

            // Name
            OutlinedTextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text("Rule Name") },
                colors = textFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // RSSI Threshold
            OutlinedTextField(
                value = rssiThreshold,
                onValueChange = { rssiThreshold = it },
                label = { Text("RSSI Threshold (dBm)") },
                placeholder = { Text("-75 = trigger when weaker than this") },
                colors = textFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action-specific fields
            if (ruleType == "PROXIMITY_ACTION") {
                OutlinedTextField(
                    value = actionHex,
                    onValueChange = { actionHex = it },
                    label = { Text("Write Payload (hex)") },
                    placeholder = { Text("01 FF A4") },
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = enableNotification,
                        onCheckedChange = { enableNotification = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF8C9EFF))
                    )
                    Text("Show notification on trigger", fontSize = 13.sp, color = Color.White)
                }
            }

            if (ruleType == "SECURITY_KEY") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = lockOnExit,
                        onCheckedChange = { lockOnExit = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE57373))
                    )
                    Text("Lock app when device out of range", fontSize = 13.sp, color = Color.White)
                }
            }

            if (ruleType == "TIME_SYNC") {
                Text(
                    "Will auto-write Current Time (0x1805) when device is in range.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        val rule = ProximityRule(
                            name = ruleName,
                            targetMac = mac,
                            ruleType = ruleType,
                            rssiThreshold = rssiThreshold.toIntOrNull(),
                            writePayloadHex = actionHex.takeIf { it.isNotBlank() },
                            showNotification = enableNotification,
                            lockAppOnExit = lockOnExit
                        )
                        viewModel.db.proximityRuleDao().insert(rule)
                        ProximityMonitorService.start(viewModel.getApplication()) // restart service
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C9EFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Activate", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RuleTypeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF8C9EFF).copy(alpha = 0.25f) else Color(0xFF2A2A35),
        border = BorderStroke(1.dp, if (selected) Color(0xFF8C9EFF) else Color.Transparent)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF8C9EFF) else Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF0F0F15),
    unfocusedContainerColor = Color(0xFF0F0F15),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedIndicatorColor = Color(0xFF8C9EFF),
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.1f)
)
