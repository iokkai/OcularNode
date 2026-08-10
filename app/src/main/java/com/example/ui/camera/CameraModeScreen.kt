package com.example.ui.camera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.asImageBitmap
import com.example.util.QRCodeUtils
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CameraModeScreen(viewModel: CameraServerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isBlackScreenActive by viewModel.isBlackScreenActive.collectAsState()
    val currentResolution by viewModel.currentResolution.collectAsState()
    val currentQuality by viewModel.currentQuality.collectAsState()
    val isMotionEnabled by viewModel.isMotionEnabled.collectAsState()
    val isMlKitFilterEnabled by viewModel.isMlKitFilterEnabled.collectAsState()
    val tailscaleIp by viewModel.tailscaleIp.collectAsState()
    val localIp by viewModel.localIp.collectAsState()

    val isTailscaleConnected by viewModel.isTailscaleConnected.collectAsState()
    val isVpnActive by viewModel.isVpnActive.collectAsState()

    val isThermalThrottled by viewModel.isThermalThrottled.collectAsState()
    val batteryTemp by viewModel.batteryTemp.collectAsState()

    var showResolutionDialog by remember { mutableStateOf(false) }
    var isAddressSectionExpanded by remember { mutableStateOf(true) }

    if (showResolutionDialog) {
        ResolutionSelectionDialog(
            currentResolution = currentResolution,
            onSelect = { selectedRes ->
                viewModel.setResolution(selectedRes)
                showResolutionDialog = false
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        hasCameraPermission = cameraGranted && audioGranted
        if (hasCameraPermission) {
            viewModel.startStreamService()
        } else {
            Toast.makeText(context, "需要相機與麥克風權限以進行監控串流", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkInfo()
    }

    // Auto enter power-saving mode (black screen) after 1 minute in camera mode
    LaunchedEffect(isServiceRunning, isBlackScreenActive) {
        if (isServiceRunning && !isBlackScreenActive) {
            kotlinx.coroutines.delay(60_000L)
            viewModel.toggleBlackScreen(true)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFDF8FF))) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Permission Needed",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "啟動鏡頭端需要權限",
                    color = Color(0xFF1C1B1F),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "請授權相機與麥克風權限，以建立 MJPEG 影像與雙向語音串流伺服器。",
                    color = Color(0xFF49454F),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("授權並啟動鏡頭端")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isAddressSectionExpanded = !isAddressSectionExpanded }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFB3261E))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isServiceRunning) "鏡頭端運作中 (LIVE)" else "串流服務已停止",
                                            color = Color(0xFF1C1B1F),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isAddressSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isAddressSectionExpanded) "縮小連線資訊" else "展開連線資訊",
                                            tint = Color(0xFF6750A4),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isAddressSectionExpanded) "點擊此區可縮小網址與 QR Code" else "點擊此區可展開網址與 QR Code",
                                        fontSize = 11.sp,
                                        color = Color(0xFF79747E)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (isServiceRunning) {
                                        viewModel.stopStreamService()
                                        isAddressSectionExpanded = true
                                    } else {
                                        viewModel.startStreamService()
                                        isAddressSectionExpanded = false
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isServiceRunning) Color(0xFFB3261E) else Color(0xFF6750A4),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isServiceRunning) "停止串流" else "開啟串流")
                            }
                        }

                        AnimatedVisibility(visible = isThermalThrottled) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFFFF3E0))
                                        .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔥", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "高溫降載機制啟動中 (${String.format(java.util.Locale.US, "%.1f", batteryTemp)}°C)",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100),
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "為避免手機過熱當機與電池膨脹，已自動暫停 ML Kit AI 分析與錄影，基礎串流與推播正常運作中。",
                                            color = Color(0xFFF57C00),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = isAddressSectionExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))

                                // Tailscale IP Banner
                                val activeTailscale = tailscaleIp
                                val activeLocal = localIp ?: "127.0.0.1"
                                val camName = viewModel.settingsManager.cameraDeviceName.ifBlank { "寵物鏡頭" }
                                val encodedCamName = try { java.net.URLEncoder.encode(camName, "UTF-8") } catch (e: Exception) { camName }
                                val streamUrl = if (!activeTailscale.isNull_or_blank_custom()) {
                                    "http://$activeTailscale:${viewModel.settingsManager.serverPort}?name=$encodedCamName"
                                } else {
                                    "http://$activeLocal:${viewModel.settingsManager.serverPort}?name=$encodedCamName"
                                }

                                if (isTailscaleConnected) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFE8F5E9))
                                            .border(1.dp, Color(0xFF81C784), RoundedCornerShape(16.dp))
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF2E7D32))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Tailscale VPN 已連線", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }

                                            TextButton(
                                                onClick = { viewModel.refreshNetworkInfo() },
                                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1B5E20))
                                            ) {
                                                Text("🔄 重新整理", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Wifi,
                                                contentDescription = null,
                                                tint = Color(0xFF1B5E20)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(" Tailscale IP (可遠端穿透 4G/5G)", color = Color(0xFF2E7D32), fontSize = 11.sp)
                                                Text("http://${activeTailscale ?: activeLocal}:${viewModel.settingsManager.serverPort}", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                            IconButton(onClick = {
                                                copyToClipboard(context, "http://${activeTailscale ?: activeLocal}:${viewModel.settingsManager.serverPort}")
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = Color(0xFF1B5E20))
                                            }
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFFFEBEE))
                                            .border(1.dp, Color(0xFFE57373), RoundedCornerShape(16.dp))
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFD32F2F))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Tailscale VPN 未連線 (僅使用區域網)", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }

                                            val isTailscaleInstalled = remember(context) { com.example.util.NetworkUtils.isTailscaleInstalled(context) }
                                            OutlinedButton(
                                                onClick = { com.example.util.NetworkUtils.openTailscaleApp(context) },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color(0xFFE57373)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(if (isTailscaleInstalled) "🚀 開啟 Tailscale" else "📥 安裝 Tailscale", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFFE65100))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("區域網內 IP (僅限相同 Wi-Fi 使用)", color = Color(0xFF5D4037), fontSize = 11.sp)
                                                Text("http://$activeLocal:${viewModel.settingsManager.serverPort}", color = Color(0xFF3E2723), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                            IconButton(onClick = {
                                                copyToClipboard(context, "http://$activeLocal:${viewModel.settingsManager.serverPort}")
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = Color(0xFF5D4037))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "💡 提示：開啟 Tailscale VPN 後，系統將自動偵測並切換至 100.x.x.x IP，即可隨時隨地遠端觀看。",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8D6E63)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // QR Code Card for Viewer Joining & Web Streaming
                                val qrBitmap = remember(streamUrl) {
                                    QRCodeUtils.generateQRCodeBitmap(streamUrl, 360)
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                                    border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color(0xFF6750A4))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("掃描 QR Code 快速加入 / 網頁串流", fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F), fontSize = 14.sp)
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (qrBitmap != null) {
                                                Image(
                                                    bitmap = qrBitmap.asImageBitmap(),
                                                    contentDescription = "Connection QR Code",
                                                    modifier = Modifier
                                                        .size(130.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color.White)
                                                        .padding(6.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF6750A4), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("網頁監控", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1C1B1F))
                                                }
                                                Text("開啟瀏覽器輸入此網址即可免安裝 APP 觀看。", fontSize = 11.sp, color = Color(0xFF49454F))

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Text("📱 監控端 APP 加入", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1C1B1F))
                                                Text("使用觀看端手機相機掃描 QR Code 貼上 URL 即可。 ", fontSize = 11.sp, color = Color(0xFF49454F))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }

                // Camera Preview Box
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val cameraService = viewModel.getCameraService()
                        if (cameraService != null) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                    cameraService.cameraHelper.attachPreviewSurface(
                                        lifecycleOwner = lifecycleOwner,
                                        previewSurface = previewView.surfaceProvider
                                    )
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("串流伺服器未開啟", color = Color.Gray)
                            }
                        }

                        // Overlay buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleCameraLens() },
                                modifier = Modifier.background(Color(0xAA000000), CircleShape)
                            ) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.toggleTorch() },
                                modifier = Modifier.background(Color(0xAA000000), CircleShape)
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = "Torch", tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Power Saving Black Screen Button
                Button(
                    onClick = { viewModel.toggleBlackScreen(true) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEADDFF), contentColor = Color(0xFF21005D))
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF21005D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("進入省電模式 (持續背景運作)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Controls Panel
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("畫質與遠端控制設定", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Operating Mode Selection (監看模式 vs 自動偵測模式)
                        val operatingMode by viewModel.operatingMode.collectAsState()
                        Column {
                            Text("運作模式設定", color = Color(0xFF49454F), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isMonitor = operatingMode == "monitor"
                                Button(
                                    onClick = { viewModel.setOperatingMode("monitor") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMonitor) Color(0xFF6750A4) else Color(0xFFE8DEF8),
                                        contentColor = if (isMonitor) Color.White else Color(0xFF1D192B)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("👁️ 監看模式", fontSize = 12.sp, fontWeight = if (isMonitor) FontWeight.Bold else FontWeight.Normal)
                                }
                                Button(
                                    onClick = { viewModel.setOperatingMode("detection") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isMonitor) Color(0xFF6750A4) else Color(0xFFE8DEF8),
                                        contentColor = if (!isMonitor) Color.White else Color(0xFF1D192B)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🚨 動態偵測", fontSize = 12.sp, fontWeight = if (!isMonitor) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (operatingMode == "monitor")
                                    "監看模式：中斷連線時關閉燈光與暫停警報，有連線時自動喚醒。"
                                else
                                    "動態偵測模式：持續進行智慧動態偵測與警報留存。",
                                fontSize = 11.sp,
                                color = Color(0xFF79747E)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Resolution Selector Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("解析度設定", color = Color(0xFF49454F), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("拍攝與串流畫面解析度", color = Color(0xFF79747E), fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { showResolutionDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF6750A4))
                            ) {
                                Text("📹 $currentResolution ▾", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // JPEG Compression Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("JPEG 壓縮品質", color = Color(0xFF49454F), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("數值越高畫質越清晰，但流量也越大，建議設定為 50%", color = Color(0xFF79747E), fontSize = 11.sp)
                                }
                                Text("${currentQuality}%", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Slider(
                                value = currentQuality.toFloat(),
                                onValueChange = { viewModel.setQuality(it.toInt()) },
                                valueRange = 30f..90f,
                                steps = 5,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF6750A4),
                                    activeTrackColor = Color(0xFF6750A4),
                                    inactiveTrackColor = Color(0xFFE8DEF8)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (operatingMode == "detection") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🤖 Google ML Kit AI 防洗版:", color = Color(0xFF49454F), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("純人類 (主人在家) 自動過濾推播，寵物正常發送警報", color = Color(0xFF79747E), fontSize = 11.sp)
                                }
                                Switch(
                                    checked = isMlKitFilterEnabled,
                                    onCheckedChange = { viewModel.toggleMlKitFilter(it) },
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
                }
            }

            // Power-Saving Overlay
            BlackScreenOverlay(
                isBlackScreenActive = isBlackScreenActive,
                batteryPct = getBatteryPercentage(context),
                onDismiss = { viewModel.toggleBlackScreen(false) }
            )
        }
    }
}

private fun String?.isNull_or_blank_custom(): Boolean {
    return this == null || this.trim().isEmpty()
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OcularNode URL", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "已複製串流網址: $text", Toast.LENGTH_SHORT).show()
}

private fun getBatteryPercentage(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
    return bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}

@Composable
fun ResolutionSelectionDialog(
    currentResolution: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "1080p" to "1920x1080 • 超高畫質 (最清晰)",
        "720p" to "1280x720 • 高畫質 (推薦預設)",
        "480p" to "854x480 • 標準畫質 (順暢省流)",
        "360p" to "640x360 • 流暢 (低延遲)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇畫面解析度", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("點選下方解析度項目以變更解析度：", fontSize = 13.sp, color = Color(0xFF49454F))
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { (resKey, desc) ->
                    val isSelected = currentResolution.equals(resKey, ignoreCase = true)
                    Card(
                        onClick = { onSelect(resKey) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFE8DEF8) else Color(0xFFF3EDF7)
                        ),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFF6750A4) else Color(0xFFCAC4D0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(resKey) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6750A4))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(resKey, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F), fontSize = 15.sp)
                                Text(desc, fontSize = 11.sp, color = Color(0xFF49454F))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
            }
        }
    )
}
