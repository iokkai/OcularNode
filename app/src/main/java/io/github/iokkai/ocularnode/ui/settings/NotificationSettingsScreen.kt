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
import androidx.compose.ui.res.stringResource
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
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.data.SettingsDataStore
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.ui.theme.*
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
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = AppTextPrimary,
                    navigationIconContentColor = AppTextPrimary
                ),
                title = { Text(stringResource(R.string.notif_settings_title), fontWeight = FontWeight.Bold) },
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
                colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.notif_settings_hint),
                        fontSize = 12.sp,
                        color = AppTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Text(
                text = stringResource(R.string.notif_settings_prompt),
                fontSize = 13.sp,
                color = AppTextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                        Text(stringResource(R.string.notif_settings_group_title), color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notif_settings_mlkit_master_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTextPrimary)
                            Text(stringResource(R.string.notif_settings_mlkit_master_desc), fontSize = 12.sp, color = AppTextSecondary)
                        }
                        Switch(
                            checked = mlKitFilterEnabled,
                            onCheckedChange = { checked ->
                                mlKitFilterEnabled = checked
                                settingsManager.mlKitFilterEnabled = checked
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppSurface,
                                checkedTrackColor = AppPrimary
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
                            Text(stringResource(R.string.notif_settings_allow_push), fontSize = 13.sp, color = AppTextMuted, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.notif_settings_trigger_record), fontSize = 13.sp, color = AppTextMuted, fontWeight = FontWeight.Bold)
                        }
                    }

                    NotificationCategory.entries.forEach { category ->
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
                                text = "$iconStr ${stringResource(category.titleRes)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTextPrimary,
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
                                        checkedThumbColor = AppSurface,
                                        checkedTrackColor = AppPrimary
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
                                        checkedThumbColor = AppSurface,
                                        checkedTrackColor = AppPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notif_settings_footer_desc),
                fontSize = 12.sp,
                color = AppTextSecondary
            )
        }
    }
}
