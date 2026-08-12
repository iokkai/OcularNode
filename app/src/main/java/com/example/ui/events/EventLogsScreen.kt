package com.example.ui.events

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CameraDevice
import com.example.data.MotionEvent
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventLogsScreen(viewModel: EventLogsViewModel) {
    val events by viewModel.motionEvents.collectAsState()
    val cameraDevices by viewModel.cameraDevices.collectAsState()
    val isSyncingRemote by viewModel.isSyncingRemote.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, ALERT, AI_FILTER, PET
    var selectedPreviewEvent by remember { mutableStateOf<MotionEvent?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedCameraForSync by remember { mutableStateOf<CameraDevice?>(null) }

    val filteredEvents = remember(events, selectedFilter) {
        when (selectedFilter) {
            "ALERT" -> events.filter { !it.aiFiltered }
            "AI_FILTER" -> events.filter { it.aiFiltered }
            "PET" -> events.filter { it.aiSummary.contains("寵物") || it.aiSummary.contains("Cat") || it.aiSummary.contains("Dog") }
            else -> events
        }
    }

    val context = LocalContext.current

    val isCameraRole = viewModel.settingsManager.deviceRoleMode == "CAMERA"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDF8FF))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "動態偵測紀錄",
                        color = Color(0xFF1C1B1F),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (viewModel.settingsManager.autoStorageCleanupEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2FE))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "♻️ 上限 ${if (viewModel.settingsManager.storageLimitGB < 1.0f) "500MB" else "${viewModel.settingsManager.storageLimitGB.toInt()}GB"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }
                }
                Text(
                    text = "列出偵測到的快照與時間點，提供即時預覽與下載",
                    color = Color(0xFF49454F),
                    fontSize = 12.sp
                )
            }

            if (isCameraRole || events.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (events.isEmpty()) {
                            Toast.makeText(context, "目前尚無任何紀錄", Toast.LENGTH_SHORT).show()
                        } else {
                            showClearConfirmDialog = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "全部刪除",
                        tint = if (events.isNotEmpty()) Color(0xFFB3261E) else Color(0xFF9E9E9E)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Remote Camera Sync Bar (Only shown in Viewer mode when remote cameras exist)
        if (!isCameraRole && cameraDevices.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("從鏡頭端同步紀錄:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                        }

                        Button(
                            onClick = {
                                val targetCam = selectedCameraForSync ?: cameraDevices.firstOrNull()
                                if (targetCam != null) {
                                    viewModel.syncRemoteEventsFromCamera(targetCam)
                                }
                            },
                            enabled = !isSyncingRemote,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isSyncingRemote) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("同步中...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("同步歷史紀錄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cameraDevices) { cam ->
                            val isSelected = (selectedCameraForSync?.id == cam.id) || (selectedCameraForSync == null && cam == cameraDevices.firstOrNull())
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCameraForSync = cam },
                                label = { Text(cam.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE8DEF8),
                                    selectedLabelColor = Color(0xFF1D192B)
                                )
                            )
                        }
                    }

                    AnimatedVisibility(visible = syncMessage != null) {
                        syncMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(msg, fontSize = 11.sp, color = if (msg.contains("❌")) Color(0xFFB3261E) else Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("全部 (${events.size})", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "PET",
                    onClick = { selectedFilter = "PET" },
                    label = { Text("🐶 寵物偵測", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "ALERT",
                    onClick = { selectedFilter = "ALERT" },
                    label = { Text("🚨 通知類別", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "AI_FILTER",
                    onClick = { selectedFilter = "AI_FILTER" },
                    label = { Text("已過濾", fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main List
        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFF79747E),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("無符合條件的動態偵測紀錄", color = Color(0xFF49454F), fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredEvents, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        onImageClick = { selectedPreviewEvent = event },
                        onDelete = { viewModel.deleteEvent(event) }
                    )
                }
            }
        }
    }

    // Full-Screen Snapshot Inspection & Download Dialog
    selectedPreviewEvent?.let { event ->
        SnapshotPreviewDialog(
            event = event,
            onDismiss = { selectedPreviewEvent = null }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("確定要清空所有紀錄？", fontWeight = FontWeight.Bold) },
            text = { Text("此動作將會刪除本頁面上所有的動態偵測快照與事件紀錄。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllEvents()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) {
                    Text("確定清空")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun EventCard(
    event: MotionEvent,
    onImageClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
    val hasVideo = !event.videoPath.isNullOrEmpty() && File(event.videoPath).exists()

    val thumbnailBitmap = remember(event.thumbnailBase64, event.snapshotPath) {
        var bmp: Bitmap? = null
        if (!event.snapshotPath.isNullOrEmpty()) {
            try {
                bmp = BitmapFactory.decodeFile(event.snapshotPath)
            } catch (_: Exception) {}
        }
        if (bmp == null && event.thumbnailBase64 != null) {
            try {
                val bytes = Base64.decode(event.thumbnailBase64, Base64.DEFAULT)
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } catch (_: Exception) {}
        }
        bmp
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE7E0EC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Snapshot Thumbnail with click preview & video badge
            Box(
                modifier = Modifier
                    .size(96.dp, 72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Event Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray)
                }

                if (hasVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("錄影", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "畫面變化${"%.1f".format(event.motionPercentage)}%",
                        color = Color(0xFF1C1B1F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(event.cameraName, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(formattedTime, color = Color(0xFF49454F), fontSize = 11.sp)

                if (event.aiSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (event.aiFiltered) Color(0xFFE8DEF8) else Color(0xFFFFD8E4))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.aiSummary,
                            fontSize = 11.sp,
                            color = if (event.aiFiltered) Color(0xFF1D192B) else Color(0xFF31111D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = if (event.telegramSentSuccess) Color(0xFF2E7D32) else if (event.aiFiltered) Color(0xFF79747E) else Color(0xFF49454F),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (event.aiFiltered) "AI 攔截" else if (event.telegramSentSuccess) "Telegram 已發送" else "未連線/失敗",
                        color = if (event.aiFiltered) Color(0xFF6750A4) else if (event.telegramSentSuccess) Color(0xFF2E7D32) else Color(0xFF49454F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Download Snapshot / Video Button
            if (thumbnailBitmap != null || hasVideo) {
                IconButton(onClick = {
                    try {
                        if (hasVideo) {
                            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                            val videoFile = File(event.videoPath!!)
                            val targetFile = File(moviesDir, "PetMonitor_Video_${event.id}_${event.timestamp}.mp4")
                            videoFile.copyTo(targetFile, overwrite = true)
                            Toast.makeText(context, "📥 錄影檔 (.mp4) 已下載至影片庫！", Toast.LENGTH_SHORT).show()
                        } else if (thumbnailBitmap != null) {
                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val file = File(picturesDir, "PetMonitor_Event_${event.id}_${event.timestamp}.jpg")
                            val fos = FileOutputStream(file)
                            thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                            fos.flush()
                            fos.close()
                            Toast.makeText(context, "📥 照片已下載並儲存至相簿！", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "儲存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = if (hasVideo) Icons.Default.Videocam else Icons.Default.Download,
                        contentDescription = "Download File",
                        tint = Color(0xFF6750A4)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF79747E), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SnapshotPreviewDialog(
    event: MotionEvent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formattedTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
    val hasVideo = !event.videoPath.isNullOrEmpty() && File(event.videoPath).exists()
    var showVideoMode by remember { mutableStateOf(hasVideo) }

    val bitmap = remember(event.thumbnailBase64, event.snapshotPath) {
        var bmp: Bitmap? = null
        if (!event.snapshotPath.isNullOrEmpty()) {
            try {
                bmp = BitmapFactory.decodeFile(event.snapshotPath)
            } catch (_: Exception) {}
        }
        if (bmp == null && event.thumbnailBase64 != null) {
            try {
                val bytes = Base64.decode(event.thumbnailBase64, Base64.DEFAULT)
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } catch (_: Exception) {}
        }
        bmp
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    Text(
                        text = if (showVideoMode) "🎥 事件動態錄影 (.mp4)" else "📷 動態偵測快照細節",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    IconButton(onClick = {
                        try {
                            if (showVideoMode && hasVideo) {
                                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                                val videoFile = File(event.videoPath!!)
                                val targetFile = File(moviesDir, "PetMonitor_Video_${event.id}_${event.timestamp}.mp4")
                                videoFile.copyTo(targetFile, overwrite = true)
                                Toast.makeText(context, "📥 錄影檔 (.mp4) 已儲存至影片庫！", Toast.LENGTH_SHORT).show()
                            } else if (bitmap != null) {
                                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                val file = File(picturesDir, "PetMonitor_Event_${event.id}_${event.timestamp}.jpg")
                                val fos = FileOutputStream(file)
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                                fos.flush()
                                fos.close()
                                Toast.makeText(context, "📥 快照圖片已儲存至相簿！", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "下載失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle Bar (if video is available)
                if (hasVideo) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2B2930))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!showVideoMode) Color(0xFFD0BCFF) else Color.Transparent)
                                .clickable { showVideoMode = false }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "📷 瞬間快照",
                                color = if (!showVideoMode) Color(0xFF381E72) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (showVideoMode) Color(0xFFD0BCFF) else Color.Transparent)
                                .clickable { showVideoMode = true }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "🎥 動態錄影",
                                color = if (showVideoMode) Color(0xFF381E72) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Media Display Box (Image or VideoView)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1B1F)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showVideoMode && hasVideo) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    setVideoPath(event.videoPath)
                                    val mediaController = android.widget.MediaController(ctx)
                                    mediaController.setAnchorView(this)
                                    setMediaController(mediaController)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Full Snapshot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("無法載入媒體檔案", color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Details Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📍 鏡頭來源: ${event.cameraName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🕒 時間點: $formattedTime", color = Color(0xFFCAC4D0), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("📊 動態異動比例: ${"%.1f".format(event.motionPercentage)}%", color = Color(0xFFE8DEF8), fontSize = 13.sp)

                        if (event.snapshotPath != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("📁 快照檔案: ${event.snapshotPath}", color = Color.Gray, fontSize = 10.sp)
                        }
                        if (event.videoPath != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("🎬 錄影檔案: ${event.videoPath}", color = Color(0xFFD0BCFF), fontSize = 10.sp)
                        }

                        if (event.aiSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("🤖 AI 偵測分析: ${event.aiSummary}", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (hasVideo) {
                            Button(
                                onClick = {
                                    try {
                                        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                                        val targetFile = File(moviesDir, "PetMonitor_Video_${event.id}_${event.timestamp}.mp4")
                                        File(event.videoPath!!).copyTo(targetFile, overwrite = true)
                                        Toast.makeText(context, "📥 動態錄影 (.mp4) 已下載至影片庫！", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "儲存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("下載並儲存完整動態錄影檔 (.mp4)", fontWeight = FontWeight.Bold)
                            }
                        } else if (bitmap != null) {
                            Button(
                                onClick = {
                                    try {
                                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                        val file = File(picturesDir, "PetMonitor_Event_${event.id}_${event.timestamp}.jpg")
                                        val fos = FileOutputStream(file)
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                                        fos.flush()
                                        fos.close()
                                        Toast.makeText(context, "📥 快照影像已下載並儲存至系統相簿！", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "儲存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("下載與儲存快照影像至相簿", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

