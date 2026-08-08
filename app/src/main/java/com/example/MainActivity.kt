package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CameraDevice
import com.example.ui.camera.CameraModeScreen
import com.example.ui.camera.CameraServerViewModel
import com.example.ui.events.EventLogsScreen
import com.example.ui.events.EventLogsViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewer.LiveMonitorScreen
import com.example.ui.viewer.ViewerListScreen
import com.example.ui.viewer.ViewerViewModel

enum class AppTab(val title: String, val icon: ImageVector) {
    CAMERA("鏡頭端", Icons.Default.Videocam),
    VIEWER("觀看端", Icons.Default.Visibility),
    ALERTS("警報紀錄", Icons.Default.Notifications),
    SETTINGS("設定", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val cameraServerViewModel: CameraServerViewModel by viewModels()
    private val viewerViewModel: ViewerViewModel by viewModels()
    private val eventLogsViewModel: EventLogsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var currentTab by remember { mutableStateOf(if (roleMode == "VIEWER") AppTab.VIEWER else AppTab.CAMERA) }
    var viewingMonitorDevice by remember { mutableStateOf<CameraDevice?>(null) }
    val unreadCount by eventLogsViewModel.unreadCount.collectAsState()

    // Sync currentTab when roleMode changes
    LaunchedEffect(roleMode) {
        if (roleMode == "CAMERA" && currentTab == AppTab.VIEWER) {
            currentTab = AppTab.CAMERA
        } else if (roleMode == "VIEWER" && currentTab == AppTab.CAMERA) {
            currentTab = AppTab.VIEWER
        }
    }

    val availableTabs = when (roleMode) {
        "VIEWER" -> listOf(AppTab.VIEWER, AppTab.ALERTS, AppTab.SETTINGS)
        "CAMERA" -> listOf(AppTab.CAMERA, AppTab.ALERTS, AppTab.SETTINGS)
        else -> listOf(AppTab.CAMERA, AppTab.VIEWER, AppTab.ALERTS, AppTab.SETTINGS)
    }

    // Onboarding role selection dialog on first run
    if (roleMode == "UNSET") {
        InitialRoleSelectionDialog(
            onSelectRole = { selectedRole ->
                settingsViewModel.updateRoleMode(selectedRole)
                currentTab = if (selectedRole == "VIEWER") AppTab.VIEWER else AppTab.CAMERA
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (viewingMonitorDevice == null && !isBlackScreenActive) {
                NavigationBar(
                    containerColor = Color(0xFFF3EDF7),
                    contentColor = Color(0xFF1C1B1F)
                ) {
                    availableTabs.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                currentTab = tab
                                if (tab == AppTab.ALERTS) {
                                    eventLogsViewModel.markAllAsRead()
                                }
                            },
                            icon = {
                                if (tab == AppTab.ALERTS && unreadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = Color(0xFFB3261E), contentColor = Color.White) {
                                                Text(unreadCount.toString())
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.title)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            },
                            label = { Text(tab.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1D192B),
                                selectedTextColor = Color(0xFF1D192B),
                                unselectedIconColor = Color(0xFF49454F),
                                unselectedTextColor = Color(0xFF49454F),
                                indicatorColor = Color(0xFFE8DEF8)
                            )
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFFDF8FF)
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
            color = Color.White,
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
                        .background(Color(0xFFE8DEF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "歡迎使用 OcularNode",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "請選擇此裝置的主要用途：\n(後續可隨時於「設定」頁面進行修改)",
                    fontSize = 13.sp,
                    color = Color(0xFF49454F),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Card 1: Camera Mode
                Card(
                    onClick = { onSelectRole("CAMERA") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF6750A4), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("📷 鏡頭端 (攝影機)", fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F), fontSize = 15.sp)
                            Text("作為監控攝影機，錄影、串流與警報", color = Color(0xFF49454F), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Card 2: Viewer Mode
                Card(
                    onClick = { onSelectRole("VIEWER") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF6750A4), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("📺 觀看端 (監控螢幕)", fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F), fontSize = 15.sp)
                            Text("作為隨身螢幕，遠端查看鏡頭畫面", color = Color(0xFF49454F), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
