package io.github.iokkai.ocularnode.ui.viewer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.ui.camera.ResolutionSelectionDialog
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSettingsScreen(
    cameraName: String,
    cameraStatusJson: JSONObject?,
    onSendCommand: suspend (String, String) -> Boolean,
    onSaveBatchConfig: (suspend (String) -> Boolean)? = null,
    onSyncTelegram: () -> Unit,
    onNavigateBack: () -> Unit,
    onFetchLogs: (suspend () -> List<String>)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showResPicker by remember { mutableStateOf(false) }
    var showRemoteLogs by remember { mutableStateOf(false) }
    var remoteLogsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isTriggeringUpdate by remember { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

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
    val nightHysteresis = cameraStatusJson?.optDouble("nightVisionHysteresis", 8.0)?.toFloat() ?: 8.0f
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
    val systemLogEnabled = cameraStatusJson?.optBoolean("systemLogEnabled", true) ?: true
    val remoteSendMediaType = cameraStatusJson?.optString("telegramSendMediaType", "photo") ?: "photo"

    val motionSchedEnable = cameraStatusJson?.optBoolean("motionScheduleEnabled", false) ?: (cameraStatusJson?.optJSONObject("motionDetection")?.optBoolean("scheduleEnabled", false) ?: false)
    val motionSchedStart = cameraStatusJson?.optString("motionScheduleStart", "22:00")?.ifBlank { "22:00" } ?: "22:00"
    val motionSchedEnd = cameraStatusJson?.optString("motionScheduleEnd", "06:00")?.ifBlank { "06:00" } ?: "06:00"
    val notifSchedEnable = cameraStatusJson?.optBoolean("notificationScheduleEnabled", false) ?: (cameraStatusJson?.optJSONObject("notifications")?.optBoolean("scheduleEnabled", false) ?: false)
    val notifSchedStart = cameraStatusJson?.optString("notificationScheduleStart", "22:00")?.ifBlank { "22:00" } ?: "22:00"
    val notifSchedEnd = cameraStatusJson?.optString("notificationScheduleEnd", "06:00")?.ifBlank { "06:00" } ?: "06:00"

    // Local DraftState
    var editingName by remember(deviceNameStr) { mutableStateOf(deviceNameStr) }
    var localResolution by remember(currentRes) { mutableStateOf(currentRes) }
    var localQuality by remember(currentQuality) { mutableFloatStateOf(currentQuality.toFloat()) }
    var localSens by remember(motionSensitivity) { mutableFloatStateOf(motionSensitivity) }
    var localCooldown by remember(motionCooldown) { mutableFloatStateOf(motionCooldown.toFloat()) }
    var localNightLuma by remember(nightLuma) { mutableFloatStateOf(nightLuma) }
    var localNightHysteresis by remember(nightHysteresis) { mutableFloatStateOf(nightHysteresis) }
    var localStorageGB by remember(storageLimitGB) { mutableFloatStateOf(storageLimitGB) }
    var localMaxEvents by remember(maxEventCount) { mutableFloatStateOf(maxEventCount.toFloat()) }

    var localIsMotion by remember(isMotion) { mutableStateOf(isMotion) }
    var localNightMode by remember(nightMode) { mutableStateOf(nightMode) }
    var localOpMode by remember(currentOpMode) { mutableStateOf(currentOpMode) }
    var localTorchOn by remember(isTorchOn) { mutableStateOf(isTorchOn) }
    var localLensFacing by remember(lensFacing) { mutableStateOf(lensFacing) }
    var localPlayLocalAlarm by remember(playLocalAlarm) { mutableStateOf(playLocalAlarm) }
    var localMlKitEnabled by remember(mlKitEnabled) { mutableStateOf(mlKitEnabled) }
    var localAutoCleanup by remember(autoCleanup) { mutableStateOf(autoCleanup) }
    var localAutoStartOnBoot by remember(autoStartOnBoot) { mutableStateOf(autoStartOnBoot) }
    var localPowerCutAlert by remember(powerCutAlertEnabled) { mutableStateOf(powerCutAlertEnabled) }
    var localSystemLogEnabled by remember(systemLogEnabled) { mutableStateOf(systemLogEnabled) }
    var localTelegramMediaType by remember(remoteSendMediaType) { mutableStateOf(remoteSendMediaType) }

    var localMotionSchedEnable by remember(motionSchedEnable) { mutableStateOf(motionSchedEnable) }
    var localMotionSchedStart by remember(motionSchedStart) { mutableStateOf(motionSchedStart) }
    var localMotionSchedEnd by remember(motionSchedEnd) { mutableStateOf(motionSchedEnd) }
    var localNotifSchedEnable by remember(notifSchedEnable) { mutableStateOf(notifSchedEnable) }
    var localNotifSchedStart by remember(notifSchedStart) { mutableStateOf(notifSchedStart) }
    var localNotifSchedEnd by remember(notifSchedEnd) { mutableStateOf(notifSchedEnd) }

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
            currentResolution = localResolution,
            onSelect = { res ->
                localResolution = res
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
                            text = "正在控制: ${editingName.ifBlank { cameraName }}",
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
        },
        bottomBar = {
            Surface(
                color = surfaceBgColor,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", color = textPrimaryColor, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            coroutineScope.launch {
                                val draftConfigJson = JSONObject().apply {
                                    put("device", JSONObject().apply {
                                        put("deviceName", editingName.ifBlank { cameraName })
                                        put("operatingMode", localOpMode)
                                    })
                                    put("camera", JSONObject().apply {
                                        put("resolution", localResolution)
                                        put("quality", localQuality.toInt())
                                        put("nightVisionMode", localNightMode)
                                        put("nightVisionLuma", localNightLuma.toDouble())
                                        put("nightVisionHysteresis", localNightHysteresis.toDouble())
                                        put("isTorchOn", localTorchOn)
                                        put("lensFacing", localLensFacing)
                                    })
                                    put("motionDetection", JSONObject().apply {
                                        put("enabled", localIsMotion)
                                        put("sensitivity", localSens.toDouble())
                                        put("cooldownSeconds", localCooldown.toInt())
                                        put("playLocalAlarm", localPlayLocalAlarm)
                                        put("mlKitEnabled", localMlKitEnabled)
                                        put("scheduleEnabled", localMotionSchedEnable)
                                        put("scheduleStart", localMotionSchedStart)
                                        put("scheduleEnd", localMotionSchedEnd)
                                        put("categories", JSONObject().apply {
                                            categoryStates.forEach { (cat, enabled) ->
                                                put(cat.name, enabled)
                                            }
                                        })
                                    })
                                    put("recording", JSONObject().apply {
                                        put("eventRecordingEnabled", localIsMotion)
                                        put("maxStorageGb", localStorageGB.toDouble())
                                        put("maxEventCount", localMaxEvents.toInt())
                                        put("autoCleanup", localAutoCleanup)
                                        put("categoryRecording", JSONObject().apply {
                                            categoryRecordStates.forEach { (cat, enabled) ->
                                                put(cat.name, enabled)
                                            }
                                        })
                                    })
                                    put("notifications", JSONObject().apply {
                                        put("autoStartOnBoot", localAutoStartOnBoot)
                                        put("powerCutAlertEnabled", localPowerCutAlert)
                                        put("systemLogEnabled", localSystemLogEnabled)
                                        put("scheduleEnabled", localNotifSchedEnable)
                                        put("scheduleStart", localNotifSchedStart)
                                        put("scheduleEnd", localNotifSchedEnd)
                                        put("telegram", JSONObject().apply {
                                            put("mediaType", localTelegramMediaType)
                                        })
                                    })
                                }.toString()

                                val success = if (onSaveBatchConfig != null) {
                                    onSaveBatchConfig(draftConfigJson)
                                } else {
                                    onSendCommand("batch_config", draftConfigJson)
                                }

                                isSaving = false
                                if (success) {
                                    Toast.makeText(context, "已成功將所有變更同步至鏡頭端", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(context, "同步失敗，請檢查網路連線", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving,
                        modifier = Modifier.weight(1.5f)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("儲存中...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text("💾 儲存並套用變更", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
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
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("安防模式", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val isMon = (localOpMode == "monitor") && !localIsMotion
                                    Button(
                                        onClick = {
                                            localOpMode = "monitor"
                                            localIsMotion = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isMon) brandPrimaryColor else Color(0xFFE8DEF8),
                                            contentColor = if (isMon) Color.White else Color(0xFF1D192B)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("👁️ 即時監看模式", fontSize = 12.sp, fontWeight = if (isMon) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    Button(
                                        onClick = {
                                            localOpMode = "detection"
                                            localIsMotion = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isMon) brandPrimaryColor else Color(0xFFE8DEF8),
                                            contentColor = if (!isMon) Color.White else Color(0xFF1D192B)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🚨 動態偵測防護", fontSize = 12.sp, fontWeight = if (!isMon) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Switch Camera / Torch Buttons
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            localLensFacing = if (localLensFacing == "back") "front" else "back"
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, brandPrimaryColor),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, tint = brandPrimaryColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("切換鏡頭 (${if (localLensFacing == "back") "前" else "後"})", color = brandPrimaryColor, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            localTorchOn = !localTorchOn
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (localTorchOn) Color(0xFFE2A03F) else Color(0xFFE8DEF8),
                                            contentColor = if (localTorchOn) Color.White else Color(0xFF1D192B)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (localTorchOn) "關閉補光燈" else "開啟補光燈", fontSize = 11.sp)
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
                                        Text("📹 $localResolution ▾", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                        val isSelected = localNightMode.equals(modeKey, ignoreCase = true)
                                        Button(
                                            onClick = {
                                                localNightMode = modeKey
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

                                if (localNightMode == "auto") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("自動夜視切換基準閥值", fontSize = 12.sp, color = textSecondaryColor)
                                            Text("${localNightLuma.toInt()} Luma", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Slider(
                                            value = localNightLuma,
                                            onValueChange = { localNightLuma = it },
                                            valueRange = 10f..100f,
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("切換磁滯區間 (Hysteresis)", fontSize = 12.sp, color = textSecondaryColor)
                                            Text("±${localNightHysteresis.toInt()} Luma", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Slider(
                                            value = localNightHysteresis,
                                            onValueChange = { localNightHysteresis = it },
                                            valueRange = 2f..20f,
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )

                                        val lowCut = (localNightLuma - localNightHysteresis).coerceAtLeast(0f).toInt()
                                        val highCut = (localNightLuma + localNightHysteresis).coerceAtMost(255f).toInt()
                                        Surface(
                                            color = Color(0xFFF3EDF7),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("防頻繁閃爍狀態區間：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF49454F))
                                                Text("• 進入夜視：環境亮度 < $lowCut Luma", fontSize = 11.sp, color = Color(0xFF6750A4))
                                                Text("• 離開夜視：環境亮度 > $highCut Luma", fontSize = 11.sp, color = Color(0xFF6750A4))
                                                Text("• 介於 $lowCut ~ $highCut Luma 之間時，保持當前模式不切換", fontSize = 10.sp, color = Color(0xFF79747E))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 2: 防護模式與動態偵測設定
                        val isDetectionMode = localIsMotion || localOpMode == "detection"

                        // 1. 安防運作模式 (主控卡片)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, cardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("安防運作模式", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("選擇鏡頭端的運作模式與防護機制", fontSize = 11.sp, color = textSecondaryColor)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // 👁️ 即時監看模式
                                    Button(
                                        onClick = {
                                            localOpMode = "monitor"
                                            localIsMotion = false
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isDetectionMode) brandPrimaryColor else Color(0xFFE8DEF8),
                                            contentColor = if (!isDetectionMode) Color.White else Color(0xFF1D192B)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("👁️ 即時監看模式", fontSize = 12.sp, fontWeight = if (!isDetectionMode) FontWeight.Bold else FontWeight.Medium)
                                    }

                                    // 🚨 動態偵測防護模式
                                    Button(
                                        onClick = {
                                            localOpMode = "detection"
                                            localIsMotion = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDetectionMode) brandPrimaryColor else Color(0xFFE8DEF8),
                                            contentColor = if (isDetectionMode) Color.White else Color(0xFF1D192B)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🚨 動態偵測防護", fontSize = 12.sp, fontWeight = if (isDetectionMode) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 模式說明區塊
                                Surface(
                                    color = if (isDetectionMode) Color(0xFFF3EDF7) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isDetectionMode) 
                                                "🚨 動態偵測防護模式：鏡頭持續分析畫面移動，一旦觸發即記錄事件、發送告警推播與備份影像。" 
                                            else 
                                                "👁️ 即時監看模式：僅提供即時串流影像，不執行背景影像比對與推播，適合人在家時使用。",
                                            fontSize = 11.sp,
                                            color = if (isDetectionMode) Color(0xFF6750A4) else Color(0xFF475569),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 層級化設定 (開啟防護模式時才展開)
                        AnimatedVisibility(
                            visible = isDetectionMode,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                // 告警參數卡片
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                    border = BorderStroke(1.dp, cardBorderColor),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("⚙️ 防護細節與告警參數", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // ➔ 敏感度門檻 (Sensitivity)
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("動態差異觸發門檻 (Sensitivity)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                                Text(String.format("%.1f%%", localSens), color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Text("低於此百分比的畫面變動將被忽略 (1% = 極度敏感, 100% = 需要全畫面變動)", fontSize = 11.sp, color = textSecondaryColor)
                                            Slider(
                                                value = localSens,
                                                onValueChange = { localSens = it },
                                                valueRange = 1.0f..100.0f,
                                                steps = 98,
                                                colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // ➔ 事件冷卻時間 (Cooldown)
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("告警冷卻時間 (Cooldown)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                                Text("${localCooldown.toInt()} 秒", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Text("觸發告警後暫停再次告警的時間間隔", fontSize = 11.sp, color = textSecondaryColor)
                                            Slider(
                                                value = localCooldown,
                                                onValueChange = { localCooldown = it },
                                                valueRange = 5f..120f,
                                                colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // 異動時鏡頭端發出響聲
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("異動時鏡頭端發出警報聲", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                                Text("偵測異動時鏡頭手機發出警報鳴聲", fontSize = 11.sp, color = textSecondaryColor)
                                            }
                                            Switch(
                                                checked = localPlayLocalAlarm,
                                                onCheckedChange = { localPlayLocalAlarm = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val success = onSendCommand("alarm", "trigger")
                                                    if (success) {
                                                        Toast.makeText(context, "🚨 已發送測試警報指令至鏡頭端", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "設定失敗", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E)),
                                            border = BorderStroke(1.dp, Color(0xFFB3261E)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("🚨 測試鏡頭端發聲響", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // ➔ 📅 監控排程設定 (自動開啟/停用動態偵測)
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
                                                Text("📅 監控排程設定", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                                Text("開啟後僅在排程時段內自動開啟動態偵測防護", fontSize = 11.sp, color = textSecondaryColor)
                                            }
                                            Switch(
                                                checked = localMotionSchedEnable,
                                                onCheckedChange = { localMotionSchedEnable = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                            )
                                        }

                                        AnimatedVisibility(visible = localMotionSchedEnable) {
                                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val parts = localMotionSchedStart.split(":")
                                                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                                                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                                            android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                                                localMotionSchedStart = String.format("%02d:%02d", hourOfDay, minute)
                                                            }, h, m, true).show()
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("開始時間: $localMotionSchedStart", fontSize = 12.sp)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            val parts = localMotionSchedEnd.split(":")
                                                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
                                                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                                            android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                                                localMotionSchedEnd = String.format("%02d:%02d", hourOfDay, minute)
                                                            }, h, m, true).show()
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("結束時間: $localMotionSchedEnd", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // ➔ 推播過濾與智慧分類設定 (人/車/寵物)
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
                                            text = "設定 AI 物件辨識與各類別 (人類、寵物、車輛等) 之推播與錄影過濾規則：",
                                            fontSize = 11.sp,
                                            color = textSecondaryColor
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // AI 智慧過濾總開關 (Google ML Kit)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Google ML Kit AI 智慧物件過濾",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = textPrimaryColor
                                                )
                                                Text(
                                                    text = "自動辨識人體、寵物與車輛並智慧分類，大幅減少風吹草動無效誤報",
                                                    fontSize = 11.sp,
                                                    color = textSecondaryColor
                                                )
                                            }
                                            Switch(
                                                checked = localMlKitEnabled,
                                                onCheckedChange = { localMlKitEnabled = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color(0xFFE2E8F0))
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
                                                        onCheckedChange = { categoryStates[category] = it },
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
                                                        onCheckedChange = { categoryRecordStates[category] = it },
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
                        }

                        AnimatedVisibility(
                            visible = !isDetectionMode,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("👁️ 當前處於「即時監看模式」", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF334155))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "背景動態比對與推播告警已暫停。點擊上方「🚨 動態偵測防護」即可開啟安防監控並設定觸發門檻、冷卻時間與 AI 物件過濾參數。",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
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
                                        checked = localAutoCleanup,
                                        onCheckedChange = { localAutoCleanup = it },
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
                                        valueRange = 0.5f..10.0f,
                                        colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Max Events Limit
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("事件數量上限:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                        Text("${localMaxEvents.toInt()} 筆", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Slider(
                                        value = localMaxEvents,
                                        onValueChange = { localMaxEvents = it },
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
                                        checked = localAutoStartOnBoot,
                                        onCheckedChange = { localAutoStartOnBoot = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                    )
                                }

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
                                        checked = localSystemLogEnabled,
                                        onCheckedChange = { localSystemLogEnabled = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedButton(
                                    onClick = {
                                        if (onFetchLogs != null) {
                                            isLoadingLogs = true
                                            showRemoteLogs = true
                                            coroutineScope.launch {
                                                remoteLogsList = onFetchLogs()
                                                isLoadingLogs = false
                                            }
                                        } else {
                                            Toast.makeText(context, "暫不支援遠端提取日誌", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, brandPrimaryColor)
                                ) {
                                    Text("📝 查看系統日誌 (Log)", color = brandPrimaryColor, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

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
                                        checked = localPowerCutAlert,
                                        onCheckedChange = { localPowerCutAlert = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 🚀 遠端檢查並更新鏡頭端 (Silent OTA)
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                                    border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = brandPrimaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "鏡頭端韌體靜默更新 (OTA)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = textPrimaryColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "向 GitHub Releases 查詢最新版 APK。若有新版本，鏡頭端將自動在背景靜默下載安裝並重啟監控服務。",
                                            fontSize = 11.sp,
                                            color = textSecondaryColor,
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                if (isTriggeringUpdate) return@Button
                                                isTriggeringUpdate = true
                                                coroutineScope.launch {
                                                    val success = onSendCommand("update", "")
                                                    isTriggeringUpdate = false
                                                    if (success) {
                                                        Toast.makeText(context, "🚀 已向鏡頭端發送更新指令！若有新版本將自動進行升級並重啟", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "發送更新指令失敗，請檢查網路連線", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor, contentColor = Color.White),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = !isTriggeringUpdate,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isTriggeringUpdate) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("發送指令中...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("🚀 立即檢查並更新鏡頭端 (OTA)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
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
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("⚡ 同步觀看端 Telegram 設定至本鏡頭", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("告警媒體類型", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("選擇遠端鏡頭觸發告警時傳送至 Telegram 的媒體種類：", fontSize = 12.sp, color = textSecondaryColor)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val types = listOf("photo" to "📸 照片", "video" to "🎥 影片", "both" to "🖼️ 照片+影片")
                                    types.forEach { (typeKey, label) ->
                                        val isSelected = localTelegramMediaType == typeKey
                                        OutlinedButton(
                                            onClick = {
                                                localTelegramMediaType = typeKey
                                                coroutineScope.launch {
                                                    onSendCommand("telegram_media_type", typeKey)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, if (isSelected) brandPrimaryColor else cardBorderColor),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) Color(0xFFE8DEF8) else Color.Transparent,
                                                contentColor = if (isSelected) brandPrimaryColor else textSecondaryColor
                                            ),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                                        ) {
                                            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }

                        // ➔ ⏰ 通知排程設定 (推播時段控制)
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
                                        Text("⏰ 通知排程設定", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                        Text("開啟後僅在排程時段內自動發送告警與推播通知", fontSize = 11.sp, color = textSecondaryColor)
                                    }
                                    Switch(
                                        checked = localNotifSchedEnable,
                                        onCheckedChange = { localNotifSchedEnable = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = brandPrimaryColor)
                                    )
                                }

                                AnimatedVisibility(visible = localNotifSchedEnable) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    val parts = localNotifSchedStart.split(":")
                                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                                    android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                                        localNotifSchedStart = String.format("%02d:%02d", hourOfDay, minute)
                                                    }, h, m, true).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("開始時間: $localNotifSchedStart", fontSize = 12.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val parts = localNotifSchedEnd.split(":")
                                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
                                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                                    android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                                        localNotifSchedEnd = String.format("%02d:%02d", hourOfDay, minute)
                                                    }, h, m, true).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("結束時間: $localNotifSchedEnd", fontSize = 12.sp)
                                            }
                                        }
                                    }
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
    onSendCommand: suspend (String, String) -> Boolean,
    onSaveBatchConfig: (suspend (String) -> Boolean)? = null,
    onSyncTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    RemoteSettingsScreen(
        cameraName = cameraName,
        cameraStatusJson = cameraStatusJson,
        onSendCommand = onSendCommand,
        onSaveBatchConfig = onSaveBatchConfig,
        onSyncTelegram = onSyncTelegram,
        onNavigateBack = onDismiss
    )
}
