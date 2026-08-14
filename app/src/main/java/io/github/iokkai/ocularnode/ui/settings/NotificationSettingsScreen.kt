package io.github.iokkai.ocularnode.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.data.SettingsDataStore
import io.github.iokkai.ocularnode.data.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore(context) }
    val settingsManager = remember { SettingsManager(context) }
    var mlKitFilterEnabled by remember { mutableStateOf(settingsManager.mlKitFilterEnabled) }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        containerColor = Color(0xFFFDF8FF),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFDF8FF),
                    titleContentColor = Color(0xFF1C1B1F),
                    navigationIconContentColor = Color(0xFF1C1B1F)
                ),
                title = { Text("智慧分類通知設定", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 提示：此頁面為本機鏡頭預設類別設定。若您在監控端管理多台鏡頭，可直接點擊該鏡頭的「遠端偏好設定」獨立設定每一台鏡頭的推播過濾與分類！",
                        fontSize = 12.sp,
                        color = Color(0xFF49454F),
                        lineHeight = 18.sp
                    )
                }
            }

            Text(
                text = "請選擇在動態偵測時，您希望收到哪些類別的推播通知：",
                fontSize = 13.sp,
                color = Color(0xFF49454F),
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                        Text("推播類別開關", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google ML Kit AI 物件過濾總開關", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                            Text("開啟後啟用本機 AI 分析物件類別與人臉", fontSize = 12.sp, color = Color(0xFF49454F))
                        }
                        Switch(
                            checked = mlKitFilterEnabled,
                            onCheckedChange = { checked ->
                                mlKitFilterEnabled = checked
                                settingsManager.mlKitFilterEnabled = checked
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1.2f))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("允許推播", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("觸發錄影", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }

                    NotificationCategory.values().forEach { category ->
                        val isEnabled by dataStore.getCategoryEnabled(category).collectAsState(initial = true)
                        val isRecordingEnabled by dataStore.getCategoryRecordingEnabled(category).collectAsState(initial = true)
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
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$iconStr ${category.displayName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1C1B1F),
                                modifier = Modifier.weight(1.2f)
                            )
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            dataStore.setCategoryEnabled(category, checked)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF6750A4)
                                    )
                                )
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Switch(
                                    checked = isRecordingEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            dataStore.setCategoryRecordingEnabled(category, checked)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF6750A4)
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "說明：系統會使用 ML 模型分析影像內容，當偵測到對應的類別且開關為開啟時，才會傳送通知。若未偵測到任何分類標籤，會依據「其他」類別的設定來決定。",
                fontSize = 12.sp,
                color = Color(0xFF49454F)
            )
        }
    }
}
