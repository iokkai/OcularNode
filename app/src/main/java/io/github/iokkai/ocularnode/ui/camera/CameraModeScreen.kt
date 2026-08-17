package io.github.iokkai.ocularnode.ui.camera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.asImageBitmap
import io.github.iokkai.ocularnode.util.QRCodeUtils
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.ui.theme.*

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
            Toast.makeText(context, context.getString(R.string.permission_required_desc), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(hasCameraPermission) {
        viewModel.refreshNetworkInfo()
        if (hasCameraPermission && !isServiceRunning) {
            viewModel.startStreamService()
        }
    }

    // Auto enter power-saving mode (black screen) after 1 minute in camera mode
    LaunchedEffect(isServiceRunning, isBlackScreenActive) {
        if (isServiceRunning && !isBlackScreenActive) {
            kotlinx.coroutines.delay(60_000L)
            viewModel.toggleBlackScreen(true)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
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
                    tint = AppPrimary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.permission_required_title),
                    color = AppTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_required_desc),
                    color = AppTextSecondary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(stringResource(R.string.btn_grant_permissions))
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
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    border = BorderStroke(1.dp, AppBorder),
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
                                        .background(if (isServiceRunning) AppSuccess else AppError)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isServiceRunning) stringResource(R.string.camera_status_streaming) else stringResource(R.string.camera_status_stopped),
                                            color = AppTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isAddressSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isAddressSectionExpanded) "Collapse" else "Expand",
                                            tint = AppPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (isServiceRunning) {
                                        viewModel.stopStreamService()
                                        isAddressSectionExpanded = false
                                    } else {
                                        viewModel.startStreamService()
                                        isAddressSectionExpanded = true
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isServiceRunning) AppError else AppPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isServiceRunning) stringResource(R.string.camera_btn_stop) else stringResource(R.string.camera_btn_start))
                            }
                        }

                        AnimatedVisibility(visible = isThermalThrottled) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(AppWarningContainer)
                                        .border(1.dp, AppWarningBright, RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔥", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            stringResource(R.string.thermal_throttling_title, batteryTemp),
                                            fontWeight = FontWeight.Bold,
                                            color = AppWarning,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            stringResource(R.string.thermal_throttling_desc),
                                            color = AppWarningBright,
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
                                val camName = viewModel.settingsManager.cameraDeviceName.ifBlank { context.getString(R.string.role_camera_title) }
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
                                            .background(AppSuccessContainer)
                                            .border(1.dp, AppSuccessBorder, RoundedCornerShape(16.dp))
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
                                                        .background(AppSuccess)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.camera_tailscale_connected), color = AppSuccessDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }

                                            TextButton(
                                                onClick = { viewModel.refreshNetworkInfo() },
                                                colors = ButtonDefaults.textButtonColors(contentColor = AppSuccessDark)
                                            ) {
                                                Text(stringResource(R.string.btn_refresh), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                                tint = AppSuccessDark
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(stringResource(R.string.camera_tailscale_ip), color = AppSuccess, fontSize = 11.sp)
                                                Text("http://${activeTailscale ?: activeLocal}:${viewModel.settingsManager.serverPort}", color = AppSuccessDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                            IconButton(onClick = {
                                                copyToClipboard(context, "http://${activeTailscale ?: activeLocal}:${viewModel.settingsManager.serverPort}")
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = AppSuccessDark)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.tailscale_key_expiry_hint),
                                            color = AppSuccessDark.copy(alpha = 0.85f),
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            modifier = Modifier.clickable {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://login.tailscale.com/admin/machines"))
                                                context.startActivity(intent)
                                            }
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AppErrorContainerLight)
                                            .border(1.dp, AppErrorBorder, RoundedCornerShape(16.dp))
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
                                                        .background(AppErrorBright)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.camera_tailscale_disconnected), color = AppErrorDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }

                                            val isTailscaleInstalled = remember(context) { io.github.iokkai.ocularnode.util.NetworkUtils.isTailscaleInstalled(context) }
                                            OutlinedButton(
                                                onClick = { io.github.iokkai.ocularnode.util.NetworkUtils.openTailscaleApp(context) },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, AppErrorBorder),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppErrorDark),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(if (isTailscaleInstalled) "🚀" else "📥", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Wifi, contentDescription = null, tint = AppWarning)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(stringResource(R.string.camera_local_ip), color = AppTextSecondary, fontSize = 11.sp)
                                                Text("http://$activeLocal:${viewModel.settingsManager.serverPort}", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                            IconButton(onClick = {
                                                copyToClipboard(context, "http://$activeLocal:${viewModel.settingsManager.serverPort}")
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = AppTextSecondary)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // QR Code Card for Viewer Joining & Web Streaming
                                val qrBitmap = remember(streamUrl) {
                                    QRCodeUtils.generateQRCodeBitmap(streamUrl, 360)
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = AppSurfaceSubtle),
                                    border = BorderStroke(1.dp, AppSecondaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = AppPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.btn_scan_qr), fontWeight = FontWeight.Bold, color = AppTextPrimary, fontSize = 14.sp)
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
                                                    Icon(Icons.Default.Computer, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(stringResource(R.string.qr_web_monitor_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTextPrimary)
                                                }
                                                Text(stringResource(R.string.qr_web_monitor_desc), fontSize = 10.sp, color = AppTextSecondary)

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Text(stringResource(R.string.qr_quick_add_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTextPrimary)
                                                Text(stringResource(R.string.qr_quick_add_desc), fontSize = 10.sp, color = AppTextSecondary)
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
                    border = BorderStroke(1.dp, AppBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                                Text(stringResource(R.string.camera_status_stopped), color = AppTextDisabled)
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
                                modifier = Modifier.background(AppOverlayDark, CircleShape)
                            ) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.toggleTorch() },
                                modifier = Modifier.background(AppOverlayDark, CircleShape)
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
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimaryContainer, contentColor = AppOnPrimaryContainer)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AppOnPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.camera_black_screen), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Controls Panel
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    border = BorderStroke(1.dp, AppBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.camera_quality_mode_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Operating Mode Selection (監看模式 vs 自動偵測模式)
                        val operatingMode by viewModel.operatingMode.collectAsState()
                        Column {
                            Text(stringResource(R.string.operating_mode_title), color = AppTextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
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
                                        containerColor = if (isMonitor) AppPrimary else AppSecondaryContainer,
                                        contentColor = if (isMonitor) Color.White else AppOnSecondaryContainer
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.mode_monitor), fontSize = 12.sp, fontWeight = if (isMonitor) FontWeight.Bold else FontWeight.Normal)
                                }
                                Button(
                                    onClick = { viewModel.setOperatingMode("detection") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isMonitor) AppPrimary else AppSecondaryContainer,
                                        contentColor = if (!isMonitor) Color.White else AppOnSecondaryContainer
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.mode_motion_detect), fontSize = 12.sp, fontWeight = if (!isMonitor) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (operatingMode == "monitor")
                                    stringResource(R.string.mode_monitor_desc)
                                else
                                    stringResource(R.string.mode_motion_detect_desc),
                                fontSize = 11.sp,
                                color = AppTextMuted
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
                                Text(stringResource(R.string.resolution_setting_title), color = AppTextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(stringResource(R.string.resolution_setting_desc), color = AppTextMuted, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { showResolutionDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, AppPrimary)
                            ) {
                                Text("📹 $currentResolution ▾", color = AppPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                    Text(stringResource(R.string.jpeg_quality_title), color = AppTextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(stringResource(R.string.jpeg_quality_desc), color = AppTextMuted, fontSize = 11.sp)
                                }
                                Text("${currentQuality}%", color = AppPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Slider(
                                value = currentQuality.toFloat(),
                                onValueChange = { viewModel.setQuality(it.toInt()) },
                                valueRange = 30f..90f,
                                steps = 5,
                                colors = SliderDefaults.colors(
                                    thumbColor = AppPrimary,
                                    activeTrackColor = AppPrimary,
                                    inactiveTrackColor = AppSecondaryContainer
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
                                    Text(stringResource(R.string.ai_filter_title), color = AppTextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(stringResource(R.string.ai_filter_desc), color = AppTextMuted, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = isMlKitFilterEnabled,
                                    onCheckedChange = { viewModel.toggleMlKitFilter(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AppPrimary,
                                        uncheckedThumbColor = AppTextSecondary,
                                        uncheckedTrackColor = AppSecondaryContainer
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
    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
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
        "1080p" to "1440x1080",
        "960p" to "1280x960",
        "720p" to "960x720",
        "480p" to "640x480",
        "360p" to "480x360"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.resolution_setting_title), color = AppTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.resolution_setting_desc), fontSize = 13.sp, color = AppTextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { (resKey, desc) ->
                    val isSelected = currentResolution.equals(resKey, ignoreCase = true)
                    Card(
                        onClick = { onSelect(resKey) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AppSecondaryContainer else AppSurfaceVariant
                        ),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) AppPrimary else AppBorder),
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
                                colors = RadioButtonDefaults.colors(selectedColor = AppPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(resKey, fontWeight = FontWeight.Bold, color = AppTextPrimary, fontSize = 15.sp)
                                Text(desc, fontSize = 11.sp, color = AppTextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close), color = AppPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
