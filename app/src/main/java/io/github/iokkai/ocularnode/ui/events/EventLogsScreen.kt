package io.github.iokkai.ocularnode.ui.events

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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.ui.theme.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.github.iokkai.ocularnode.util.MediaSaveUtils
import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.data.MotionEvent
import java.io.File
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
            "PET" -> events.filter {
                it.aiSummary.contains("Pet", ignoreCase = true) ||
                it.aiSummary.contains("Cat", ignoreCase = true) ||
                it.aiSummary.contains("Dog", ignoreCase = true) ||
                it.aiSummary.contains("Animal", ignoreCase = true) ||
                it.aiSummary.contains("寵物") ||
                it.aiSummary.contains("動物")
            }
            else -> events
        }
    }

    val context = LocalContext.current

    val isCameraRole = viewModel.settingsManager.deviceRoleMode == "CAMERA"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
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
                        text = stringResource(R.string.events_title),
                        color = AppTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (viewModel.settingsManager.autoStorageCleanupEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppInfoContainerLight)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "♻️ ${if (viewModel.settingsManager.storageLimitGB < 1.0f) "500MB" else "${viewModel.settingsManager.storageLimitGB.toInt()}GB"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppInfoBright
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.events_subtitle),
                    color = AppTextSecondary,
                    fontSize = 12.sp
                )
            }

            if (isCameraRole || events.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (events.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.events_empty), Toast.LENGTH_SHORT).show()
                        } else {
                            showClearConfirmDialog = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.events_btn_clear_all),
                        tint = if (events.isNotEmpty()) AppError else AppTextDisabled
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Remote Camera Sync Bar (Only shown in Viewer mode when remote cameras exist)
        if (!isCameraRole && cameraDevices.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.events_sync_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTextPrimary)
                        }

                        Button(
                            onClick = {
                                val targetCam = selectedCameraForSync ?: cameraDevices.firstOrNull()
                                if (targetCam != null) {
                                    viewModel.syncRemoteEventsFromCamera(targetCam)
                                }
                            },
                            enabled = !isSyncingRemote,
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isSyncingRemote) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.events_syncing), fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.events_btn_sync), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                    selectedContainerColor = AppSecondaryContainer,
                                    selectedLabelColor = AppOnSecondaryContainer
                                )
                            )
                        }
                    }

                    AnimatedVisibility(visible = syncMessage != null) {
                        syncMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(msg, fontSize = 11.sp, color = if (msg.contains("❌")) AppError else AppSuccess, fontWeight = FontWeight.Medium)
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
                    label = { Text("${stringResource(R.string.events_filter_all)} (${events.size})", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "PET",
                    onClick = { selectedFilter = "PET" },
                    label = { Text("🐶 ${stringResource(R.string.events_filter_pet)}", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "ALERT",
                    onClick = { selectedFilter = "ALERT" },
                    label = { Text("🚨 ${stringResource(R.string.events_filter_motion)}", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "AI_FILTER",
                    onClick = { selectedFilter = "AI_FILTER" },
                    label = { Text(stringResource(R.string.events_filter_ai), fontSize = 12.sp) }
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
                        tint = AppTextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.events_empty), color = AppTextSecondary, fontSize = 15.sp)
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
            title = { Text(stringResource(R.string.events_clear_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.events_clear_dialog_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllEvents()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppError)
                ) {
                    Text(stringResource(R.string.events_clear_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
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
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppBorderLight),
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
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AppTextDisabled)
                }

                if (hasVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppOverlayDarkHeavy)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = AppErrorBorder, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(stringResource(R.string.events_badge_video), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.events_motion_change, event.motionPercentage),
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(event.cameraName, color = AppPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(formattedTime, color = AppTextSecondary, fontSize = 11.sp)

                if (event.aiSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (event.aiFiltered) AppSecondaryContainer else AppPrimaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.aiSummary,
                            fontSize = 11.sp,
                            color = if (event.aiFiltered) AppOnSecondaryContainer else AppOnPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (event.telegramSentSuccess) AppSuccess else if (event.aiFiltered) AppTextMuted else AppTextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (event.aiFiltered) stringResource(R.string.events_status_filtered) else if (event.telegramSentSuccess) stringResource(R.string.events_status_telegram) else stringResource(R.string.events_status_failed),
                        color = if (event.aiFiltered) AppPrimary else if (event.telegramSentSuccess) AppSuccess else AppTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val coroutineScope = rememberCoroutineScope()

            // Download Snapshot / Video Button
            if (thumbnailBitmap != null || hasVideo) {
                IconButton(onClick = {
                    coroutineScope.launch {
                        try {
                            if (hasVideo) {
                                val videoFile = File(event.videoPath!!)
                                val result = MediaSaveUtils.saveVideoToGallery(
                                    context = context,
                                    sourceVideoFile = videoFile,
                                    baseFileName = "OcularNode_Video_${event.id}_${event.timestamp}"
                                )
                                if (result.isSuccess) {
                                    Toast.makeText(context, context.getString(R.string.events_toast_video_saved), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.events_toast_save_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            } else if (thumbnailBitmap != null) {
                                val result = MediaSaveUtils.saveImageToGallery(
                                    context = context,
                                    bitmap = thumbnailBitmap,
                                    baseFileName = "OcularNode_Event_${event.id}_${event.timestamp}"
                                )
                                if (result.isSuccess) {
                                    Toast.makeText(context, context.getString(R.string.events_toast_photo_saved), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.events_toast_save_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.events_toast_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (hasVideo) Icons.Default.Videocam else Icons.Default.Download,
                        contentDescription = "Download File",
                        tint = AppPrimary
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppTextMuted, modifier = Modifier.size(20.dp))
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
                        text = if (showVideoMode) stringResource(R.string.events_preview_video_title) else stringResource(R.string.events_preview_photo_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                if (showVideoMode && hasVideo) {
                                    val videoFile = File(event.videoPath!!)
                                    val result = MediaSaveUtils.saveVideoToGallery(
                                        context = context,
                                        sourceVideoFile = videoFile,
                                        baseFileName = "OcularNode_Video_${event.id}_${event.timestamp}"
                                    )
                                    if (result.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.events_toast_video_saved), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.events_toast_save_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_SHORT).show()
                                    }
                                } else if (bitmap != null) {
                                    val result = MediaSaveUtils.saveImageToGallery(
                                        context = context,
                                        bitmap = bitmap,
                                        baseFileName = "OcularNode_Event_${event.id}_${event.timestamp}"
                                    )
                                    if (result.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.events_toast_photo_saved), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.events_toast_save_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.events_toast_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
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
                            .background(AppDarkSurface)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!showVideoMode) Purple80 else Color.Transparent)
                                .clickable { showVideoMode = false }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.events_tab_snapshot),
                                color = if (!showVideoMode) AppOnPrimaryContainer else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (showVideoMode) Purple80 else Color.Transparent)
                                .clickable { showVideoMode = true }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.events_tab_video),
                                color = if (showVideoMode) AppOnPrimaryContainer else Color.White,
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
                        .background(AppTextPrimary),
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
                        Text(stringResource(R.string.events_media_load_failed), color = AppTextDisabled)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Details Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppDarkSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.events_source_cam, event.cameraName), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.events_time_point, formattedTime), color = AppBorder, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.events_motion_ratio, event.motionPercentage), color = AppSecondaryContainer, fontSize = 13.sp)

                        if (event.snapshotPath != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("📁 ${event.snapshotPath}", color = AppTextDisabled, fontSize = 10.sp)
                        }
                        if (event.videoPath != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("🎬 ${event.videoPath}", color = Purple80, fontSize = 10.sp)
                        }

                        if (event.aiSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("🤖 AI: ${event.aiSummary}", color = Purple80, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (hasVideo) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val videoFile = File(event.videoPath!!)
                                            val result = MediaSaveUtils.saveVideoToGallery(
                                                context = context,
                                                sourceVideoFile = videoFile,
                                                baseFileName = "OcularNode_Video_${event.id}_${event.timestamp}"
                                            )
                                            if (result.isSuccess) {
                                                Toast.makeText(context, context.getString(R.string.events_toast_video_saved), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.events_toast_save_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.events_toast_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80, contentColor = AppOnPrimaryContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.events_btn_download_video), fontWeight = FontWeight.Bold)
                            }
                        } else if (bitmap != null) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val result = MediaSaveUtils.saveImageToGallery(
                                                context = context,
                                                bitmap = bitmap,
                                                baseFileName = "OcularNode_Event_${event.id}_${event.timestamp}"
                                            )
                                            if (result.isSuccess) {
                                                Toast.makeText(context, context.getString(R.string.events_toast_photo_saved), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.events_toast_save_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.events_toast_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80, contentColor = AppOnPrimaryContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.events_btn_download_photo), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

