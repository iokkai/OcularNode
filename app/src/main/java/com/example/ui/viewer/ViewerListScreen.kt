package com.example.ui.viewer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CameraDevice
import com.example.util.ScannedCameraInfo

import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import coil.imageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
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
    val isTailscaleActive = isTailscaleConnected || isVpnActive

    var showAddDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }

    var prefilledInfo by remember { mutableStateOf<ScannedCameraInfo?>(null) }
    var editingCamera by remember { mutableStateOf<CameraDevice?>(null) }
    var remoteSettingsCamera by remember { mutableStateOf<CameraDevice?>(null) }
    var remoteStatusJson by remember { mutableStateOf<JSONObject?>(null) }

    val settingsManager = remember { com.example.data.SettingsManager(context) }
    val isLivePreviewAllEnabled = settingsManager.livePreviewInListEnabled

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkInfo()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    prefilledInfo = null
                    showAddDialog = true
                },
                containerColor = Color(0xFF6750A4),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增鏡頭")
            }
        },
        containerColor = Color(0xFFFDF8FF)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "觀看端 - 鏡頭列表",
                        color = Color(0xFF1C1B1F),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "透過掃描鏡頭端 QR Code 或輸入 Tailscale/區域網 IP 加入",
                        color = Color(0xFF49454F),
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
                        .background(Color(0xFFE8F5E9))
                        .border(1.dp, Color(0xFF81C784), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Tailscale VPN 已連線",
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (!tailscaleIp.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "($tailscaleIp)",
                                        color = Color(0xFF2E7D32),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Text(
                                text = "VPN 服務連線中 • 可跨網段遠端監控",
                                color = Color(0xFF388E3C),
                                fontSize = 10.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = { com.example.util.NetworkUtils.openTailscaleApp(context) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("🚀 開啟 App", color = Color(0xFF1B5E20), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFEBEE))
                        .border(1.dp, Color(0xFFE57373), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD32F2F))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Tailscale VPN 未連線",
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "VPN 服務未開啟 • 僅限同 Wi-Fi 區域網觀看",
                                color = Color(0xFFC62828),
                                fontSize = 10.sp
                            )
                        }
                    }

                    val isTailscaleInstalled = remember(context) { com.example.util.NetworkUtils.isTailscaleInstalled(context) }
                    TextButton(
                        onClick = { com.example.util.NetworkUtils.openTailscaleApp(context) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(if (isTailscaleInstalled) "🚀 開啟 Tailscale" else "📥 安裝 Tailscale", color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            tint = Color(0xFF79747E),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("尚未新增任何鏡頭裝置", color = Color(0xFF1C1B1F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("請掃描鏡頭端的 QR Code 或手動輸入 IP 位址", color = Color(0xFF49454F), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showQrScannerDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("掃描 QR Code 加入")
                            }

                            OutlinedButton(
                                onClick = {
                                    prefilledInfo = null
                                    showAddDialog = true
                                },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("手動輸入 IP")
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
                            onConnect = {
                                viewModel.selectAndConnect(camera)
                                onSelectCamera(camera)
                            },
                            onRemoteSettings = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "正在連線取得 ${camera.name} 狀態...", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "📷 成功讀取 QR Code！請確認資訊後儲存", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Add Dialog
        if (showAddDialog) {
            AddOrEditCameraDialog(
                title = if (prefilledInfo != null) "確認 QR Code 掃描資訊" else "新增鏡頭裝置",
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
                    Toast.makeText(context, "✅ 已成功加入鏡頭: $name", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Dialog
        editingCamera?.let { camera ->
            AddOrEditCameraDialog(
                title = "編輯鏡頭裝置",
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
        // Remote Camera Settings Dialog
        remoteSettingsCamera?.let { camera ->
            RemoteSettingsDialog(
                cameraName = camera.name,
                cameraStatusJson = remoteStatusJson,
                onSendCommand = { cmd, valStr ->
                    viewModel.sendControlCommandToCamera(camera, cmd, valStr)
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
                onDismiss = {
                    remoteSettingsCamera = null
                    remoteStatusJson = null
                }
            )
        }
    }
}

@Composable
fun CameraDeviceCard(
    camera: CameraDevice,
    isLivePreviewAll: Boolean,
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
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
                    .background(Color(0xFF1E1B2E)),
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
                        CircularProgressIndicator(color = Color(0xFF6750A4), modifier = Modifier.size(24.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2D2A3E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("快照載入中 / 點擊進入即時畫面", color = Color.LightGray, fontSize = 11.sp)
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
                            .background(if (isLivePreviewAll) Color(0xCC2E7D32) else Color(0xCC000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isLivePreviewAll) Color.Green else Color.LightGray)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLivePreviewAll) "LIVE 串流中" else "📸 快照預覽",
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
                            .background(Color(0xAA000000), CircleShape)
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
                        .background(Color(0xCC6750A4))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("進入主畫面監控", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                    Text(camera.name, color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${camera.ipAddress}:${camera.port}", color = Color(0xFF6750A4), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onRemoteSettings, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Remote Settings", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF49454F), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFB3261E), modifier = Modifier.size(18.dp))
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
        val parsed = com.example.util.QRCodeUtils.parseScannedQrCode(input)
        if (parsed != null) {
            if (name.isBlank() || name == "鏡頭裝置") name = parsed.name
            ipAddress = parsed.ipAddress
            portStr = parsed.port.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold) },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("鏡頭名稱 (如 客廳鏡頭)") },
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
                    value = ipAddress,
                    onValueChange = {
                        ipAddress = it
                        if (it.startsWith("http://") || it.startsWith("https://") || it.contains(":")) {
                            processInputUrl(it)
                        }
                    },
                    label = { Text("IP 或 完整 URL (如 100.x.x.x)") },
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
                    value = portStr,
                    onValueChange = { portStr = it },
                    label = { Text("Port 通訊埠 (預設 8080)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1C1B1F),
                        unfocusedTextColor = Color(0xFF1C1B1F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
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
                        Text("相機掃描 QR", color = Color(0xFF6750A4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrBlank()) {
                                processInputUrl(clipText)
                                Toast.makeText(context, "已貼上並解析連結: $clipText", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "剪貼簿無內容", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("📋 貼上剪貼簿", color = Color(0xFF6750A4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White)
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF49454F))
            }
        }
    )
}

