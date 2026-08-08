package com.example.ui.viewer

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.camera.ResolutionSelectionDialog
import org.json.JSONObject

@Composable
fun RemoteSettingsDialog(
    cameraName: String,
    cameraStatusJson: JSONObject?,
    onSendCommand: (String, String) -> Unit,
    onSyncTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showResPicker by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    // Read current settings from JSON
    val deviceNameStr = cameraStatusJson?.optString("deviceName", cameraName) ?: cameraName
    val currentRes = cameraStatusJson?.optString("resolution", "720p") ?: "720p"
    val currentQuality = cameraStatusJson?.optInt("quality", 60) ?: 60
    val isMotion = cameraStatusJson?.optBoolean("isMotionDetectionEnabled", false) ?: false
    val nightMode = cameraStatusJson?.optString("nightVisionMode", "auto") ?: "auto"
    val nightLuma = cameraStatusJson?.optDouble("nightVisionLuma", 45.0)?.toFloat() ?: 45.0f
    val currentOpMode = cameraStatusJson?.optString("operatingMode", "monitor") ?: "monitor"
    val isTorchOn = cameraStatusJson?.optBoolean("isTorchOn", false) ?: false
    val lensFacing = cameraStatusJson?.optString("lensFacing", "back") ?: "back"

    val motionSensitivity = cameraStatusJson?.optDouble("motionSensitivity", 5.0)?.toFloat() ?: 5.0f
    val motionCooldown = cameraStatusJson?.optInt("motionCooldown", 30) ?: 30
    val playLocalAlarm = cameraStatusJson?.optBoolean("playLocalAlarmOnMotion", false) ?: false
    val mlKitEnabled = cameraStatusJson?.optBoolean("mlKitFilterEnabled", true) ?: true

    val autoCleanup = cameraStatusJson?.optBoolean("autoStorageCleanupEnabled", true) ?: true
    val storageLimitGB = cameraStatusJson?.optDouble("storageLimitGB", 2.0)?.toFloat() ?: 2.0f
    val maxEventCount = cameraStatusJson?.optInt("maxEventCountLimit", 200) ?: 200

    // Local mutable state for sliders
    var editingName by remember(deviceNameStr) { mutableStateOf(deviceNameStr) }
    var localQuality by remember(currentQuality) { mutableFloatStateOf(currentQuality.toFloat()) }
    var localSens by remember(motionSensitivity) { mutableFloatStateOf(motionSensitivity) }
    var localCooldown by remember(motionCooldown) { mutableFloatStateOf(motionCooldown.toFloat()) }
    var localNightLuma by remember(nightLuma) { mutableFloatStateOf(nightLuma) }
    var localStorageGB by remember(storageLimitGB) { mutableFloatStateOf(storageLimitGB) }
    var localMaxEvents by remember(maxEventCount) { mutableFloatStateOf(maxEventCount.toFloat()) }

    if (showResPicker) {
        ResolutionSelectionDialog(
            currentResolution = currentRes,
            onSelect = { res ->
                onSendCommand("resolution", res)
                Toast.makeText(context, "已變更遠端解析度為 $res", Toast.LENGTH_SHORT).show()
                showResPicker = false
            },
            onDismiss = { showResPicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚙️ 鏡頭端遠端偏好設定",
                        color = onSurfaceColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "正在控制: $deviceNameStr",
                    fontSize = 12.sp,
                    color = primaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column {
                // Category Tabs
                val tabTitles = listOf("📷 畫質/模式", "🚨 動態偵測", "💾 儲存與事件", "✈️ Telegram")
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = primaryColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == index) primaryColor else onSurfaceVariantColor) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // TAB 1: Camera & Quality
                            Text("裝置重命名", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editingName,
                                    onValueChange = { editingName = it },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = onSurfaceColor,
                                        unfocusedTextColor = onSurfaceColor
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        onSendCommand("device_name", editingName)
                                        Toast.makeText(context, "已更新鏡頭名稱: $editingName", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("變更", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text("運作模式", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isMon = currentOpMode == "monitor"
                                Button(
                                    onClick = {
                                        onSendCommand("mode", "monitor")
                                        Toast.makeText(context, "已設為監看模式", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMon) primaryColor else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (isMon) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("👁️ 監看模式", fontSize = 11.sp, fontWeight = if (isMon) FontWeight.Bold else FontWeight.Normal)
                                }
                                Button(
                                    onClick = {
                                        onSendCommand("mode", "detection")
                                        Toast.makeText(context, "已設為動態偵測模式", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isMon) primaryColor else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (!isMon) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🚨 動態偵測", fontSize = 11.sp, fontWeight = if (!isMon) FontWeight.Bold else FontWeight.Normal)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Switch Camera / Torch Buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        onSendCommand("camera", "switch")
                                        Toast.makeText(context, "已切換前後鏡頭", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("切換鏡頭 (${if (lensFacing == "back") "後" else "前"})", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        onSendCommand("torch", if (isTorchOn) "off" else "on")
                                        Toast.makeText(context, if (isTorchOn) "已關閉閃光燈" else "已開啟閃光燈", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTorchOn) Color(0xFFE2A03F) else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (isTorchOn) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isTorchOn) "關閉補光燈" else "開啟補光燈", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Resolution
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("畫面解析度", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("降低解析度可顯著節省傳輸頻寬", fontSize = 11.sp, color = onSurfaceVariantColor)
                                }
                                OutlinedButton(
                                    onClick = { showResPicker = true },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.5.dp, primaryColor)
                                ) {
                                    Text("📹 $currentRes ▾", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Quality Slider
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("JPEG 壓縮品質", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("${localQuality.toInt()}%", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Slider(
                                    value = localQuality,
                                    onValueChange = { localQuality = it },
                                    onValueChangeFinished = {
                                        onSendCommand("quality", "${localQuality.toInt()}")
                                        Toast.makeText(context, "品質已設定為: ${localQuality.toInt()}%", Toast.LENGTH_SHORT).show()
                                    },
                                    valueRange = 30f..90f,
                                    steps = 5,
                                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Night Vision
                            Text("夜視模式", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val modes = listOf("off" to "關閉", "on" to "黑白夜視", "auto" to "自動切換")
                                modes.forEach { (modeKey, label) ->
                                    val isSelected = nightMode.equals(modeKey, ignoreCase = true)
                                    Button(
                                        onClick = {
                                            onSendCommand("night_vision", modeKey)
                                            Toast.makeText(context, "夜視模式設為: $label", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            if (nightMode == "auto") {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("自動夜視切換亮度閥值", fontSize = 12.sp, color = onSurfaceVariantColor)
                                        Text("${localNightLuma.toInt()} Luma", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Slider(
                                        value = localNightLuma,
                                        onValueChange = { localNightLuma = it },
                                        onValueChangeFinished = {
                                            onSendCommand("night_vision_luma", "${localNightLuma.toInt()}")
                                            Toast.makeText(context, "夜視亮度閥值已設為: ${localNightLuma.toInt()}", Toast.LENGTH_SHORT).show()
                                        },
                                        valueRange = 10f..100f,
                                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                    )
                                }
                            }
                        }

                        1 -> {
                            // TAB 2: Motion Detection & Alarm
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("啟用智慧動態偵測", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("鏡頭異動時自動記錄與告警", fontSize = 11.sp, color = onSurfaceVariantColor)
                                }
                                Switch(
                                    checked = isMotion,
                                    onCheckedChange = { checked ->
                                        onSendCommand("motion", if (checked) "on" else "off")
                                        Toast.makeText(context, if (checked) "已開啟遠端動態偵測" else "已關閉遠端動態偵測", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Motion Sensitivity
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("動態偵測靈敏度", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text(String.format("%.1f", localSens), color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text("數值越高越敏感（1.0 = 微動忽略, 10.0 = 極度敏感）", fontSize = 11.sp, color = onSurfaceVariantColor)
                                Slider(
                                    value = localSens,
                                    onValueChange = { localSens = it },
                                    onValueChangeFinished = {
                                        onSendCommand("sensitivity", String.format("%.1f", localSens))
                                        Toast.makeText(context, "靈敏度設為: ${String.format("%.1f", localSens)}", Toast.LENGTH_SHORT).show()
                                    },
                                    valueRange = 1.0f..10.0f,
                                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Cooldown
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("告警冷卻時間", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("${localCooldown.toInt()} 秒", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text("觸發告警後暫停再次告警的時間間隔", fontSize = 11.sp, color = onSurfaceVariantColor)
                                Slider(
                                    value = localCooldown,
                                    onValueChange = { localCooldown = it },
                                    onValueChangeFinished = {
                                        onSendCommand("cooldown", "${localCooldown.toInt()}")
                                        Toast.makeText(context, "冷卻時間設為: ${localCooldown.toInt()} 秒", Toast.LENGTH_SHORT).show()
                                    },
                                    valueRange = 5f..120f,
                                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // ML Kit Filter Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Google ML Kit AI 物體/寵物過濾", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("辨識寵物與人體，減少風吹樹影誤報", fontSize = 11.sp, color = onSurfaceVariantColor)
                                }
                                Switch(
                                    checked = mlKitEnabled,
                                    onCheckedChange = { checked ->
                                        onSendCommand("mlkit_filter", if (checked) "on" else "off")
                                        Toast.makeText(context, if (checked) "已啟用 AI ML Kit 過濾" else "已關閉 AI 過濾", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Play Local Alarm Switch & Test Alarm
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("異動時鏡頭端發出響聲", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("偵測異動時鏡頭手機發出警告音", fontSize = 11.sp, color = onSurfaceVariantColor)
                                }
                                Switch(
                                    checked = playLocalAlarm,
                                    onCheckedChange = { checked ->
                                        onSendCommand("play_alarm_setting", if (checked) "on" else "off")
                                        Toast.makeText(context, if (checked) "已開啟現場警報聲" else "已關閉現場警報聲", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    onSendCommand("alarm", "trigger")
                                    Toast.makeText(context, "🚨 已發送測試警報指令至鏡頭端", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E)),
                                border = BorderStroke(1.dp, Color(0xFFB3261E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🚨 測試鏡頭端發聲響", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        2 -> {
                            // TAB 3: Storage & Cleanup
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("空間自動循環清理 (Loop Storage)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("空間或筆數超標時自動刪除最舊紀錄", fontSize = 11.sp, color = onSurfaceVariantColor)
                                }
                                Switch(
                                    checked = autoCleanup,
                                    onCheckedChange = { checked ->
                                        onSendCommand("auto_cleanup", if (checked) "on" else "off")
                                        Toast.makeText(context, if (checked) "已開啟自動循環清理" else "已關閉自動循環清理", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Storage Limit GB
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("儲存空間上限", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text(String.format("%.1f GB", localStorageGB), color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Slider(
                                    value = localStorageGB,
                                    onValueChange = { localStorageGB = it },
                                    onValueChangeFinished = {
                                        onSendCommand("storage_limit_gb", String.format("%.1f", localStorageGB))
                                        Toast.makeText(context, "儲存容量上限設為: ${String.format("%.1f GB", localStorageGB)}", Toast.LENGTH_SHORT).show()
                                    },
                                    valueRange = 0.5f..10.0f,
                                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Max Events Limit
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("最高留存事件筆數", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                                    Text("${localMaxEvents.toInt()} 筆", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Slider(
                                    value = localMaxEvents,
                                    onValueChange = { localMaxEvents = it },
                                    onValueChangeFinished = {
                                        onSendCommand("max_event_count", "${localMaxEvents.toInt()}")
                                        Toast.makeText(context, "最高事件筆數設為: ${localMaxEvents.toInt()} 筆", Toast.LENGTH_SHORT).show()
                                    },
                                    valueRange = 50f..500f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                                )
                            }
                        }

                        3 -> {
                            // TAB 4: Telegram Sync
                            val remoteBotToken = cameraStatusJson?.optString("telegramBotToken", "") ?: ""
                            val remoteChatId = cameraStatusJson?.optString("telegramChatId", "") ?: ""
                            val hasRemoteTelegram = remoteBotToken.isNotBlank() && remoteChatId.isNotBlank()

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (hasRemoteTelegram) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (hasRemoteTelegram) "✅ 鏡頭端已綁定 Telegram Bot" else "⚠️ 鏡頭端尚未設定 Telegram 告警",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (hasRemoteTelegram) Color(0xFF2E7D32) else Color(0xFFB3261E)
                                    )
                                    if (hasRemoteTelegram) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Chat ID: $remoteChatId", fontSize = 11.sp, color = Color(0xFF1B5E20))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text("一鍵同步設定", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = onSurfaceColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("將目前觀看端的 Telegram Bot Token 與 Chat ID 推送給這台鏡頭裝置：", fontSize = 11.sp, color = onSurfaceVariantColor)
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    onSyncTelegram()
                                    Toast.makeText(context, "已將觀看端 Telegram 設定推送至該鏡頭", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("⚡ 同步觀看端 Telegram 設定至本鏡頭", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成關閉", color = primaryColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}
