package io.github.iokkai.ocularnode.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    val telegramSetupUiState by telegramSetupViewModel.uiState.collectAsState()
    LaunchedEffect(telegramSetupUiState) {
        if (telegramSetupUiState !is TelegramSetupUiState.Step1_InputToken) {
            showTelegramSetupDialog = true
        }
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
    val storageLimitGB by viewModel.storageLimitGB.collectAsState()
    val maxEventCount by viewModel.maxEventCount.collectAsState()
    val cleanupStatus by viewModel.cleanupStatus.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var localStorageGB by remember(storageLimitGB) { mutableStateOf(storageLimitGB) }
    var localMaxEvents by remember(maxEventCount) { mutableStateOf(maxEventCount.toFloat()) }
    val brandPrimaryColor = Color(0xFF6750A4)
    val textPrimaryColor = Color(0xFF1C1B1F)

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDF8FF))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("系統設定", color = Color(0xFF1C1B1F), fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        val isCamera = roleMode == "CAMERA" || roleMode == "UNSET"

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

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                        Text("鏡頭端", fontWeight = FontWeight.Bold)
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
                        Text("觀看端", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isCamera) {
            // ==================== 📷 鏡頭端專用設定 ====================

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

            // Auto-Start on Boot Card (開機/復電自動啟動)
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
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFF6750A4))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("開機自動啟動", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (autoStartOnBoot) "停電復電重啟手機後，自動在背景啟動" else "開機後需手動開啟監控",
                                    color = Color(0xFF49454F),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = autoStartOnBoot,
                            onCheckedChange = { viewModel.updateAutoStartOnBoot(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4)
                            )
                        )
                    }
                }
            }

            // Power Cut & Low Battery Telegram Alert Card (斷電與低電量推播)
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
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = Color(0xFFB3261E))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("斷電與低電量警報", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (powerCutAlertEnabled) "由「充電中」轉為「放電中」或電量低於 60% 時立即發送 Telegram 警報" else "關閉電源與低電量警報推播",
                                    color = Color(0xFF49454F),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = powerCutAlertEnabled,
                            onCheckedChange = { viewModel.updatePowerCutAlertEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFB3261E)
                            )
                        )
                    }
                }
            }

            // Motion Sensitivity Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("動態警報敏感度與冷卻", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "動態差異觸發門檻 (Sensitivity)",
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
                            "低於此百分比的畫面變動將被忽略 (1% = 極度敏感，100% = 需要全畫面變動)",
                            fontSize = 11.sp,
                            color = Color(0xFF49454F)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = sensitivity,
                            onValueChange = { viewModel.updateSensitivity(it) },
                            valueRange = 1f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = brandPrimaryColor,
                                activeTrackColor = brandPrimaryColor,
                                inactiveTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = cooldown,
                        onValueChange = { viewModel.updateCooldown(it) },
                        label = { Text("冷卻時間 (秒，避免連續發送)") },
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

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("事件動態延長錄影", color = Color(0xFF49454F), fontWeight = FontWeight.Medium)
                            Text("開啟後，動態事件將自動錄製含前 5 秒緩衝的影片，並依據持續動態自動延長 (最長 3 分鐘)", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = eventVideoRecordingEnabled,
                            onCheckedChange = { viewModel.updateEventVideoRecordingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4),
                                uncheckedThumbColor = Color(0xFF49454F),
                                uncheckedTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("動態調整 FPS", color = Color(0xFF49454F), fontWeight = FontWeight.Medium)
                            Text("根據設備效能監測自動降低幀數以穩定傳輸，避免舊機型過熱或卡頓", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = dynamicFpsAdjustmentEnabled,
                            onCheckedChange = { viewModel.updateDynamicFpsAdjustmentEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4),
                                uncheckedThumbColor = Color(0xFF49454F),
                                uncheckedTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }

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

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("監控排程 (自動啟用/停用)", color = Color(0xFF49454F), fontWeight = FontWeight.Medium)
                            Text("開啟後，僅在指定時段自動開啟動態偵測", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = motionScheduleEnabled,
                            onCheckedChange = { viewModel.updateMotionScheduleEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4),
                                uncheckedThumbColor = Color(0xFF49454F),
                                uncheckedTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }

                    AnimatedVisibility(visible = motionScheduleEnabled) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("開始: $motionScheduleStartTime")
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("結束: $motionScheduleEndTime")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通知排程 (自動啟用/停用通知)", color = Color(0xFF49454F), fontWeight = FontWeight.Medium)
                            Text("開啟後，僅在指定時段自動發送告警與推播通知", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = notificationScheduleEnabled,
                            onCheckedChange = { viewModel.updateNotificationScheduleEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4),
                                uncheckedThumbColor = Color(0xFF49454F),
                                uncheckedTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }

                    AnimatedVisibility(visible = notificationScheduleEnabled) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("開始: $notificationScheduleStartTime")
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("結束: $notificationScheduleEndTime")
                                }
                            }
                        }
                    }
                }
            }

            // Telegram Bot Card (Camera Mode)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF6750A4))
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

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("告警媒體類型", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1C1B1F))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val types = listOf("photo" to "📸 照片", "video" to "🎥 影片", "both" to "🖼️ 照片+影片")
                        types.forEach { (typeKey, label) ->
                            val isSelected = telegramSendMediaType == typeKey
                            OutlinedButton(
                                onClick = { viewModel.updateTelegramSendMediaType(typeKey) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF6750A4) else Color(0xFFCAC4D0)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) Color(0xFFE8DEF8) else Color.Transparent,
                                    contentColor = if (isSelected) Color(0xFF6750A4) else Color(0xFF49454F)
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showTelegramSetupDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF6750A4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🤖 自動配對 Chat ID", fontWeight = FontWeight.Bold, color = Color(0xFF6750A4), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.testTelegram() },
                        enabled = !isTesting && botToken.isNotBlank() && chatId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.height(18.dp).width(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("測試中...", fontSize = 13.sp)
                        } else {
                            Text("測試連線", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

            // Notification Category Settings Card (Camera Mode)
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("通知過濾與分類設定", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("設定偵測到各類別物件時是否觸發通知。", fontSize = 10.sp, color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ML Kit 智慧過濾", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("開啟後啟用本機 AI 分析物件類別", color = Color(0xFF49454F), fontSize = 10.sp)
                        }
                        Switch(
                            checked = mlKitFilterEnabled,
                            onCheckedChange = { viewModel.updateMlKitFilterEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4),
                                uncheckedThumbColor = Color(0xFF49454F),
                                uncheckedTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showNotificationSettings = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B))
                    ) {
                        Text("前往設定各分類通知", fontWeight = FontWeight.Bold)
                    }
                }
            }

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
                        Text("儲存空間與紀錄清理", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自動清理", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("超過上限時自動刪除日期最早的偵測影像與紀錄", color = Color(0xFF49454F), fontSize = 10.sp)
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

                        // Storage Limit GB
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("儲存空間上限", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimaryColor)
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
                                    inactiveTrackColor = Color(0xFFE8DEF8)
                                )
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
                                onValueChange = {
                                    localMaxEvents = it
                                    viewModel.updateMaxEventCount(it.toInt())
                                },
                                valueRange = 50f..500f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = brandPrimaryColor,
                                    activeTrackColor = brandPrimaryColor,
                                    inactiveTrackColor = Color(0xFFE8DEF8)
                                )
                            )
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
                        Text("🧹 刪除紀錄 (保留最新20%)", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    AnimatedVisibility(visible = cleanupStatus != null) {
                        cleanupStatus?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(msg, color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // System Logs (Only for Camera)
            if (isCamera) {
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("啟用系統日誌", color = Color(0xFF49454F), fontWeight = FontWeight.Medium)
                        Text("開啟後會紀錄運作狀態", fontSize = 10.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = systemLogEnabled,
                        onCheckedChange = { viewModel.updateSystemLogEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6750A4),
                            uncheckedThumbColor = Color(0xFF49454F),
                            uncheckedTrackColor = Color(0xFFE8DEF8)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showSystemLogs = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFF6750A4))
                ) {
                    Text("📝 查看系統日誌", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            // ==================== 觀看端專用設定 ====================

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
                                Text("即時預覽", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (livePreviewInListEnabled) "列表頁將持續顯示即時畫面" else "預設使用靜態快照",
                                    color = Color(0xFF49454F),
                                    fontSize = 10.sp
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

            // Notification Category Settings Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("通知過濾與分類設定", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("設定各類別物件 (人類、寵物、車輛等) 是否觸發動態通知。", fontSize = 13.sp, color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ML Kit 智慧過濾", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("開啟後啟用本機 AI 分析物件類別", color = Color(0xFF49454F), fontSize = 10.sp)
                        }
                        Switch(
                            checked = mlKitFilterEnabled,
                            onCheckedChange = { viewModel.updateMlKitFilterEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4),
                                uncheckedThumbColor = Color(0xFF49454F),
                                uncheckedTrackColor = Color(0xFFE8DEF8)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showNotificationSettings = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B))
                    ) {
                        Text("前往設定各分類通知", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Telegram Bot Card (Viewer Mode with Sync to Cameras option)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF6750A4))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Telegram 機器人設定與同步", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("此處設定可一鍵同步給所有鏡頭端，由鏡頭端發送 Telegram 警報通知", color = Color(0xFF49454F), fontSize = 13.sp)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showTelegramSetupDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF6750A4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🤖 自動配對 Chat ID", fontWeight = FontWeight.Bold, color = Color(0xFF6750A4), fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("告警媒體類型", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1C1B1F))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val types = listOf("photo" to "📸 照片", "video" to "🎥 影片", "both" to "🖼️ 照片+影片")
                        types.forEach { (typeKey, label) ->
                            val isSelected = telegramSendMediaType == typeKey
                            OutlinedButton(
                                onClick = { viewModel.updateTelegramSendMediaType(typeKey) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF6750A4) else Color(0xFFCAC4D0)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) Color(0xFFE8DEF8) else Color.Transparent,
                                    contentColor = if (isSelected) Color(0xFF6750A4) else Color(0xFF49454F)
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

                        OutlinedButton(
                            onClick = { viewModel.syncTelegramToCameras() },
                            enabled = !isSyncing && (botToken.isNotBlank() || chatId.isNotBlank()),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF6750A4)),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = Color(0xFF6750A4), modifier = Modifier.height(18.dp).width(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("同步中...", fontSize = 13.sp)
                            } else {
                                Text("🔄 同步至鏡頭", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    AnimatedVisibility(visible = syncStatus != null) {
                        syncStatus?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val statusColor = when {
                                msg.contains("⚡") || msg.contains("成功") -> Color(0xFF2E7D32)
                                msg.contains("⏳") -> Color(0xFF6750A4)
                                msg.contains("⚠️") -> Color(0xFFE65100)
                                else -> Color(0xFFB3261E)
                            }
                            Text(msg, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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

        }

        // ==================== 🔐 專用設備 Kiosk 死鎖與維護逃生門 (Escape Hatch) ====================
        val context = androidx.compose.ui.platform.LocalContext.current
        val activity = context as? android.app.Activity
        val dpm = remember(context) { context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager }
        val isDeviceOwner = remember(context, dpm) { dpm?.isDeviceOwnerApp(context.packageName) == true }

        // 僅在專用設備 (Device Owner) 上顯示逃生門卡片
        if (isDeviceOwner) {
            val settingsManager = remember { io.github.iokkai.ocularnode.data.SettingsManager(context) }
            var isKioskActive by remember { mutableStateOf(settingsManager.isKioskModeActive) }
            var escapeHatchClicks by remember { mutableStateOf(0) }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF6750A4)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = Color(0xFF6750A4)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("專用設備 Kiosk 與維護逃生門", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isKioskActive) "已取得 Device Owner 特權，當前處於 Kiosk 鎖定狀態" else "已取得 Device Owner 特權，當前為維護模式 (Kiosk 未鎖定)",
                        fontSize = 12.sp,
                        color = if (isKioskActive) Color(0xFF6750A4) else Color(0xFF2E7D32),
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔒 啟動 Kiosk 死鎖", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            border = BorderStroke(1.5.dp, if (isKioskActive) Color(0xFFB3261E) else Color(0xFFCAC4D0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isKioskActive) Color(0xFFB3261E) else Color(0xFF9E9E9E),
                                disabledContentColor = Color(0xFF9E9E9E)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (escapeHatchClicks > 0 && isKioskActive) "逃生門 (${escapeHatchClicks}/5)" else "🚨 解除死鎖 (連擊5次)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

