package io.github.iokkai.ocularnode.ui.viewer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.ui.theme.*
import io.github.iokkai.ocularnode.util.ScannedCameraInfo

import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay

@Composable
fun ViewerListScreen(
    viewModel: ViewerViewModel,
    onSelectCamera: (CameraDevice) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraList by viewModel.cameraList.collectAsState()
    val isTailscaleConnected by viewModel.isTailscaleConnected.collectAsState()
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val tailscaleIp by viewModel.tailscaleIp.collectAsState()
    val isTailscaleActive = isTailscaleConnected
    val devicesExpiryMap by viewModel.devicesExpiryMap.collectAsState()
    val isDisablingKeyExpiry by viewModel.isDisablingKeyExpiry.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var showDedicatedDeviceWizard by remember { mutableStateOf(false) }
    var isSpeedDialExpanded by remember { mutableStateOf(false) }

    val fabRotationAngle by animateFloatAsState(
        targetValue = if (isSpeedDialExpanded) 45f else 0f,
        label = "fab_rotation"
    )

    var prefilledInfo by remember { mutableStateOf<ScannedCameraInfo?>(null) }
    var editingCamera by remember { mutableStateOf<CameraDevice?>(null) }
    var remoteSettingsCamera by remember { mutableStateOf<CameraDevice?>(null) }
    var remoteStatusJson by remember { mutableStateOf<JSONObject?>(null) }

    val settingsManager = remember { io.github.iokkai.ocularnode.data.SettingsManager.getInstance(context) }
    val isLivePreviewAllEnabled = settingsManager.livePreviewInListEnabled

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkInfo()
    }

    if (showDedicatedDeviceWizard) {
        DedicatedDeviceWizardScreen(
            onBack = { showDedicatedDeviceWizard = false }
        )
        return
    }

    if (remoteSettingsCamera != null) {
        val camera = remoteSettingsCamera!!
        RemoteSettingsScreen(
            cameraName = camera.name,
            cameraStatusJson = remoteStatusJson,
            onSendCommand = { cmd, valStr ->
                viewModel.sendControlCommandToCameraSuspend(camera, cmd, valStr)
            },
            onSaveBatchConfig = { jsonStr ->
                viewModel.saveRemoteConfig(camera, jsonStr)
            },
            onSyncTelegram = {
                val token = viewModel.settingsManager.telegramBotToken
                val chatId = viewModel.settingsManager.telegramChatId
                val json = org.json.JSONObject().apply {
                    put("token", token)
                    put("chatId", chatId)
                }.toString()
                viewModel.sendControlCommandToCamera(camera, "telegram_config", json)
            },
            onNavigateBack = {
                remoteSettingsCamera = null
                remoteStatusJson = null
            }
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = isSpeedDialExpanded,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 子選項 1：🛠️ 製作專用設備
                        Surface(
                            onClick = {
                                isSpeedDialExpanded = false
                                showDedicatedDeviceWizard = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = AppPrimary,
                            contentColor = Color.White,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🛠️", fontSize = 16.sp)
                                Text(
                                    text = stringResource(R.string.btn_provision_device),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 子選項 2：📷 掃描 QR code
                        Surface(
                            onClick = {
                                isSpeedDialExpanded = false
                                showQrScannerDialog = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = AppPrimary,
                            contentColor = Color.White,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📷", fontSize = 16.sp)
                                Text(
                                    text = stringResource(R.string.btn_scan_qr),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 子選項 3：⌨️ 手動輸入 IP
                        Surface(
                            onClick = {
                                isSpeedDialExpanded = false
                                prefilledInfo = null
                                showAddDialog = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = AppPrimary,
                            contentColor = Color.White,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⌨️", fontSize = 16.sp)
                                Text(
                                    text = stringResource(R.string.btn_manual_ip),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isSpeedDialExpanded = !isSpeedDialExpanded },
                    containerColor = AppPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (isSpeedDialExpanded) stringResource(R.string.viewer_menu_close) else stringResource(R.string.viewer_menu_add),
                        modifier = Modifier.rotate(fabRotationAngle)
                    )
                }
            }
        },
        containerColor = AppBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.viewer_title),
                        color = AppTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.viewer_header_subtitle),
                        color = AppTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tailscale Status Bar for Viewer (Green when connected, Red when disconnected)
            if (isTailscaleActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppSuccessContainer)
                        .border(1.dp, AppSuccessBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(AppSuccess)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.viewer_tailscale_connected),
                                color = AppSuccessDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(R.string.viewer_tailscale_connected_desc),
                                color = AppSuccess,
                                fontSize = 10.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = { io.github.iokkai.ocularnode.util.NetworkUtils.openTailscaleApp(context) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("🚀", color = AppSuccessDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppErrorContainerLight)
                        .border(1.dp, AppErrorBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(AppErrorBright)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.viewer_tailscale_disconnected),
                                color = AppErrorDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(R.string.viewer_tailscale_disconnected_desc),
                                color = AppErrorDark,
                                fontSize = 10.sp
                            )
                        }
                    }

                    val isTailscaleInstalled = remember(context) { io.github.iokkai.ocularnode.util.NetworkUtils.isTailscaleInstalled(context) }
                    TextButton(
                        onClick = { io.github.iokkai.ocularnode.util.NetworkUtils.openTailscaleApp(context) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(if (isTailscaleInstalled) stringResource(R.string.viewer_tailscale_open) else stringResource(R.string.viewer_tailscale_install), color = AppErrorDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (cameraList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = AppTextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.viewer_empty_title), color = AppTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.viewer_empty_desc), color = AppTextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showQrScannerDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_scan_qr))
                            }

                            OutlinedButton(
                                onClick = {
                                    prefilledInfo = null
                                    showAddDialog = true
                                },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(stringResource(R.string.btn_manual_ip))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(cameraList, key = { it.id }) { camera ->
                        CameraDeviceCard(
                            camera = camera,
                            isLivePreviewAll = isLivePreviewAllEnabled,
                            expiryInfo = devicesExpiryMap[camera.ipAddress],
                            isDisablingExpiry = isDisablingKeyExpiry,
                            onDisableKeyExpiry = { deviceId ->
                                viewModel.disableKeyExpiry(deviceId) { success, err ->
                                    if (success) {
                                        Toast.makeText(context, context.getString(R.string.msg_key_expiry_disabled_success), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, err ?: "Failed to disable key expiry", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onConnect = {
                                viewModel.selectAndConnect(camera)
                                onSelectCamera(camera)
                            },
                            onRemoteSettings = {
                                coroutineScope.launch {
                                    Toast.makeText(context, context.getString(R.string.viewer_toast_fetching_status, camera.name), Toast.LENGTH_SHORT).show()
                                    val status = viewModel.fetchCameraStatus(camera)
                                    remoteStatusJson = status
                                    remoteSettingsCamera = camera
                                }
                            },
                            onEdit = { editingCamera = camera },
                            onDelete = { viewModel.deleteCamera(camera) }
                        )
                    }
                }
            }
        }

        // QR Code Scanner Dialog Overlay
        if (showQrScannerDialog) {
            QRCodeScannerDialog(
                onDismiss = { showQrScannerDialog = false },
                onQrCodeScanned = { scanned ->
                    showQrScannerDialog = false
                    prefilledInfo = scanned
                    showAddDialog = true
                }
            )
        }

        // Add Dialog
        if (showAddDialog) {
            AddOrEditCameraDialog(
                title = if (prefilledInfo != null) stringResource(R.string.viewer_dialog_confirm_qr) else stringResource(R.string.btn_add_camera),
                initialName = prefilledInfo?.name ?: "",
                initialIp = prefilledInfo?.ipAddress ?: "100.",
                initialPort = prefilledInfo?.port?.toString() ?: "8080",
                onScanQrClick = {
                    showAddDialog = false
                    showQrScannerDialog = true
                },
                onDismiss = {
                    showAddDialog = false
                    prefilledInfo = null
                },
                onSave = { name, ip, port ->
                    viewModel.addCamera(name, ip, port)
                    showAddDialog = false
                    prefilledInfo = null
                    Toast.makeText(context, context.getString(R.string.camera_added_success, name), Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Dialog
        editingCamera?.let { camera ->
            AddOrEditCameraDialog(
                title = stringResource(R.string.btn_edit_camera),
                initialName = camera.name,
                initialIp = camera.ipAddress,
                initialPort = camera.port.toString(),
                onScanQrClick = {
                    editingCamera = null
                    showQrScannerDialog = true
                },
                onDismiss = { editingCamera = null },
                onSave = { name, ip, port ->
                    viewModel.updateCamera(camera.copy(name = name, ipAddress = ip, port = port))
                    editingCamera = null
                }
            )
        }

        if (isSpeedDialExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable { isSpeedDialExpanded = false }
            )
        }
    }
}
}

@Composable
fun CameraDeviceCard(
    camera: CameraDevice,
    isLivePreviewAll: Boolean,
    expiryInfo: io.github.iokkai.ocularnode.util.TailscaleDeviceExpiryInfo? = null,
    isDisablingExpiry: Boolean = false,
    onDisableKeyExpiry: (String) -> Unit = {},
    onConnect: () -> Unit,
    onRemoteSettings: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // If live preview is enabled, auto refresh snapshot every 1.5s to simulate live stream preview
    LaunchedEffect(isLivePreviewAll) {
        if (isLivePreviewAll) {
            while (true) {
                delay(1500)
                refreshKey = System.currentTimeMillis()
            }
        }
    }

    val snapshotUrl = "http://${camera.ipAddress}:${camera.port}/snapshot?t=$refreshKey"

    var currentBitmap by remember(camera.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(camera.id) { mutableStateOf(true) }

    var cpuUsage by remember(camera.id) { mutableIntStateOf(0) }
    var memoryUsage by remember(camera.id) { mutableIntStateOf(0) }
    var memoryUsedMB by remember(camera.id) { mutableIntStateOf(0) }
    var pingMs by remember(camera.id) { mutableIntStateOf(0) }
    var isOnlineStatus by remember(camera.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(camera.id, camera.ipAddress, camera.port) {
        var failCount = 0
        while (true) {
            val startTime = System.currentTimeMillis()
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val cleanIp = camera.ipAddress.trim()
                    val urlStr = "http://$cleanIp:${camera.port}/status"
                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.useCaches = false
                    conn.setRequestProperty("Connection", "close")
                    
                    val responseCode = conn.responseCode
                    val duration = (System.currentTimeMillis() - startTime).toInt().coerceAtLeast(1)
                    if (responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        val json = org.json.JSONObject(text)
                        
                        pingMs = duration
                        cpuUsage = json.optInt("cpuUsage", 0)
                        memoryUsage = json.optInt("memoryUsage", 0)
                        memoryUsedMB = json.optInt("memoryUsedMB", 0)
                        isOnlineStatus = true
                        failCount = 0
                    } else {
                        conn.disconnect()
                        failCount++
                    }
                }
            } catch (e: Exception) {
                failCount++
            }

            if (failCount >= 2) {
                isOnlineStatus = false
                pingMs = 999
            }

            delay(2000)
        }
    }

    LaunchedEffect(snapshotUrl) {
        val request = ImageRequest.Builder(context)
            .data(snapshotUrl)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = context.imageLoader.execute(request)
        if (result is coil.request.SuccessResult) {
            val drawable = result.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                currentBitmap = drawable.bitmap.asImageBitmap()
            }
        }
        isLoading = false
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Snapshot / Stream Preview Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(AppDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap!!,
                        contentDescription = "Camera Snapshot Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppPrimary, modifier = Modifier.size(24.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppDarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = AppTextDisabled, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.viewer_card_loading_snapshot), color = AppBorderSubtle, fontSize = 11.sp)
                        }
                    }
                }

                // Overlay Controls & Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LIVE / Snapshot Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLivePreviewAll) AppOverlaySuccess else AppOverlayDarkHeavy)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isLivePreviewAll) AppSuccessBright else AppBorderSubtle)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLivePreviewAll) stringResource(R.string.viewer_card_live_streaming) else stringResource(R.string.viewer_card_snapshot_preview),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Refresh Snapshot Manual Button
                    IconButton(
                        onClick = { refreshKey = System.currentTimeMillis() },
                        modifier = Modifier
                            .size(28.dp)
                            .background(AppOverlayDark, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Snapshot",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Center Live Touch Hint Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppOverlayPrimary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(stringResource(R.string.viewer_card_tap_to_monitor), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Info & Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val (statusColor, statusText) = when (isOnlineStatus) {
                        true -> AppSuccessBright to stringResource(R.string.device_online)
                        false -> AppError to stringResource(R.string.device_offline)
                        null -> AppTextMuted to stringResource(R.string.viewer_status_checking)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(camera.name, color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${camera.ipAddress}:${camera.port}", color = AppPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOnlineStatus == true) {
                            val isCpuHigh = cpuUsage > 80
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("CPU: $cpuUsage%", fontSize = 11.sp, color = if (isCpuHigh) AppError else AppTextSecondary, fontWeight = FontWeight.SemiBold)
                                if (isCpuHigh) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.viewer_cd_cpu_warning), tint = AppError, modifier = Modifier.size(14.dp))
                                }
                            }
                            val isMemHigh = memoryUsage > 85
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val memText = if (memoryUsedMB > 0) "RAM: $memoryUsage% (${memoryUsedMB}MB)" else "RAM: $memoryUsage%"
                                Text(memText, fontSize = 11.sp, color = if (isMemHigh) AppError else AppTextSecondary, fontWeight = FontWeight.SemiBold)
                                if (isMemHigh) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.viewer_cd_mem_warning), tint = AppError, modifier = Modifier.size(14.dp))
                                }
                            }
                            val isPingHigh = pingMs > 250
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Ping: ${pingMs}ms", fontSize = 11.sp, color = if (isPingHigh) AppError else AppSuccess, fontWeight = FontWeight.SemiBold)
                                if (isPingHigh) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.viewer_cd_ping_warning), tint = AppError, modifier = Modifier.size(14.dp))
                                }
                            }
                        } else if (isOnlineStatus == false) {
                            Text("CPU: --", fontSize = 11.sp, color = AppTextMuted)
                            Text("RAM: --", fontSize = 11.sp, color = AppTextMuted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.viewer_status_ping_offline), fontSize = 11.sp, color = AppError, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.viewer_cd_disconnected), tint = AppError, modifier = Modifier.size(14.dp))
                            }
                        } else {
                            Text("CPU: --", fontSize = 11.sp, color = AppTextMuted)
                            Text("RAM: --", fontSize = 11.sp, color = AppTextMuted)
                            Text("Ping: --", fontSize = 11.sp, color = AppTextMuted)
                        }
                    }

                    if (expiryInfo != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (expiryInfo.keyExpiryDisabled) {
                                Surface(
                                    color = AppSuccessContainerLight,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, AppSuccess.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = stringResource(R.string.msg_key_expiry_permanent),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppSuccessDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                val remainingDays = expiryInfo.getRemainingDays() ?: 0
                                Surface(
                                    color = AppWarningContainerLight,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, AppWarning.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = stringResource(R.string.msg_key_expiry_days_left, remainingDays),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppWarningDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                TextButton(
                                    onClick = { onDisableKeyExpiry(expiryInfo.deviceId) },
                                    enabled = !isDisablingExpiry,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Text(
                                        text = if (isDisablingExpiry) stringResource(R.string.btn_disabling_key_expiry) else stringResource(R.string.btn_disable_key_expiry),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onRemoteSettings, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Remote Settings", tint = AppPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AppTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppError, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AddOrEditCameraDialog(
    title: String,
    initialName: String,
    initialIp: String,
    initialPort: String,
    onScanQrClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var ipAddress by remember { mutableStateOf(initialIp) }
    var portStr by remember { mutableStateOf(initialPort) }

    fun processInputUrl(input: String) {
        val parsed = io.github.iokkai.ocularnode.util.QRCodeUtils.parseScannedQrCode(input)
        if (parsed != null) {
            if (name.isBlank() || name == "Camera Node" || name == "鏡頭裝置") name = parsed.name
            ipAddress = parsed.ipAddress
            portStr = parsed.port.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = AppTextPrimary, fontWeight = FontWeight.Bold) },
        containerColor = AppSurface,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.camera_name_label)) },
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
                    value = ipAddress,
                    onValueChange = {
                        ipAddress = it
                        if (it.startsWith("http://") || it.startsWith("https://") || it.contains(":")) {
                            processInputUrl(it)
                        }
                    },
                    label = { Text(stringResource(R.string.camera_ip_label)) },
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
                    value = portStr,
                    onValueChange = { portStr = it },
                    label = { Text(stringResource(R.string.camera_port_label)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onScanQrClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.viewer_btn_scan_qr_dialog), color = AppPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrBlank()) {
                                processInputUrl(clipText)
                                Toast.makeText(context, context.getString(R.string.viewer_toast_pasted, clipText), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.viewer_toast_clipboard_empty), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.viewer_btn_paste_clipboard), color = AppPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portStr.toIntOrNull() ?: 8080
                    onSave(name, ipAddress, port)
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White)
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = AppTextSecondary)
            }
        }
    )
}

