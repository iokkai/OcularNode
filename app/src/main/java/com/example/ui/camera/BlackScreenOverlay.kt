package com.example.ui.camera

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.zIndex

@Composable
fun BlackScreenOverlay(
    isBlackScreenActive: Boolean,
    batteryPct: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showHint by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf("") }

    // Update screen brightness on enter/exit (we apply this to the Activity window)
    DisposableEffect(isBlackScreenActive) {
        val activity = context as? Activity
        val originalLayoutParams = activity?.window?.attributes
        val originalBrightness = originalLayoutParams?.screenBrightness ?: -1f
        val originalCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) originalLayoutParams?.layoutInDisplayCutoutMode else null

        if (isBlackScreenActive && activity != null) {
            val params = activity.window.attributes
            params.screenBrightness = 0.01f // Minimum brightness 1%
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            activity.window.attributes = params
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            if (activity != null) {
                val params = activity.window.attributes
                params.screenBrightness = originalBrightness
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                    params.layoutInDisplayCutoutMode = originalCutoutMode
                }
                activity.window.attributes = params
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Clock update loop
    LaunchedEffect(isBlackScreenActive) {
        while (isBlackScreenActive) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(1000L)
        }
    }

    if (!isBlackScreenActive) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f) // Ensure it draws over everything in CameraModeScreen
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDismiss()
                    },
                    onTap = {
                        showHint = true
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(showHint) {
            if (showHint) {
                delay(3000L)
                showHint = false
            }
        }

        // Simple Digital Clock
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentTime,
                color = Color.White.copy(alpha = 0.3f), // Dim clock to prevent screen burn-in
                fontSize = 48.sp,
                fontWeight = FontWeight.Light
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Live",
                    tint = Color.Red.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LIVE • Battery $batteryPct%",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Power Saving Lock",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "省電模式運作中",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "連按兩下 (Double Tap) 解除省電模式",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
