package io.github.iokkai.ocularnode.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import io.github.iokkai.ocularnode.BuildConfig
import io.github.iokkai.ocularnode.ui.about.AboutScreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.ui.theme.*

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    telegramSetupViewModel: TelegramSetupViewModel = viewModel()
) {
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showTelegramSetupDialog by remember { mutableStateOf(false) }
    var showSystemLogs by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }

    val telegramSetupUiState by telegramSetupViewModel.uiState.collectAsState()
    LaunchedEffect(telegramSetupUiState) {
        if (telegramSetupUiState !is TelegramSetupUiState.Step1_InputToken) {
            showTelegramSetupDialog = true
        }
    }

    if (showAboutScreen) {
        BackHandler { showAboutScreen = false }
        AboutScreen(onBack = { showAboutScreen = false })
        return
    }
    if (showSystemLogs) {
        BackHandler { showSystemLogs = false }
        SystemLogScreen(onBack = { showSystemLogs = false })
        return
    }
    if (showNotificationSettings) {
        BackHandler { showNotificationSettings = false }
        NotificationSettingsScreen(onNavigateBack = { showNotificationSettings = false })
        return
    }

    if (showTelegramSetupDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showTelegramSetupDialog = false
                viewModel.reloadSettings()
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                TelegramSetupScreen(
                    viewModel = telegramSetupViewModel,
                    onBack = {
                        showTelegramSetupDialog = false
                        viewModel.reloadSettings()
                    }
                )
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dpm = remember(context) { context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager }
    val isDeviceOwner = remember(context, dpm) { dpm?.isDeviceOwnerApp(context.packageName) == true }

    val roleMode by viewModel.roleMode.collectAsState()
    val botToken by viewModel.botToken.collectAsState()
    val chatId by viewModel.chatId.collectAsState()
    val telegramSendMediaType by viewModel.telegramSendMediaType.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val cooldown by viewModel.cooldown.collectAsState()
    val motionScheduleEnabled by viewModel.motionScheduleEnabled.collectAsState()
    val motionScheduleStartTime by viewModel.motionScheduleStartTime.collectAsState()
    val motionScheduleEndTime by viewModel.motionScheduleEndTime.collectAsState()
    val notificationScheduleEnabled by viewModel.notificationScheduleEnabled.collectAsState()
    val notificationScheduleStartTime by viewModel.notificationScheduleStartTime.collectAsState()
    val notificationScheduleEndTime by viewModel.notificationScheduleEndTime.collectAsState()
    val playAlarm by viewModel.playAlarm.collectAsState()
    val autoCleanupEnabled by viewModel.autoCleanupEnabled.collectAsState()
    val eventVideoRecordingEnabled by viewModel.eventVideoRecordingEnabled.collectAsState()
    val livePreviewInListEnabled by viewModel.livePreviewInListEnabled.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val powerCutAlertEnabled by viewModel.powerCutAlertEnabled.collectAsState()
    val mlKitFilterEnabled by viewModel.mlKitFilterEnabled.collectAsState()
    val systemLogEnabled by viewModel.systemLogEnabled.collectAsState()
    val dynamicFpsAdjustmentEnabled by viewModel.dynamicFpsAdjustmentEnabled.collectAsState()
    val httpAuthEnabled by viewModel.httpAuthEnabled.collectAsState()
    val httpPinCode by viewModel.httpPinCode.collectAsState()
    val scheduledRebootEnabled by viewModel.scheduledRebootEnabled.collectAsState()
    val scheduledRebootTime by viewModel.scheduledRebootTime.collectAsState()
    val storageLimitGB by viewModel.storageLimitGB.collectAsState()
    val maxEventCount by viewModel.maxEventCount.collectAsState()
    val cleanupStatus by viewModel.cleanupStatus.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val customTurnServerUrl by viewModel.customTurnServerUrl.collectAsState()
    val customTurnUsername by viewModel.customTurnUsername.collectAsState()
    val customTurnPassword by viewModel.customTurnPassword.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()

    var localStorageGB by remember(storageLimitGB) { mutableStateOf(storageLimitGB) }
    var localMaxEvents by remember(maxEventCount) { mutableStateOf(maxEventCount.toFloat()) }
    val brandPrimaryColor = AppPrimary
    val textPrimaryColor = AppTextPrimary

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.settings_title), color = AppTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        val isCamera = roleMode == "CAMERA" || roleMode == "UNSET"

        // Device Role Config Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, AppBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = AppPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_device_role), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.updateRoleMode("CAMERA") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCamera) AppPrimary else AppSecondaryContainer,
                            contentColor = if (isCamera) Color.White else AppOnSecondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.role_camera_title), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.updateRoleMode("VIEWER") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isCamera) AppPrimary else AppSecondaryContainer,
                            contentColor = if (!isCamera) Color.White else AppOnSecondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.role_viewer_title), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isCamera) {
            // ==================== 📷 鏡頭端專用設定 (5 大模組整合) ====================

            // 1. ⚙️ 基本與連線設定 (General & Network)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_general_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { viewModel.updateDeviceName(it) },
                        label = { Text(stringResource(R.string.settings_camera_name)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { viewModel.updateServerPort(it) },
                        label = { Text(stringResource(R.string.settings_camera_port)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = AppBorderLight)

                    // HTTP PIN Protection Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("網頁與 API 存取密碼保護", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (httpAuthEnabled) "已啟用：控制相機與設定需輸入 PIN 碼" else "已關閉：區域網路內無需驗證即可操作",
                                color = AppTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = httpAuthEnabled,
                            onCheckedChange = { viewModel.updateHttpAuthEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary
                            )
                        )
                    }

                    AnimatedVisibility(visible = httpAuthEnabled) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = httpPinCode,
                                onValueChange = { viewModel.updateHttpPinCode(it) },
                                label = { Text("自訂存取 PIN 碼 (4-6 位數字/英數)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppTextPrimary,
                                    unfocusedTextColor = AppTextPrimary,
                                    focusedBorderColor = AppPrimary,
                                    unfocusedBorderColor = AppBorder
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 2. ⚡ 電源與系統運作管理 (Power & System Management)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_power_system_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-Start on Boot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_auto_start_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (autoStartOnBoot) stringResource(R.string.settings_auto_start_desc_on) else stringResource(R.string.settings_auto_start_desc_off),
                                color = AppTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = autoStartOnBoot,
                            onCheckedChange = { viewModel.updateAutoStartOnBoot(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Power Cut Alert
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_power_alert_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (powerCutAlertEnabled) stringResource(R.string.settings_power_alert_desc_on) else stringResource(R.string.settings_power_alert_desc_off),
                                color = AppTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = powerCutAlertEnabled,
                            onCheckedChange = { viewModel.updatePowerCutAlertEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppError
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Scheduled Self-Healing Reboot (Problem 7)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.settings_scheduled_reboot_title),
                                    color = AppTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (!isDeviceOwner) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AppWarning.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "DO",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            color = AppWarning,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.settings_scheduled_reboot_desc),
                                color = AppTextSecondary,
                                fontSize = 11.sp
                            )
                            if (!isDeviceOwner && scheduledRebootEnabled) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.settings_scheduled_reboot_do_required),
                                    color = AppWarning,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = scheduledRebootEnabled,
                            onCheckedChange = { viewModel.updateScheduledRebootEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    AnimatedVisibility(visible = scheduledRebootEnabled) {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            OutlinedButton(
                                onClick = {
                                    val parts = scheduledRebootTime.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 4
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    android.app.TimePickerDialog(ctx, { _, hourOfDay, minute ->
                                        viewModel.updateScheduledRebootTime(String.format("%02d:%02d", hourOfDay, minute))
                                    }, h, m, true).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "${stringResource(R.string.settings_scheduled_reboot_time_label)}: $scheduledRebootTime",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // System Log
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_logs_enable_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.settings_logs_enable_desc), fontSize = 11.sp, color = AppTextSecondary)
                        }
                        Switch(
                            checked = systemLogEnabled,
                            onCheckedChange = { viewModel.updateSystemLogEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showSystemLogs = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AppPrimary)
                    ) {
                        Text(stringResource(R.string.settings_logs_btn_view), color = AppPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // 3. 🎯 偵測與排程警報 (Motion Detection & Schedules)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_motion_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // Motion Sensitivity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.settings_motion_sensitivity_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = textPrimaryColor
                            )
                            Text(
                                String.format("%.1f%%", sensitivity),
                                color = brandPrimaryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_motion_sensitivity_desc),
                            fontSize = 11.sp,
                            color = AppTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = sensitivity,
                            onValueChange = { viewModel.updateSensitivity(it) },
                            valueRange = 1f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = brandPrimaryColor,
                                activeTrackColor = brandPrimaryColor,
                                inactiveTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Motion Cooldown
                    OutlinedTextField(
                        value = cooldown,
                        onValueChange = { viewModel.updateCooldown(it) },
                        label = { Text(stringResource(R.string.settings_motion_cooldown)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Event Video Recording
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_event_recording_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.settings_event_recording_desc), fontSize = 11.sp, color = AppTextSecondary)
                        }
                        Switch(
                            checked = eventVideoRecordingEnabled,
                            onCheckedChange = { viewModel.updateEventVideoRecordingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Dynamic FPS Adjustment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_dynamic_fps_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.settings_dynamic_fps_desc), fontSize = 11.sp, color = AppTextSecondary)
                        }
                        Switch(
                            checked = dynamicFpsAdjustmentEnabled,
                            onCheckedChange = { viewModel.updateDynamicFpsAdjustmentEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Alarm Buzzer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_alarm_sound), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Switch(
                            checked = playAlarm,
                            onCheckedChange = { viewModel.updatePlayAlarm(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Motion Schedule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_motion_schedule_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.settings_motion_schedule_desc), fontSize = 11.sp, color = AppTextSecondary)
                        }
                        Switch(
                            checked = motionScheduleEnabled,
                            onCheckedChange = { viewModel.updateMotionScheduleEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    AnimatedVisibility(visible = motionScheduleEnabled) {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                OutlinedButton(
                                    onClick = {
                                        val parts = motionScheduleStartTime.split(":")
                                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                            viewModel.updateMotionScheduleStartTime(String.format("%02d:%02d", hourOfDay, minute))
                                        }, h, m, true).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.settings_schedule_start, motionScheduleStartTime), fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val parts = motionScheduleEndTime.split(":")
                                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
                                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                            viewModel.updateMotionScheduleEndTime(String.format("%02d:%02d", hourOfDay, minute))
                                        }, h, m, true).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.settings_schedule_end, motionScheduleEndTime), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Notification Schedule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_notif_schedule_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.settings_notif_schedule_desc), fontSize = 11.sp, color = AppTextSecondary)
                        }
                        Switch(
                            checked = notificationScheduleEnabled,
                            onCheckedChange = { viewModel.updateNotificationScheduleEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    AnimatedVisibility(visible = notificationScheduleEnabled) {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                OutlinedButton(
                                    onClick = {
                                        val parts = notificationScheduleStartTime.split(":")
                                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                            viewModel.updateNotificationScheduleStartTime(String.format("%02d:%02d", hourOfDay, minute))
                                        }, h, m, true).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.settings_schedule_start, notificationScheduleStartTime), fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val parts = notificationScheduleEndTime.split(":")
                                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
                                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                            viewModel.updateNotificationScheduleEndTime(String.format("%02d:%02d", hourOfDay, minute))
                                        }, h, m, true).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.settings_schedule_end, notificationScheduleEndTime), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 4. 🤖 AI 智慧過濾與 Telegram 告警 (AI Filter & Telegram Notifications)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // AI Section Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_ai_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_ai_smart_filter_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.settings_ai_smart_filter_desc), color = AppTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = mlKitFilterEnabled,
                            onCheckedChange = { viewModel.updateMlKitFilterEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showNotificationSettings = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppSecondaryContainer, contentColor = AppOnSecondaryContainer)
                    ) {
                        Text(stringResource(R.string.settings_ai_btn_categories), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = AppBorderLight)

                    // Telegram Subhead
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_telegram_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { viewModel.updateBotToken(it) },
                        label = { Text(stringResource(R.string.settings_telegram_bot_token)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { viewModel.updateChatId(it) },
                        label = { Text(stringResource(R.string.settings_telegram_chat_id)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(stringResource(R.string.settings_telegram_media_type), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
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
                            val isSelected = telegramSendMediaType == typeKey
                            OutlinedButton(
                                onClick = { viewModel.updateTelegramSendMediaType(typeKey) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) AppPrimary else AppBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AppSecondaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) AppPrimary else AppTextSecondary
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showTelegramSetupDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AppPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_telegram_auto_pair), fontWeight = FontWeight.Bold, color = AppPrimary, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.testTelegram() },
                        enabled = !isTesting && botToken.isNotBlank() && chatId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.height(18.dp).width(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_telegram_testing), fontSize = 13.sp)
                        } else {
                            Text(stringResource(R.string.settings_telegram_test_btn), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    AnimatedVisibility(visible = testStatus != null) {
                        testStatus?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, color = if (msg.contains("成功") || msg.contains("Success", ignoreCase = true)) AppSuccess else AppError, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 5. 💾 儲存空間與清理維護 (Storage Space & Auto-Cleanup)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_storage_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_auto_cleanup_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.settings_auto_cleanup_desc), color = AppTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoCleanupEnabled,
                            onCheckedChange = { viewModel.updateAutoCleanupEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    AnimatedVisibility(visible = autoCleanupEnabled) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            // Storage Limit GB
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.settings_storage_limit_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                    Text(String.format("%.1f GB", localStorageGB), color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Slider(
                                    value = localStorageGB,
                                    onValueChange = {
                                        localStorageGB = it
                                        viewModel.updateStorageLimitGB(it)
                                    },
                                    valueRange = 0.5f..10.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = brandPrimaryColor,
                                        activeTrackColor = brandPrimaryColor,
                                        inactiveTrackColor = AppSecondaryContainer
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Max Events Limit
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.settings_events_limit_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimaryColor)
                                    Text(stringResource(R.string.settings_events_count, localMaxEvents.toInt()), color = brandPrimaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Slider(
                                    value = localMaxEvents,
                                    onValueChange = {
                                        localMaxEvents = it
                                        viewModel.updateMaxEventCount(it.toInt())
                                    },
                                    valueRange = 50f..500f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(
                                        thumbColor = brandPrimaryColor,
                                        activeTrackColor = brandPrimaryColor,
                                        inactiveTrackColor = AppSecondaryContainer
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = AppBorderLight)

                    OutlinedButton(
                        onClick = { viewModel.performManualCleanup() },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, AppError),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AppError, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_btn_delete_old_records), color = AppError, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    AnimatedVisibility(visible = cleanupStatus != null) {
                        cleanupStatus?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, color = AppSuccess, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else {
            // ==================== 觀看端專用設定 ====================

            // Live Stream Preview Setting Card (觀看端全鏡頭即時預覽)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = AppPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.settings_live_preview_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (livePreviewInListEnabled) stringResource(R.string.settings_live_preview_desc_on) else stringResource(R.string.settings_live_preview_desc_off),
                                    color = AppTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = livePreviewInListEnabled,
                            onCheckedChange = { viewModel.updateLivePreviewInListEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary
                            )
                        )
                    }
                }
            }

            // Notification Category Settings Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_ai_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_ai_viewer_subtitle), fontSize = 13.sp, color = AppTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_ai_smart_filter_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.settings_ai_smart_filter_desc), color = AppTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = mlKitFilterEnabled,
                            onCheckedChange = { viewModel.updateMlKitFilterEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppPrimary,
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSecondaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showNotificationSettings = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppSecondaryContainer, contentColor = AppOnSecondaryContainer)
                    ) {
                        Text(stringResource(R.string.settings_ai_btn_categories), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Telegram Bot Card (Viewer Mode with Sync to Cameras option)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = AppPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_telegram_viewer_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.settings_telegram_viewer_desc), color = AppTextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { viewModel.updateBotToken(it) },
                        label = { Text(stringResource(R.string.settings_telegram_bot_token)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { viewModel.updateChatId(it) },
                        label = { Text(stringResource(R.string.settings_telegram_chat_id)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showTelegramSetupDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, AppPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_telegram_auto_pair), fontWeight = FontWeight.Bold, color = AppPrimary, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(stringResource(R.string.settings_telegram_media_type), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
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
                            val isSelected = telegramSendMediaType == typeKey
                            OutlinedButton(
                                onClick = { viewModel.updateTelegramSendMediaType(typeKey) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) AppPrimary else AppBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AppSecondaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) AppPrimary else AppTextSecondary
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testTelegram() },
                            enabled = !isTesting && botToken.isNotBlank() && chatId.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.height(18.dp).width(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_telegram_testing), fontSize = 13.sp)
                            } else {
                                Text(stringResource(R.string.settings_telegram_test_btn), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.syncTelegramToCameras() },
                            enabled = !isSyncing && (botToken.isNotBlank() || chatId.isNotBlank()),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.5.dp, AppPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = AppPrimary, modifier = Modifier.height(18.dp).width(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_telegram_syncing), fontSize = 13.sp)
                            } else {
                                Text(stringResource(R.string.settings_telegram_sync_btn), color = AppPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    AnimatedVisibility(visible = syncStatus != null) {
                        syncStatus?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val statusColor = when {
                                msg.contains("⚡") || msg.contains("成功") || msg.contains("Success", ignoreCase = true) -> AppSuccess
                                msg.contains("⏳") -> AppPrimary
                                msg.contains("⚠️") -> AppWarning
                                else -> AppError
                            }
                            Text(msg, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    AnimatedVisibility(visible = testStatus != null) {
                        testStatus?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, color = if (msg.contains("成功") || msg.contains("Success", ignoreCase = true)) AppSuccess else AppError, fontSize = 13.sp)
                        }
                    }
                }
            }

        }

        // ==================== ⚡ 進階連線模式 (WebRTC P2P vs Tailscale VPN) ====================
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, AppBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = AppPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settings_connection_mode_group),
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_connection_mode_desc),
                    color = AppTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // WebRTC P2P (Default)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (connectionMode == "WEBRTC") AppPrimaryContainer else AppBackground,
                    border = BorderStroke(1.dp, if (connectionMode == "WEBRTC") AppPrimary else AppBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.updateConnectionMode("WEBRTC") }
                ) {
                    Text(
                        stringResource(R.string.settings_connection_mode_webrtc),
                        color = if (connectionMode == "WEBRTC") AppOnPrimaryContainer else AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tailscale VPN (Advanced)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (connectionMode == "TAILSCALE") AppPrimaryContainer else AppBackground,
                    border = BorderStroke(1.dp, if (connectionMode == "TAILSCALE") AppPrimary else AppBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.updateConnectionMode("TAILSCALE") }
                ) {
                    Text(
                        stringResource(R.string.settings_connection_mode_tailscale),
                        color = if (connectionMode == "TAILSCALE") AppOnPrimaryContainer else AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // ==================== 🌐 進階網路與 WebRTC TURN 中繼設定 (選填) ====================
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, AppBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = AppPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settings_webrtc_advanced_group),
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_webrtc_advanced_desc),
                    color = AppTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = customTurnServerUrl,
                    onValueChange = { viewModel.updateCustomTurnServerUrl(it) },
                    label = { Text(stringResource(R.string.settings_turn_server_url)) },
                    placeholder = { Text("turn:turn.cloudflare.com:3478", color = AppTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customTurnUsername,
                    onValueChange = { viewModel.updateCustomTurnUsername(it) },
                    label = { Text(stringResource(R.string.settings_turn_username)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customTurnPassword,
                    onValueChange = { viewModel.updateCustomTurnPassword(it) },
                    label = { Text(stringResource(R.string.settings_turn_password)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ==================== 🔐 專用設備 Kiosk 死鎖與維護逃生門 (Escape Hatch) ====================
        val context = androidx.compose.ui.platform.LocalContext.current
        val activity = context as? android.app.Activity
        val dpm = remember(context) { context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager }
        val isDeviceOwner = remember(context, dpm) { dpm?.isDeviceOwnerApp(context.packageName) == true }

        // 僅在專用設備 (Device Owner) 上顯示逃生門卡片
        if (isDeviceOwner) {
            val settingsManager = remember { io.github.iokkai.ocularnode.data.SettingsManager.getInstance(context) }
            var isKioskActive by remember { mutableStateOf(settingsManager.isKioskModeActive) }
            var escapeHatchClicks by remember { mutableStateOf(0) }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppPrimary),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = AppPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_kiosk_group), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isKioskActive) stringResource(R.string.settings_kiosk_status_locked) else stringResource(R.string.settings_kiosk_status_unlocked),
                        fontSize = 12.sp,
                        color = if (isKioskActive) AppPrimary else AppSuccess,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                activity?.let {
                                    val success = io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager.enableKioskMode(it)
                                    if (success) {
                                        isKioskActive = true
                                        escapeHatchClicks = 0
                                    }
                                }
                            },
                            enabled = !isKioskActive,
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_kiosk_enable), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                escapeHatchClicks++
                                if (escapeHatchClicks >= 5) {
                                    escapeHatchClicks = 0
                                    activity?.let {
                                        val success = io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager.disableKioskMode(it)
                                        if (success) {
                                            isKioskActive = false
                                        }
                                    }
                                }
                            },
                            enabled = isKioskActive,
                            border = BorderStroke(1.5.dp, if (isKioskActive) AppError else AppBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isKioskActive) AppError else AppTextDisabled,
                                disabledContentColor = AppTextDisabled
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (escapeHatchClicks > 0 && isKioskActive) stringResource(R.string.settings_kiosk_escape_counter, escapeHatchClicks) else stringResource(R.string.settings_kiosk_escape),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ==================== ℹ️ 關於 OcularNode ====================
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, AppBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutScreen = true }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AppPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.about_title),
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                color = AppTextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val buildChannel = BuildConfig.BUILD_CHANNEL
                            val badgeColor = when {
                                BuildConfig.DEBUG -> Color(0xFFF59E0B)
                                buildChannel.contains("Local", ignoreCase = true) -> Color(0xFF3B82F6)
                                else -> Color(0xFF10B981)
                            }
                            val badgeText = when {
                                BuildConfig.DEBUG -> "Debug"
                                buildChannel.contains("Local", ignoreCase = true) -> "Local"
                                else -> "Release"
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = badgeColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Open About",
                    tint = AppTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

