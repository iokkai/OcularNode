package io.github.iokkai.ocularnode

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.service.CameraStreamService
import io.github.iokkai.ocularnode.ui.camera.CameraModeScreen
import io.github.iokkai.ocularnode.ui.camera.CameraServerViewModel
import io.github.iokkai.ocularnode.ui.events.EventLogsScreen
import io.github.iokkai.ocularnode.ui.events.EventLogsViewModel
import io.github.iokkai.ocularnode.ui.settings.SettingsScreen
import io.github.iokkai.ocularnode.ui.settings.SettingsViewModel
import io.github.iokkai.ocularnode.ui.theme.*
import io.github.iokkai.ocularnode.ui.viewer.LiveMonitorScreen
import io.github.iokkai.ocularnode.ui.viewer.ViewerListScreen
import io.github.iokkai.ocularnode.ui.viewer.ViewerViewModel
import io.github.iokkai.ocularnode.util.NetworkUtils
import io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager

enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    CAMERA(R.string.tab_camera, Icons.Default.Videocam),
    VIEWER(R.string.tab_viewer, Icons.Default.Visibility),
    ALERTS(R.string.tab_events, Icons.Default.Notifications),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val cameraServerViewModel: CameraServerViewModel by viewModels()
    private val viewerViewModel: ViewerViewModel by viewModels()
    private val eventLogsViewModel: EventLogsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureScreenAndLockBypass()

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    cameraServerViewModel = cameraServerViewModel,
                    viewerViewModel = viewerViewModel,
                    eventLogsViewModel = eventLogsViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ensureScreenAndLockBypass()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ensureScreenAndLockBypass()
    }

    private fun ensureScreenAndLockBypass() {
        val settingsManager = io.github.iokkai.ocularnode.data.SettingsManager.getInstance(this)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isDeviceOwner = dpm?.isDeviceOwnerApp(packageName) == true

        if (isDeviceOwner || settingsManager.deviceRoleMode == "CAMERA" || settingsManager.autoStartOnBoot) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                // Android 8.1+ (API 27+): 使用新版 API
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                // Android 8.0 及以下: 使用 WindowManager Flags
                @Suppress("DEPRECATION")
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD // Android 8.0 鎖屏解鎖
                )
            }
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Android 8.0+ (API 26+): 嘗試程式化解鎖鎖定畫面 (無密碼保護時生效)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                keyguardManager?.requestDismissKeyguard(this, null)
            }
        }
    }
}

@Composable
fun MainAppScreen(
    cameraServerViewModel: CameraServerViewModel,
    viewerViewModel: ViewerViewModel,
    eventLogsViewModel: EventLogsViewModel,
    settingsViewModel: SettingsViewModel
) {
    val roleMode by settingsViewModel.roleMode.collectAsState()
    val isBlackScreenActive by cameraServerViewModel.isBlackScreenActive.collectAsState()
    val context = LocalContext.current
    val dpm = remember(context) { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager }
    val isDeviceOwner = remember(context, dpm) { dpm?.isDeviceOwnerApp(context.packageName) == true }

    var currentTab by remember {
        mutableStateOf(
            if (isDeviceOwner || roleMode == "CAMERA") AppTab.CAMERA else AppTab.VIEWER
        )
    }
    var viewingMonitorDevice by remember { mutableStateOf<CameraDevice?>(null) }
    val unreadCount by eventLogsViewModel.unreadCount.collectAsState()
    val tailscaleProgress by ZeroTouchProvisionManager.tailscaleDownloadProgress.collectAsState()

    // Force CAMERA role & sync if Device Owner
    LaunchedEffect(isDeviceOwner) {
        if (isDeviceOwner) {
            if (roleMode != "CAMERA") {
                settingsViewModel.updateRoleMode("CAMERA")
            }
            if (currentTab == AppTab.VIEWER) {
                currentTab = AppTab.CAMERA
            }
            val activity = context as? MainActivity
            val settingsManager = SettingsManager.getInstance(context)
            
            // 自動賦予鏡頭與麥克風權限並啟用 NTP 自動校時 (僅限 Device Owner 權限)
            try {
                val adminComponent = ZeroTouchProvisionManager.getAdminComponent(context)
                dpm?.setPermissionGrantState(adminComponent, context.packageName, android.Manifest.permission.CAMERA, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                dpm?.setPermissionGrantState(adminComponent, context.packageName, android.Manifest.permission.RECORD_AUDIO, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                try {
                    dpm?.setPermissionGrantState(adminComponent, context.packageName, android.Manifest.permission.SYSTEM_ALERT_WINDOW, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                } catch (e: Exception) {}
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    dpm?.setAutoTimeEnabled(adminComponent, true)
                } else {
                    @Suppress("DEPRECATION")
                    dpm?.setAutoTimeRequired(adminComponent, true)
                }
                Log.i("MainActivity", "自動賦予相機與麥克風權限並啟用 NTP 自動校時成功")
            } catch (e: Exception) {
                Log.e("MainActivity", "自動配置 DO 權限或校時失敗", e)
            }

            // 自動啟動相機背景串流服務
            if (!cameraServerViewModel.isServiceRunning.value) {
                cameraServerViewModel.startStreamService()
                Log.i("MainActivity", "自動啟動相機串流服務")
            }

            // 確保 Tailscale 處於安裝與連線狀態
            val authKey = settingsManager.tailscaleAuthKey
            if (authKey.isNotBlank()) {
                if (NetworkUtils.isTailscaleInstalled(context)) {
                    ZeroTouchProvisionManager.injectTailscaleRestrictionsAndEnableVpn(context, authKey)
                } else {
                    ZeroTouchProvisionManager.startZeroTouchPipeline(context, authKey)
                }
            }

            if (settingsManager.isKioskModeActive && activity != null) {
                ZeroTouchProvisionManager.enableKioskMode(activity)
            }
        }
    }

    // Listen for notification click with OPEN_TELEGRAM_SETUP intent
    val activity = context as? MainActivity
    LaunchedEffect(activity?.intent) {
        if (activity?.intent?.getBooleanExtra("OPEN_TELEGRAM_SETUP", false) == true) {
            currentTab = AppTab.SETTINGS
            activity.intent?.removeExtra("OPEN_TELEGRAM_SETUP")
        }
    }
    LaunchedEffect(roleMode) {
        if (isDeviceOwner) {
            if (currentTab == AppTab.VIEWER) {
                currentTab = AppTab.CAMERA
            }
        } else if (roleMode == "CAMERA") {
            if (currentTab == AppTab.VIEWER) {
                currentTab = AppTab.CAMERA
            }
            if (!cameraServerViewModel.isServiceRunning.value) {
                cameraServerViewModel.startStreamService()
                Log.i("MainActivity", "自動啟動相機串流服務 (一般 CAMERA 模式)")
            }
        } else if (roleMode == "VIEWER") {
            if (currentTab == AppTab.CAMERA) {
                currentTab = AppTab.VIEWER
            }
            try {
                val serviceIntent = Intent(context, CameraStreamService::class.java)
                context.stopService(serviceIntent)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(1001)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error stopping service on VIEWER role sync", e)
            }
        }
    }

    val availableTabs = when {
        isDeviceOwner -> listOf(AppTab.CAMERA, AppTab.ALERTS, AppTab.SETTINGS)
        roleMode == "VIEWER" -> listOf(AppTab.VIEWER, AppTab.ALERTS, AppTab.SETTINGS)
        roleMode == "CAMERA" -> listOf(AppTab.CAMERA, AppTab.ALERTS, AppTab.SETTINGS)
        else -> listOf(AppTab.VIEWER, AppTab.CAMERA, AppTab.ALERTS, AppTab.SETTINGS)
    }

    // Onboarding role selection dialog on first run (only for non-Device Owner)
    if (roleMode == "UNSET" && !isDeviceOwner) {
        InitialRoleSelectionDialog(
            onSelectRole = { selectedRole ->
                settingsViewModel.updateRoleMode(selectedRole)
                currentTab = if (selectedRole == "VIEWER") AppTab.VIEWER else AppTab.CAMERA
            }
        )
    }

    val settingsManager = remember { io.github.iokkai.ocularnode.data.SettingsManager.getInstance(context) }
    var showTailscaleOnboardingDialog by remember {
        mutableStateOf(
            !isDeviceOwner &&
            roleMode != "UNSET" &&
            !io.github.iokkai.ocularnode.util.NetworkUtils.isTailscaleInstalled(context) &&
            !settingsManager.hasDismissedTailscaleOnboarding
        )
    }

    androidx.compose.runtime.LaunchedEffect(roleMode) {
        if (!isDeviceOwner && roleMode != "UNSET" && !io.github.iokkai.ocularnode.util.NetworkUtils.isTailscaleInstalled(context) && !settingsManager.hasDismissedTailscaleOnboarding) {
            showTailscaleOnboardingDialog = true
        }
    }

    if (showTailscaleOnboardingDialog) {
        io.github.iokkai.ocularnode.ui.common.TailscaleOnboardingDialog(
            onDismiss = {
                settingsManager.hasDismissedTailscaleOnboarding = true
                showTailscaleOnboardingDialog = false
            }
        )
    }

    // 零接觸部署 / Tailscale APK 下載進度對話框
    if (tailscaleProgress.isDownloading) {
        Dialog(
            onDismissRequest = { /* 下載核心套件時不允許點擊外部退出 */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppPrimary),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(AppSecondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.provisioning_in_progress),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AppTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = tailscaleProgress.status,
                        fontSize = 13.sp,
                        color = AppTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (tailscaleProgress.progressPercent >= 0) {
                        LinearProgressIndicator(
                            progress = { tailscaleProgress.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = AppPrimary,
                            trackColor = AppSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${tailscaleProgress.progressPercent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppPrimary
                            )
                            if (tailscaleProgress.totalBytes > 0) {
                                Text(
                                    text = "${tailscaleProgress.downloadedBytes / (1024 * 1024)} MB / ${tailscaleProgress.totalBytes / (1024 * 1024)} MB",
                                    fontSize = 11.sp,
                                    color = AppTextMuted
                                )
                            }
                        }
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = AppPrimary,
                            trackColor = AppSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (viewingMonitorDevice == null && !isBlackScreenActive) {
                NavigationBar(
                    containerColor = AppSurfaceVariant,
                    contentColor = AppTextPrimary
                ) {
                    availableTabs.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentTab != tab) {
                                    settingsViewModel.clearStatus()
                                }
                                currentTab = tab
                                if (tab == AppTab.ALERTS) {
                                    eventLogsViewModel.markAllAsRead()
                                }
                            },
                            icon = {
                                if (tab == AppTab.ALERTS && unreadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = AppError, contentColor = AppSurface) {
                                                Text(unreadCount.toString())
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = stringResource(tab.titleRes))
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = stringResource(tab.titleRes))
                                }
                            },
                            label = { Text(stringResource(tab.titleRes), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppOnSecondaryContainer,
                                selectedTextColor = AppOnSecondaryContainer,
                                unselectedIconColor = AppTextSecondary,
                                unselectedTextColor = AppTextSecondary,
                                indicatorColor = AppSecondaryContainer
                            )
                        )
                    }
                }
            }
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isBlackScreenActive) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
        ) {
            if (viewingMonitorDevice != null) {
                LiveMonitorScreen(
                    viewModel = viewerViewModel,
                    onBack = { viewingMonitorDevice = null }
                )
            } else {
                when (currentTab) {
                    AppTab.CAMERA -> CameraModeScreen(viewModel = cameraServerViewModel)
                    AppTab.VIEWER -> ViewerListScreen(
                        viewModel = viewerViewModel,
                        onSelectCamera = { device -> viewingMonitorDevice = device }
                    )
                    AppTab.ALERTS -> EventLogsScreen(viewModel = eventLogsViewModel)
                    AppTab.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
fun InitialRoleSelectionDialog(onSelectRole: (String) -> Unit) {
    Dialog(
        onDismissRequest = { /* force explicit selection */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AppSurface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AppSecondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.onboarding_welcome),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.onboarding_select_role_desc),
                    fontSize = 13.sp,
                    color = AppTextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Card 1: Camera Mode
                Card(
                    onClick = { onSelectRole("CAMERA") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                    border = BorderStroke(1.dp, AppSecondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(AppPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = AppSurface)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.role_camera_option), fontWeight = FontWeight.Bold, color = AppTextPrimary, fontSize = 15.sp)
                            Text(stringResource(R.string.role_camera_desc), color = AppTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Card 2: Viewer Mode
                Card(
                    onClick = { onSelectRole("VIEWER") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                    border = BorderStroke(1.dp, AppSecondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(AppPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = AppSurface)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.role_viewer_option), fontWeight = FontWeight.Bold, color = AppTextPrimary, fontSize = 15.sp)
                            Text(stringResource(R.string.role_viewer_desc), color = AppTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Privacy & Terms Consent Link (Option A)
                val context = LocalContext.current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_terms_consent_prefix),
                        fontSize = 11.sp,
                        color = AppTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iokkai/OcularNode/blob/main/PRIVACY.md")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_terms_link),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_terms_footnote),
                        fontSize = 10.sp,
                        color = AppTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
