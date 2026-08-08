package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val roleMode by viewModel.roleMode.collectAsState()
    val botToken by viewModel.botToken.collectAsState()
    val chatId by viewModel.chatId.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val cooldown by viewModel.cooldown.collectAsState()
    val playAlarm by viewModel.playAlarm.collectAsState()
    val autoCleanupEnabled by viewModel.autoCleanupEnabled.collectAsState()
    val livePreviewInListEnabled by viewModel.livePreviewInListEnabled.collectAsState()
    val storageLimitGB by viewModel.storageLimitGB.collectAsState()
    val maxEventCount by viewModel.maxEventCount.collectAsState()
    val cleanupStatus by viewModel.cleanupStatus.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDF8FF))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("系統與警報設定", color = Color(0xFF1C1B1F), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("設定 Telegram 機器人推播、裝置角色與動態敏感度", color = Color(0xFF49454F), fontSize = 13.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Device Role Config Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("裝置角色模式", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("可切換此裝置作為鏡頭端 (攝影機) 或觀看端 (監控螢幕)", color = Color(0xFF49454F), fontSize = 13.sp)

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isCamera = roleMode == "CAMERA" || roleMode == "UNSET"
                    Button(
                        onClick = { viewModel.updateRoleMode("CAMERA") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCamera) Color(0xFF6750A4) else Color(0xFFE8DEF8),
                            contentColor = if (isCamera) Color.White else Color(0xFF1D192B)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📷 鏡頭端", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.updateRoleMode("VIEWER") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isCamera) Color(0xFF6750A4) else Color(0xFFE8DEF8),
                            contentColor = if (!isCamera) Color.White else Color(0xFF1D192B)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📺 觀看端", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live Stream Preview Setting Card (觀看端全鏡頭即時預覽)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("觀看端全鏡頭即時預覽", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (livePreviewInListEnabled) "列表頁將持續輪詢刷新鏡頭最新畫面" else "預設使用靜態快照，節省網路流量與發熱",
                                color = Color(0xFF49454F),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = livePreviewInListEnabled,
                        onCheckedChange = { viewModel.updateLivePreviewInListEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6750A4)
                        )
                    )
                }
            }
        }

        // Telegram Bot Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Telegram 警報機器人設定", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { viewModel.updateBotToken(it) },
                    label = { Text("Telegram Bot Token") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1C1B1F),
                        unfocusedTextColor = Color(0xFF1C1B1F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = chatId,
                    onValueChange = { viewModel.updateChatId(it) },
                    label = { Text("Telegram Chat ID") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1C1B1F),
                        unfocusedTextColor = Color(0xFF1C1B1F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.testTelegram() },
                        enabled = !isTesting && botToken.isNotBlank() && chatId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.height(18.dp).width(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("測試中...", fontSize = 13.sp)
                        } else {
                            Text("測試連線", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = { viewModel.syncTelegramToCameras() },
                        enabled = botToken.isNotBlank() || chatId.isNotBlank(),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF6750A4)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔄 同步至鏡頭", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                AnimatedVisibility(visible = syncStatus != null) {
                    syncStatus?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(msg, color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                AnimatedVisibility(visible = testStatus != null) {
                    testStatus?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(msg, color = if (msg.contains("成功")) Color(0xFF2E7D32) else Color(0xFFB3261E), fontSize = 13.sp)
                    }
                }
            }
        }

        // Camera Device Config Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("本機鏡頭裝置設定", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { viewModel.updateDeviceName(it) },
                    label = { Text("裝置顯示名稱 (如 客廳鏡頭)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1C1B1F),
                        unfocusedTextColor = Color(0xFF1C1B1F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { viewModel.updateServerPort(it) },
                    label = { Text("HTTP 串流 Port (預設 8080)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1C1B1F),
                        unfocusedTextColor = Color(0xFF1C1B1F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Motion Sensitivity Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("動態警報敏感度與冷卻", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))

                Text("動態偵測敏感度 (${"%.1f".format(sensitivity)})", color = Color(0xFF49454F), fontWeight = FontWeight.Medium)
                Slider(
                    value = sensitivity,
                    onValueChange = { viewModel.updateSensitivity(it) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6750A4),
                        activeTrackColor = Color(0xFF6750A4),
                        inactiveTrackColor = Color(0xFFE8DEF8)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = cooldown,
                    onValueChange = { viewModel.updateCooldown(it) },
                    label = { Text("警報冷卻時間 (秒，避免連續轟炸)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1C1B1F),
                        unfocusedTextColor = Color(0xFF1C1B1F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("觸發時本機播放警報蜂鳴聲:", color = Color(0xFF49454F), fontWeight = FontWeight.Medium)
                    Switch(
                        checked = playAlarm,
                        onCheckedChange = { viewModel.updatePlayAlarm(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6750A4),
                            uncheckedThumbColor = Color(0xFF49454F),
                            uncheckedTrackColor = Color(0xFFE8DEF8)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage & Auto Cleanup Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("儲存空間與歷史紀錄清理", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("啟用自動儲存空間清理", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("超過上限時自動刪除日期最早的偵測影像與紀錄", color = Color(0xFF49454F), fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoCleanupEnabled,
                        onCheckedChange = { viewModel.updateAutoCleanupEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6750A4),
                            uncheckedThumbColor = Color(0xFF49454F),
                            uncheckedTrackColor = Color(0xFFE8DEF8)
                        )
                    )
                }

                if (autoCleanupEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("儲存空間上限:", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f).forEach { gbOption ->
                            val isSelected = Math.abs(storageLimitGB - gbOption) < 0.1f
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateStorageLimitGB(gbOption) },
                                label = { Text("${gbOption.toInt().let { if (gbOption < 1.0f) "500MB" else "${it}GB" }}", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE8DEF8),
                                    selectedLabelColor = Color(0xFF1D192B)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("事件數量保留上限:", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(100, 200, 500, 1000).forEach { countOption ->
                            val isSelected = maxEventCount == countOption
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateMaxEventCount(countOption) },
                                label = { Text("$countOption 筆", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE8DEF8),
                                    selectedLabelColor = Color(0xFF1D192B)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { viewModel.performManualCleanup() },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFB3261E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFB3261E), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🧹 立即清理舊快照與紀錄 (保留 80%)", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                AnimatedVisibility(visible = cleanupStatus != null) {
                    cleanupStatus?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(msg, color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
