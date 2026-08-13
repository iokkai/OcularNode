package io.github.iokkai.ocularnode.ui.viewer

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.filled.ExpandLess
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.ExpandMore

import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun LiveMonitorScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val camera = viewModel.selectedCamera.collectAsState().value
    val frame by viewModel.streamClient.currentFrame.collectAsState()
    val isConnected by viewModel.streamClient.isConnected.collectAsState()
    val isConnecting by viewModel.streamClient.isConnecting.collectAsState()
    val fps by viewModel.streamClient.fps.collectAsState()
    val statusMsg by viewModel.streamClient.statusMessage.collectAsState()
    val cameraStatusJson by viewModel.streamClient.cameraStatusJson.collectAsState()
    val nightMode = cameraStatusJson?.optString("nightVisionMode", "off") ?: "off"

    val isListening by viewModel.streamClient.isListeningAudio.collectAsState()
    val isSpeaking by viewModel.streamClient.isSpeakingAudio.collectAsState()
    val adaptiveState by viewModel.adaptiveState.collectAsState()

    var torchOn by remember { mutableStateOf(false) }
    var showRemoteSettingsDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val currentStreamRotation = cameraStatusJson?.optInt("streamRotation", 0) ?: 0

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Control panel collapse/expand state to avoid blocking video stream
    var isControlPanelExpanded by remember { mutableStateOf(false) }

    if (showRemoteSettingsDialog && camera != null) {
        RemoteSettingsScreen(
            cameraName = camera.name,
            cameraStatusJson = cameraStatusJson,
            onSendCommand = { cmd, valStr -> viewModel.sendControlCommandSuspend(cmd, valStr) },
            onSaveBatchConfig = { jsonStr -> viewModel.saveRemoteConfig(camera, jsonStr) },
            onSyncTelegram = { viewModel.syncTelegramToCurrentCamera() },
            onNavigateBack = { showRemoteSettingsDialog = false },
            onFetchLogs = { viewModel.fetchRemoteLogs(camera) }
        )
        return
    }

    if (camera == null) {
        onBack()
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // MJPEG Video Frame Canvas with Zoom & Rotation
        if (frame != null) {
            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = "Live Stream Frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                            if (zoomScale == 1f) {
                                panOffset = Offset.Zero
                            } else {
                                panOffset += pan
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(color = Color(0xFF6750A4))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(statusMsg, color = Color.LightGray, fontSize = 15.sp)
            }
        }

        // Top-Left Adaptive Mode Badge Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 68.dp, start = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (adaptiveState.isDowngraded) Color(0xEEE65100)
                    else if (adaptiveState.isEnabled) Color(0xEE4A148C)
                    else Color(0xEE374151)
                )
                .clickable {
                    viewModel.setAdaptiveModeEnabled(!adaptiveState.isEnabled)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = adaptiveState.labelText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // Top-Left Zoom Overlay Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 108.dp, start = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xAA0F172A))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { zoomScale = (zoomScale - 0.25f).coerceIn(1f, 5f) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "縮小", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { zoomScale = 1f; panOffset = Offset.Zero }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format("%.1fx", zoomScale),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            IconButton(
                onClick = { zoomScale = (zoomScale + 0.25f).coerceIn(1f, 5f) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "放大", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Top-Right Rotate Overlay Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xAA0F172A))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.sendControlCommandSuspend("rotation", "-1")
                    }
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.RotateLeft, contentDescription = "逆時鐘旋轉", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.sendControlCommandSuspend("rotation", "+1")
                    }
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = "順時鐘旋轉", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Top Status Header Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xF2FDF8FF))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    viewModel.disconnectCamera()
                    onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1B1F))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(camera.name, color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val cpuVal = cameraStatusJson?.optInt("cpuUsage", -1) ?: -1
                    val memVal = cameraStatusJson?.optInt("memoryUsage", -1) ?: -1
                    val pingVal = cameraStatusJson?.optInt("pingMs", -1) ?: -1

                    val cpuText = if (cpuVal >= 0) "CPU: $cpuVal%" else "CPU: --"
                    val memText = if (memVal >= 0) "RAM: $memVal%" else "RAM: --"
                    val pingText = if (pingVal >= 0) "Ping: ${pingVal}ms" else "Ping: --"
                    val adaptiveStatusStr = if (adaptiveState.isDowngraded) "⚡ Adaptive: ${adaptiveState.currentResolution} (${adaptiveState.reasonText})" else if (adaptiveState.isEnabled) "⚡ Adaptive: ${adaptiveState.currentResolution}" else "⚡ Adaptive: Off"

                    Text("$cpuText | $memText | $pingText\n$adaptiveStatusStr", color = Color(0xFF6750A4), fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isConnected) Color(0xFF2E7D32) else Color(0xFFB3261E))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isConnected) "LIVE ${fps}FPS" else "中斷重連中",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { showRemoteSettingsDialog = true },
                    modifier = Modifier.size(34.dp).background(Color(0xFFE8DEF8), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                }
            }
        }

        // Bottom Collapsible Remote Control Panel
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xDC1E1B2E)),
            border = BorderStroke(1.dp, Color(0x66CAC4D0)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header Bar for Control Panel: Audio, Talk & Expand Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Talk & Listen Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Listen Camera Audio Button
                        IconButton(
                            onClick = { viewModel.toggleAudioListening() },
                            modifier = Modifier
                                .size(38.dp)
                                .background(if (isListening) Color(0xFF2E7D32) else Color(0x44FFFFFF), CircleShape)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Push-to-Talk Walkie-Talkie Button
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(if (isSpeaking) Color(0xFFB3261E) else Color(0xFF6750A4))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            viewModel.toggleAudioSpeaking()
                                            tryAwaitRelease()
                                            viewModel.toggleAudioSpeaking()
                                        }
                                    )
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSpeaking) "🎙️ 發話中" else "按住對講",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Panel Expand / Collapse Toggle
                    TextButton(
                        onClick = { isControlPanelExpanded = !isControlPanelExpanded },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isControlPanelExpanded) "收合控制項" else "展開更多控制",
                            color = Color(0xFFD0BCFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (isControlPanelExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "Expand",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Expanded Controls View
                if (isControlPanelExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Switch Camera
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { viewModel.sendControlCommand("camera", "switch") },
                                modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)
                            ) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                            }
                            Text("切換鏡頭", color = Color.White, fontSize = 10.sp)
                        }

                        // Torch
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    torchOn = !torchOn
                                    viewModel.sendControlCommand("torch", if (torchOn) "on" else "off")
                                },
                                modifier = Modifier.background(if (torchOn) Color(0xFFEADDFF) else Color(0x33FFFFFF), CircleShape)
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = "Torch", tint = if (torchOn) Color(0xFF21005D) else Color.White)
                            }
                            Text("補光燈", color = Color.White, fontSize = 10.sp)
                        }

                        // Night Vision
                        val currentNightMode = nightMode.lowercase()
                        val nightIcon = when (currentNightMode) {
                            "auto" -> Icons.Default.BrightnessAuto
                            else -> Icons.Default.Nightlight
                        }
                        val nightLabel = when (currentNightMode) {
                            "on" -> "夜視：黑白"
                            "auto" -> "夜視：自動"
                            else -> "夜視：關閉"
                        }
                        val nightBgColor = when (currentNightMode) {
                            "on" -> Color(0xFFEADDFF)
                            "auto" -> Color(0xFFD0BCFF)
                            else -> Color(0x33FFFFFF)
                        }
                        val nightIconTint = when (currentNightMode) {
                            "on", "auto" -> Color(0xFF21005D)
                            else -> Color.White
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val (nextMode, toastMsg) = when (currentNightMode) {
                                        "off" -> "on" to "已開啟黑白夜視模式"
                                        "on" -> "auto" to "已切換至自動夜視模式"
                                        "auto" -> "off" to "已關閉夜視模式"
                                        else -> "on" to "已開啟黑白夜視模式"
                                    }
                                    viewModel.sendControlCommand("night_vision", nextMode)
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(nightBgColor, CircleShape)
                            ) {
                                Icon(nightIcon, contentDescription = nightLabel, tint = nightIconTint)
                            }
                            Text(nightLabel, color = Color.White, fontSize = 10.sp)
                        }

                        // Snapshot
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    if (frame != null) {
                                        Toast.makeText(context, "已快照當前畫面", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Snapshot", tint = Color.White)
                            }
                            Text("快照", color = Color.White, fontSize = 10.sp)
                        }

                        // Adaptive Resolution Toggle
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val nextState = !adaptiveState.isEnabled
                                    viewModel.setAdaptiveModeEnabled(nextState)
                                    Toast.makeText(context, if (nextState) "已開啟動態自適應解析度" else "已關閉自適應模式", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(if (adaptiveState.isEnabled) Color(0xFFD0BCFF) else Color(0x33FFFFFF), CircleShape)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Adaptive Mode", tint = if (adaptiveState.isEnabled) Color(0xFF21005D) else Color.White)
                            }
                            Text(if (adaptiveState.isEnabled) "自適應: ON" else "自適應: OFF", color = Color.White, fontSize = 10.sp)
                        }

                        // Alarm
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    viewModel.sendControlCommand("alarm", "trigger")
                                    Toast.makeText(context, "已遠端發送警報蜂鳴", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(Color(0xAA8C1D18), CircleShape)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Alarm", tint = Color.White)
                            }
                            Text("警報", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
