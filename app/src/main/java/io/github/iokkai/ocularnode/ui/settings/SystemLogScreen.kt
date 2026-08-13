package io.github.iokkai.ocularnode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemLogScreen(
    onBack: () -> Unit
) {
    val logs by AppLogger.logs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系統日誌 (System Logs)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear Logs")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1E1E1E)),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(logs) { log ->
                val color = when {
                    log.contains(" E/") -> Color(0xFFFF5252)
                    log.contains(" W/") -> Color(0xFFFFD740)
                    log.contains(" I/") -> Color(0xFF69F0AE)
                    else -> Color(0xFFE0E0E0)
                }
                Text(
                    text = log,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
                Divider(color = Color(0xFF333333), thickness = 0.5.dp)
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "暫無日誌",
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
