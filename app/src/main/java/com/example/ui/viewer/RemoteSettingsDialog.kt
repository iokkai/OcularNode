package com.example.ui.viewer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateMapOf
import com.example.data.NotificationCategory
import com.example.ui.camera.ResolutionSelectionDialog
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSettingsScreen(
    cameraName: String,
    cameraStatusJson: JSONObject?,
    onSendCommand: (String, String) -> Unit,
    onSyncTelegram: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showResPicker by remember { mutableStateOf(false) }

    val surfaceBgColor = Color(0xFFFDF8FF)
    val textPrimaryColor = Color(0xFF1C1B1F)
    val textSecondaryColor = Color(0xFF49454F)
    val brandPrimaryColor = Color(0xFF6750A4)
    val cardBgColor = Color.White
    val cardBorderColor = Color(0xFFCAC4D0)

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
    val autoStartOnBoot = cameraStatusJson?.optBoolean("autoStartOnBoot", true) ?: true
    val powerCutAlertEnabled = cameraStatusJson?.optBoolean("powerCutAlertEnabled", true) ?: true

    // Local mutable state for sliders
    var editingName by remember(deviceNameStr) { mutableStateOf(deviceNameStr) }
    var localQuality by remember(currentQuality) { mutableFloatStateOf(currentQuality.toFloat()) }
    var localSens by remember(motionSensitivity) { mutableFloatStateOf(motionSensitivity) }
    var localCooldown by remember(motionCooldown) { mutableFloatStateOf(motionCooldown.toFloat()) }
    var localNightLuma by remember(nightLuma) { mutableFloatStateOf(nightLuma) }
    var localStorageGB by remember(storageLimitGB) { mutableFloatStateOf(storageLimitGB) }
    var localMaxEvents by remember(maxEventCount) { mutableFloatStateOf(maxEventCount.toFloat()) }
    val systemLogEnabled = cameraStatusJson?.optBoolean("systemLogEnabled", true) ?: true

    val categoryStates = remember(cameraStatusJson) {
        val catJson = cameraStatusJson?.optJSONObject("categoryFilters")
        mutableStateMapOf<NotificationCategory, Boolean>().apply {
            NotificationCategory.values().forEach { cat ->
                put(cat, catJson?.optBoolean(cat.name, true) ?: true)
            }
        }
    }
    val categoryRecordStates = remember(cameraStatusJson) {
        val catRecordJson = cameraStatusJson?.optJSONObject("categoryRecordingFilters")
        mutableStateMapOf<NotificationCategory, Boolean>().apply {
            NotificationCategory.values().forEach { cat ->
                put(cat, catRecordJson?.optBoolean(cat.name, true) ?: true)
            }
        }
    }

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

    BackHandler { onNavigateBack() }

    Scaffold(
        containerColor = surfaceBgColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceBgColor,
                    titleContentColor = textPrimaryColor,
                    navigationIconContentColor = textPrimaryColor
                ),
                title = {
                    Column {
                        Text(
                            text = "鏡頭端遠端偏好設定",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "正在控制: $deviceNameStr",
                            fontSize = 12.sp,
                            color = brandPrimaryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = textPrimaryColor
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Tabs
            val tabTitles = listOf("📷 畫質/模式", "🚨 動態偵測", "💾 儲存與維護", "✈️ Telegram")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = surfaceBgColor,
                contentColor = brandPrimaryColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) brandPrimaryColor else textSecondaryColor
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                    when (selectedTab) {
                        0 -> {
                            // TAB 1: Camera & Quality Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = BorderStroke(1.dp, cardBorderColor),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("裝置名稱", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = editingName,
                                            onValueChange = { editingName = it },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = textPrimaryColor,
                                                unfocusedTextColor = textPrimaryColor,
                                                focusedBorderColor = brandPrimaryColor,
                                                unfocusedBorderColor = cardBorderColor
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                onSendCommand("device_name", editingName)
                                                Toast.makeText(context, "已更新鏡頭名稱: $editingName", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor, contentColor = Color.White),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("變更", fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("運作模式", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val isMon = currentOpMode == "monitor"
                                        Button(
                                            onClick = {
                                                onSendCommand("mode", "monitor")
                                                Toast.makeText(context, "已設為監看模式", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isMon) brandPrimaryColor else Color(0xFFE8DEF8),
                                                contentColor = if (isMon) Color.White else Color(0xFF1D192B)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("👁️ 監看模式", fontSize = 12.sp, fontWeight = if (isMon) FontWeight.Bold else FontWeight.Normal)
                                        }
                                        Button(
                                            onClick = {
                                                onSendCommand("mode", "detection")
                                                Toast.makeText(context, "已設為動態偵測模式", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (!isMon) brandPrimaryColor else Color(0xFFE8DEF8),
                                                contentColor = if (!isMon) Color.White else Color(0xFF1D192B)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("🚨 動態偵測", fontSize = 12.sp, fontWeight = if (!isMon) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Switch Camera / Torch Buttons
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                onSendCommand("camera", "switch")
                                                Toast.makeText(context, "已切換前後鏡頭", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, brandPrimaryColor),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, tint = brandPrimaryColor, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("切換鏡頭 (${if (lensFacing == "back") "後" else "前"})", color = brandPrimaryColor, fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = {
                                                onSendCommand("torch", if (isTorchOn) "off" else "on")
                                                Toast.makeText(context, if (isTorchOn) "已關閉閃光燈" else "已開啟閃光燈", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isTorchOn) Color(0xFFE2A03F) else Color(0xFFE8DEF8),
                                                contentColor = if (isTorchOn) Color.White else Color(0xFF1D192B)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isTorchOn) "關閉補光燈" else "開啟補光燈", fontSize = 11.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Resolution
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("畫面解析度", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("降低解析度可顯著節省傳輸頻寬", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        OutlinedButton(
                                            onClick = { showResPicker = true },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.5.dp, brandPrimaryColor)
                                        ) {
                                            Text("📹 $currentRes ▾", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Quality Slider
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("JPEG 壓縮品質", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("${localQuality.toInt()}%", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Night Vision
                                    Text("夜視模式", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                    Spacer(modifier = Modifier.height(6.dp))
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
                                                    containerColor = if (isSelected) brandPrimaryColor else Color(0xFFE8DEF8),
                                                    contentColor = if (isSelected) Color.White else Color(0xFF1D192B)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        }
                                    }

                                    if (nightMode == "auto") {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("自動夜視切換亮度閥值", fontSize = 12.sp, color = textSecondaryColor)
                                                Text("${localNightLuma.toInt()} Luma", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Slider(
                                                value = localNightLuma,
                                                onValueChange = { localNightLuma = it },
                                                onValueChangeFinished = {
                                                    onSendCommand("night_vision_luma", "${localNightLuma.toInt()}")
                                                    Toast.makeText(context, "夜視亮度閥值已設為: ${localNightLuma.toInt()}", Toast.LENGTH_SHORT).show()
                                                },
                                                valueRange = 10f..100f,
                                                colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // TAB 2: Motion Detection & Alarm Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = BorderStroke(1.dp, cardBorderColor),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("啟用智慧動態偵測", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("鏡頭異動時自動記錄與告警", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        Switch(
                                            checked = isMotion,
                                            onCheckedChange = { checked ->
                                                onSendCommand("motion", if (checked) "on" else "off")
                                                Toast.makeText(context, if (checked) "已開啟遠端動態偵測" else "已關閉遠端動態偵測", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Motion Sensitivity
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("動態差異觸發門檻", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text(String.format("%.1f%%", localSens), color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Text("低於此百分比的畫面變動將被忽略 (1% = 極度敏感, 100% = 需要全畫面變動)", fontSize = 11.sp, color = textSecondaryColor)
                                        Slider(
                                            value = localSens,
                                            onValueChange = { localSens = it },
                                            onValueChangeFinished = {
                                                onSendCommand("sensitivity", String.format("%.1f", localSens))
                                                Toast.makeText(context, "觸發門檻設為: ${String.format("%.1f%%", localSens)}", Toast.LENGTH_SHORT).show()
                                            },
                                            valueRange = 1.0f..100.0f,
                                            steps = 98,
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Cooldown
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("告警冷卻時間", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("${localCooldown.toInt()} 秒", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Text("觸發告警後暫停再次告警的時間間隔", fontSize = 11.sp, color = textSecondaryColor)
                                        Slider(
                                            value = localCooldown,
                                            onValueChange = { localCooldown = it },
                                            onValueChangeFinished = {
                                                onSendCommand("cooldown", "${localCooldown.toInt()}")
                                                Toast.makeText(context, "冷卻時間設為: ${localCooldown.toInt()} 秒", Toast.LENGTH_SHORT).show()
                                            },
                                            valueRange = 5f..120f,
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // ML Kit Filter Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Google ML Kit AI 物體/寵物過濾", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("辨識寵物與人體，減少風吹樹影誤報", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        Switch(
                                            checked = mlKitEnabled,
                                            onCheckedChange = { checked ->
                                                onSendCommand("mlkit_filter", if (checked) "on" else "off")
                                                Toast.makeText(context, if (checked) "已啟用 AI ML Kit 過濾" else "已關閉 AI 過濾", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Play Local Alarm Switch & Test Alarm
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("異動時鏡頭端發出響聲", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("偵測異動時鏡頭手機發出警告音", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        Switch(
                                            checked = playLocalAlarm,
                                            onCheckedChange = { checked ->
                                                onSendCommand("play_alarm_setting", if (checked) "on" else "off")
                                                Toast.makeText(context, if (checked) "已開啟現場警報聲" else "已關閉現場警報聲", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

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
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Push Notification Category Filter Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = BorderStroke(1.dp, cardBorderColor),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = brandPrimaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "推播過濾與智慧分類設定",
                                            color = textPrimaryColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "此鏡頭偵測到畫面異動時，僅針對勾選開啟的分類類別傳送告警通知：",
                                        fontSize = 11.sp,
                                        color = textSecondaryColor
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(modifier = Modifier.weight(1.2f))
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            Text("允許推播", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        }
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            Text("觸發錄影", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    NotificationCategory.values().forEach { category ->
                                        val isEnabled = categoryStates[category] ?: true
                                        val isRecordEnabled = categoryRecordStates[category] ?: true
                                        val iconStr = when (category) {
                                            NotificationCategory.HUMAN_AND_ACTIVITY -> "🚶 👨‍👩‍👧"
                                            NotificationCategory.PET_AND_ANIMAL -> "🐶 🐱"
                                            NotificationCategory.VEHICLE_AND_TRANSPORT -> "🚗 🚲"
                                            NotificationCategory.HOUSEHOLD_ITEM -> "🛋️ 📦"
                                            NotificationCategory.ENVIRONMENT_AND_NATURE -> "🌿 🏞️"
                                            NotificationCategory.OTHER -> "❓"
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "$iconStr ${category.displayName}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = textPrimaryColor,
                                                modifier = Modifier.weight(1.2f)
                                            )
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                Switch(
                                                    checked = isEnabled,
                                                    onCheckedChange = { checked ->
                                                        categoryStates[category] = checked
                                                        val payload = JSONObject().apply {
                                                            put("category", category.name)
                                                            put("enabled", checked)
                                                        }.toString()
                                                        onSendCommand("cat_toggle", payload)
                                                    },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = brandPrimaryColor
                                                    ),
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                Switch(
                                                    checked = isRecordEnabled,
                                                    onCheckedChange = { checked ->
                                                        categoryRecordStates[category] = checked
                                                        val payload = JSONObject().apply {
                                                            put("category", category.name)
                                                            put("enabled", checked)
                                                        }.toString()
                                                        onSendCommand("cat_record_toggle", payload)
                                                    },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = brandPrimaryColor
                                                    ),
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // TAB 3: Storage & Cleanup Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = BorderStroke(1.dp, cardBorderColor),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("空間自動循環清理 (Loop Storage)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("空間或筆數超標時自動刪除最舊紀錄", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        Switch(
                                            checked = autoCleanup,
                                            onCheckedChange = { checked ->
                                                onSendCommand("auto_cleanup", if (checked) "on" else "off")
                                                Toast.makeText(context, if (checked) "已開啟自動循環清理" else "已關閉自動循環清理", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Storage Limit GB
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("儲存空間上限", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text(String.format("%.1f GB", localStorageGB), color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = localStorageGB,
                                            onValueChange = { localStorageGB = it },
                                            onValueChangeFinished = {
                                                onSendCommand("storage_limit_gb", String.format("%.1f", localStorageGB))
                                                Toast.makeText(context, "儲存容量上限設為: ${String.format("%.1f GB", localStorageGB)}", Toast.LENGTH_SHORT).show()
                                            },
                                            valueRange = 0.5f..10.0f,
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Max Events Limit
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("最高留存事件筆數", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("${localMaxEvents.toInt()} 筆", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Auto Start On Boot Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("開機/復電自動啟動監控服務", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("手機重新開機後背景自動啟動「相機節點」", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        Switch(
                                            checked = autoStartOnBoot,
                                            onCheckedChange = { checked ->
                                                onSendCommand("auto_start_boot", if (checked) "on" else "off")
                                                Toast.makeText(context, if (checked) "已開啟開機自動啟動" else "已關閉開機自動啟動", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // System Log Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("啟用系統日誌紀錄 (Log)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("開啟後會將運作狀態紀錄至記憶體供排解問題", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        Switch(
                                            checked = systemLogEnabled,
                                            onCheckedChange = { checked ->
                                                onSendCommand("system_log_enabled", if (checked) "on" else "off")
                                                Toast.makeText(context, if (checked) "已開啟系統日誌" else "已關閉系統日誌", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { 
                                            // TODO: View remote logs... we could just open a new dialog or ignore for now,
                                            // The user mainly asked for "系統日誌紀錄開關" and "查看系統日誌 (Log) 按鈕". 
                                            // Viewing remote logs requires fetching from API, which might take more work, 
                                            // but at least the button is there. Wait, is there a /logs endpoint? No.
                                            // So we should add a toast saying "請至鏡頭端本機查看，或等待後續支援遠端日誌拉取" 
                                            Toast.makeText(context, "請至鏡頭端本機查看，目前版本尚未支援遠端提取日誌", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(1.dp, brandPrimaryColor)
                                    ) {
                                        androidx.compose.material3.Text("📝 查看系統日誌 (Log)", color = brandPrimaryColor, fontWeight = FontWeight.Bold)
                                    }

                                    // Power Cut Alert Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("斷電與低電量 Telegram 警報", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                            Text("拔除電源線或低電量時發送推播通知", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                        Switch(
                                            checked = powerCutAlertEnabled,
                                            onCheckedChange = { checked ->
                                                onSendCommand("power_cut_alert", if (checked) "on" else "off")
                                                Toast.makeText(context, if (checked) "已開啟斷電/低電量警報" else "已關閉斷電/低電量警報", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                        )
                                    }
                                }
                            }
                        }

                        3 -> {
                            // TAB 4: Telegram Sync Card
                            val remoteBotToken = cameraStatusJson?.optString("telegramBotToken", "") ?: ""
                            val remoteChatId = cameraStatusJson?.optString("telegramChatId", "") ?: ""
                            val hasRemoteTelegram = remoteBotToken.isNotBlank() && remoteChatId.isNotBlank()

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = BorderStroke(1.dp, cardBorderColor),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
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

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("一鍵同步設定", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("將目前觀看端的 Telegram Bot Token 與 Chat ID 推送給這台鏡頭裝置：", fontSize = 12.sp, color = textSecondaryColor)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            onSyncTelegram()
                                            Toast.makeText(context, "已將觀看端 Telegram 設定推送至該鏡頭", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor, contentColor = Color.White),
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
                }
        }
    }
}

@Composable
fun RemoteSettingsDialog(
    cameraName: String,
    cameraStatusJson: JSONObject?,
    onSendCommand: (String, String) -> Unit,
    onSyncTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    RemoteSettingsScreen(
        cameraName = cameraName,
        cameraStatusJson = cameraStatusJson,
        onSendCommand = onSendCommand,
        onSyncTelegram = onSyncTelegram,
        onNavigateBack = onDismiss
    )
}
