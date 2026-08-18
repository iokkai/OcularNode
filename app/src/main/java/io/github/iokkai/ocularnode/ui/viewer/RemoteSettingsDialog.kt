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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.ui.camera.ResolutionSelectionDialog
import io.github.iokkai.ocularnode.ui.theme.*
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

    val surfaceBgColor = AppBackground
    val textPrimaryColor = AppTextPrimary
    val textSecondaryColor = AppTextSecondary
    val brandPrimaryColor = AppPrimary
    val cardBgColor = AppSurface
    val cardBorderColor = AppBorder

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
    val motionCooldown = cameraStatusJson?.optInt("motionCooldownSeconds", cameraStatusJson?.optInt("motionCooldown", 30) ?: 30) ?: 30
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
    val remoteAppVersion = cameraStatusJson?.optString("appVersion", "")?.ifBlank { null }

    // Local DraftState (remembered once per settings screen session, not reset by background heartbeat polling)
    var editingName by remember { mutableStateOf(deviceNameStr) }
    var localResolution by remember { mutableStateOf(currentRes) }
    var localQuality by remember { mutableFloatStateOf(currentQuality.toFloat()) }
    var localSens by remember { mutableFloatStateOf(motionSensitivity) }
    var localCooldown by remember { mutableFloatStateOf(motionCooldown.toFloat()) }
    var localNightLuma by remember { mutableFloatStateOf(nightLuma) }
    var localNightHysteresis by remember { mutableFloatStateOf(nightHysteresis) }
    var localStorageGB by remember { mutableFloatStateOf(storageLimitGB) }
    var localMaxEvents by remember { mutableFloatStateOf(maxEventCount.toFloat()) }

    var localIsMotion by remember { mutableStateOf(isMotion) }
    var localNightMode by remember { mutableStateOf(nightMode) }
    var localOpMode by remember { mutableStateOf(currentOpMode) }
    var localTorchOn by remember { mutableStateOf(isTorchOn) }
    var localLensFacing by remember { mutableStateOf(lensFacing) }
    var localPlayLocalAlarm by remember { mutableStateOf(playLocalAlarm) }
    var localMlKitEnabled by remember { mutableStateOf(mlKitEnabled) }
    var localAutoCleanup by remember { mutableStateOf(autoCleanup) }
    var localAutoStartOnBoot by remember { mutableStateOf(autoStartOnBoot) }
    var localPowerCutAlert by remember { mutableStateOf(powerCutAlertEnabled) }
    var localSystemLogEnabled by remember { mutableStateOf(systemLogEnabled) }
    var localTelegramMediaType by remember { mutableStateOf(remoteSendMediaType) }

    var localMotionSchedEnable by remember { mutableStateOf(motionSchedEnable) }
    var localMotionSchedStart by remember { mutableStateOf(motionSchedStart) }
    var localMotionSchedEnd by remember { mutableStateOf(motionSchedEnd) }
    var localNotifSchedEnable by remember { mutableStateOf(notifSchedEnable) }
    var localNotifSchedStart by remember { mutableStateOf(notifSchedStart) }
    var localNotifSchedEnd by remember { mutableStateOf(notifSchedEnd) }

    val categoryStates = remember {
        val catJson = cameraStatusJson?.optJSONObject("categoryFilters")
        mutableStateMapOf<NotificationCategory, Boolean>().apply {
            NotificationCategory.entries.forEach { cat ->
                put(cat, catJson?.optBoolean(cat.name, true) ?: true)
            }
        }
    }
    val categoryRecordStates = remember {
        val catRecordJson = cameraStatusJson?.optJSONObject("categoryRecordingFilters")
        mutableStateMapOf<NotificationCategory, Boolean>().apply {
            NotificationCategory.entries.forEach { cat ->
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
                            text = stringResource(R.string.remote_settings_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = stringResource(R.string.remote_settings_controlling, editingName.ifBlank { cameraName }),
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
                            contentDescription = stringResource(R.string.remote_settings_btn_back),
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
                        Text(stringResource(R.string.remote_settings_btn_cancel), color = textPrimaryColor, fontWeight = FontWeight.Medium)
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
                                    Toast.makeText(context, context.getString(R.string.remote_settings_toast_success), Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.remote_settings_toast_fail), Toast.LENGTH_SHORT).show()
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
                            Text(stringResource(R.string.remote_settings_btn_saving), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text(stringResource(R.string.remote_settings_btn_save), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            val tabTitles = listOf(
                stringResource(R.string.remote_tab_quality),
                stringResource(R.string.remote_tab_motion),
                stringResource(R.string.remote_tab_storage),
                stringResource(R.string.remote_tab_telegram)
            )
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
                                Text(stringResource(R.string.remote_dev_name), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
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

                                Text(stringResource(R.string.remote_sec_mode), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
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
                                            containerColor = if (isMon) brandPrimaryColor else AppSecondaryContainer,
                                            contentColor = if (isMon) Color.White else AppOnSecondaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.remote_mode_live), fontSize = 12.sp, fontWeight = if (isMon) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    Button(
                                        onClick = {
                                            localOpMode = "detection"
                                            localIsMotion = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isMon) brandPrimaryColor else AppSecondaryContainer,
                                            contentColor = if (!isMon) Color.White else AppOnSecondaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.remote_mode_guard), fontSize = 12.sp, fontWeight = if (!isMon) FontWeight.Bold else FontWeight.Normal)
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
                                        val facingText = if (localLensFacing == "back") stringResource(R.string.remote_lens_front) else stringResource(R.string.remote_lens_back)
                                        Text(stringResource(R.string.remote_btn_switch_cam, facingText), color = brandPrimaryColor, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            localTorchOn = !localTorchOn
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (localTorchOn) AppWarning else AppSecondaryContainer,
                                            contentColor = if (localTorchOn) Color.White else AppOnSecondaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (localTorchOn) stringResource(R.string.remote_btn_torch_off) else stringResource(R.string.remote_btn_torch_on), fontSize = 11.sp)
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
                                        Text(stringResource(R.string.remote_resolution), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                        Text(stringResource(R.string.remote_resolution_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                        Text(stringResource(R.string.remote_jpeg_quality), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
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
                                Text(stringResource(R.string.remote_night_vision), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val modes = listOf(
                                        "off" to stringResource(R.string.remote_night_off),
                                        "on" to stringResource(R.string.remote_night_on),
                                        "auto" to stringResource(R.string.remote_night_auto)
                                    )
                                    modes.forEach { (modeKey, label) ->
                                        val isSelected = localNightMode.equals(modeKey, ignoreCase = true)
                                        Button(
                                            onClick = {
                                                localNightMode = modeKey
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) brandPrimaryColor else AppSecondaryContainer,
                                                contentColor = if (isSelected) Color.White else AppOnSecondaryContainer
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
                                            Text(stringResource(R.string.remote_night_threshold), fontSize = 12.sp, color = textSecondaryColor)
                                            Text("${localNightLuma.toInt()} Luma", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Slider(
                                            value = localNightLuma,
                                            onValueChange = { localNightLuma = it },
                                            valueRange = 10f..100f,
                                            colors = SliderDefaults.colors(thumbColor = brandPrimaryColor, activeTrackColor = brandPrimaryColor)
                                        )

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(stringResource(R.string.remote_night_hysteresis), fontSize = 12.sp, color = textSecondaryColor)
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
                                            color = AppSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(stringResource(R.string.remote_night_anti_flicker), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTextSecondary)
                                                Text(stringResource(R.string.remote_night_enter, lowCut), fontSize = 11.sp, color = AppPrimary)
                                                Text(stringResource(R.string.remote_night_exit, highCut), fontSize = 11.sp, color = AppPrimary)
                                                Text(stringResource(R.string.remote_night_hold, lowCut, highCut), fontSize = 10.sp, color = AppTextMuted)
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
                                Text(stringResource(R.string.remote_motion_mode_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stringResource(R.string.remote_motion_mode_desc), fontSize = 11.sp, color = textSecondaryColor)

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
                                            containerColor = if (!isDetectionMode) brandPrimaryColor else AppSecondaryContainer,
                                            contentColor = if (!isDetectionMode) Color.White else AppOnSecondaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.remote_mode_live), fontSize = 12.sp, fontWeight = if (!isDetectionMode) FontWeight.Bold else FontWeight.Medium)
                                    }

                                    // 🚨 動態偵測防護模式
                                    Button(
                                        onClick = {
                                            localOpMode = "detection"
                                            localIsMotion = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDetectionMode) brandPrimaryColor else AppSecondaryContainer,
                                            contentColor = if (isDetectionMode) Color.White else AppOnSecondaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.remote_mode_guard), fontSize = 12.sp, fontWeight = if (isDetectionMode) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 模式說明區塊
                                Surface(
                                    color = if (isDetectionMode) AppSurfaceVariant else AppSurfaceCardAlt,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isDetectionMode) 
                                                stringResource(R.string.remote_motion_guard_desc)
                                            else 
                                                stringResource(R.string.remote_motion_live_desc),
                                            fontSize = 11.sp,
                                            color = if (isDetectionMode) AppPrimary else AppTextSecondary,
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
                                        Text(stringResource(R.string.remote_motion_params_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // ➔ 敏感度門檻 (Sensitivity)
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(stringResource(R.string.remote_motion_sens_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                                Text(String.format("%.1f%%", localSens), color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Text(stringResource(R.string.remote_motion_sens_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                                Text(stringResource(R.string.remote_motion_cooldown_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                                Text("${localCooldown.toInt()} s", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Text(stringResource(R.string.remote_motion_cooldown_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                                Text(stringResource(R.string.remote_motion_alarm_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                                Text(stringResource(R.string.remote_motion_alarm_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                                        Toast.makeText(context, context.getString(R.string.remote_motion_toast_alarm_sent), Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.remote_motion_toast_alarm_fail), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppError),
                                            border = BorderStroke(1.dp, AppError),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.remote_motion_btn_test_alarm), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                                Text(stringResource(R.string.remote_motion_sched_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                                Text(stringResource(R.string.remote_motion_sched_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                                        Text(stringResource(R.string.remote_motion_sched_start, localMotionSchedStart), fontSize = 12.sp)
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
                                                        Text(stringResource(R.string.remote_motion_sched_end, localMotionSchedEnd), fontSize = 12.sp)
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
                                                text = stringResource(R.string.remote_ai_filter_title),
                                                color = textPrimaryColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.remote_ai_filter_desc),
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
                                                    text = stringResource(R.string.remote_ai_mlkit_title),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = textPrimaryColor
                                                )
                                                Text(
                                                    text = stringResource(R.string.remote_ai_mlkit_desc),
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
                                                .background(AppBorderLight)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Spacer(modifier = Modifier.weight(1.2f))
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.remote_ai_allow_push), fontSize = 13.sp, color = AppTextMuted, fontWeight = FontWeight.Bold)
                                            }
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                Text(stringResource(R.string.remote_ai_trigger_rec), fontSize = 13.sp, color = AppTextMuted, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        NotificationCategory.entries.forEach { category ->
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
                                                    text = "$iconStr ${stringResource(category.titleRes)}",
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

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(AppBorderLight)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // ➔ 告警媒體類型 (照片 / 影片 / 照片+影片)
                                        Text(stringResource(R.string.remote_tg_media_type), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(stringResource(R.string.remote_tg_media_type_desc), fontSize = 11.sp, color = textSecondaryColor)
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val types = listOf(
                                                "photo" to stringResource(R.string.settings_telegram_media_photo),
                                                "video" to stringResource(R.string.settings_telegram_media_video),
                                                "both" to stringResource(R.string.settings_telegram_media_both)
                                            )
                                            types.forEach { (typeKey, label) ->
                                                val isSelected = localTelegramMediaType == typeKey
                                                OutlinedButton(
                                                    onClick = { localTelegramMediaType = typeKey },
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, if (isSelected) brandPrimaryColor else AppBorder),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = if (isSelected) AppSecondaryContainer else Color.Transparent,
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
                            }
                        }

                        AnimatedVisibility(
                            visible = !isDetectionMode,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                color = AppSurfaceCardAlt,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, AppBorderLight),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(stringResource(R.string.remote_live_mode_banner_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppTextPrimary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.remote_live_mode_banner_desc),
                                        fontSize = 12.sp,
                                        color = AppTextSecondary,
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
                                        Text(stringResource(R.string.remote_storage_loop_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                        Text(stringResource(R.string.remote_storage_loop_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                        Text(stringResource(R.string.remote_storage_max_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
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
                                        Text(stringResource(R.string.remote_storage_events_limit), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                        Text("${localMaxEvents.toInt()}", color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                        Text(stringResource(R.string.remote_kiosk_autostart_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                        Text(stringResource(R.string.remote_kiosk_autostart_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                        Text(stringResource(R.string.remote_syslog_switch_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                        Text(stringResource(R.string.remote_syslog_switch_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                            Toast.makeText(context, context.getString(R.string.remote_syslog_toast_unsupported), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, brandPrimaryColor)
                                ) {
                                    Text(stringResource(R.string.remote_syslog_btn_view), color = brandPrimaryColor, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Power Cut Alert Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.remote_powercut_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                        Text(stringResource(R.string.remote_powercut_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                    colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                                    border = BorderStroke(1.dp, AppSecondaryContainer),
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
                                                text = stringResource(R.string.remote_ota_title),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = textPrimaryColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.remote_ota_desc),
                                            fontSize = 11.sp,
                                            color = textSecondaryColor,
                                            lineHeight = 16.sp
                                        )
                                        if (!remoteAppVersion.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = brandPrimaryColor.copy(alpha = 0.12f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = brandPrimaryColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = stringResource(R.string.remote_ota_current_version, remoteAppVersion),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = brandPrimaryColor
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                if (isTriggeringUpdate) return@Button
                                                isTriggeringUpdate = true
                                                coroutineScope.launch {
                                                    val success = onSendCommand("trigger_ota_update", "true")
                                                    isTriggeringUpdate = false
                                                    if (success) {
                                                        Toast.makeText(context, context.getString(R.string.remote_ota_toast_sent), Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.remote_ota_toast_fail), Toast.LENGTH_SHORT).show()
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
                                                Text(stringResource(R.string.remote_ota_sending), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.remote_ota_btn_check), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // TAB 4: Telegram Sync Card
                        val settingsManager = remember { io.github.iokkai.ocularnode.data.SettingsManager.getInstance(context) }
                        var isLocalTgSynced by remember { mutableStateOf(false) }
                        val remoteBotToken = cameraStatusJson?.optString("telegramBotToken", "") ?: ""
                        val remoteChatId = cameraStatusJson?.optString("telegramChatId", "") ?: ""
                        val isConfiguredInJson = cameraStatusJson?.optBoolean("telegramConfigured", false) == true
                        val hasRemoteTelegram = isLocalTgSynced || isConfiguredInJson || (remoteBotToken.isNotBlank() && remoteChatId.isNotBlank())
                        val displayChatId = if (isLocalTgSynced && settingsManager.telegramChatId.isNotBlank()) {
                            settingsManager.telegramChatId
                        } else {
                            remoteChatId.ifBlank { if (hasRemoteTelegram) settingsManager.telegramChatId else "" }
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, cardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (hasRemoteTelegram) AppSuccessContainer else AppErrorContainerLight),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (hasRemoteTelegram) stringResource(R.string.remote_tg_status_bound) else stringResource(R.string.remote_tg_status_unbound),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (hasRemoteTelegram) AppSuccess else AppError
                                        )
                                        if (hasRemoteTelegram && displayChatId.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Chat ID: $displayChatId", fontSize = 11.sp, color = AppSuccessDark)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(stringResource(R.string.remote_tg_sync_header), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stringResource(R.string.remote_tg_sync_desc), fontSize = 12.sp, color = textSecondaryColor)
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (settingsManager.telegramBotToken.isBlank() && settingsManager.telegramChatId.isBlank()) {
                                            Toast.makeText(context, "請先於觀看端設定 Telegram Bot Token 與 Chat ID", Toast.LENGTH_LONG).show()
                                        } else {
                                            onSyncTelegram()
                                            isLocalTgSynced = true
                                            Toast.makeText(context, context.getString(R.string.remote_settings_toast_success), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor, contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.remote_tg_btn_sync), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(stringResource(R.string.remote_tg_media_type), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stringResource(R.string.remote_tg_media_type_desc), fontSize = 12.sp, color = textSecondaryColor)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val types = listOf(
                                        "photo" to stringResource(R.string.settings_telegram_media_photo),
                                        "video" to stringResource(R.string.settings_telegram_media_video),
                                        "both" to stringResource(R.string.settings_telegram_media_both)
                                    )
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
                                            border = BorderStroke(1.dp, if (isSelected) brandPrimaryColor else AppBorder),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) AppSecondaryContainer else Color.Transparent,
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
                                        Text(stringResource(R.string.remote_notif_sched_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimaryColor)
                                        Text(stringResource(R.string.remote_notif_sched_desc), fontSize = 11.sp, color = textSecondaryColor)
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
                                                Text(stringResource(R.string.remote_motion_sched_start, localNotifSchedStart), fontSize = 12.sp)
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
                                                Text(stringResource(R.string.remote_motion_sched_end, localNotifSchedEnd), fontSize = 12.sp)
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
