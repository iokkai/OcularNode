with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    content = f.read()

import re

# Update signature
old_sig = """fun RemoteSettingsScreen(
    cameraName: String,
    cameraStatusJson: JSONObject?,
    onSendCommand: (String, String) -> Unit,
    onSyncTelegram: () -> Unit,
    onNavigateBack: () -> Unit
) {"""

new_sig = """fun RemoteSettingsScreen(
    cameraName: String,
    cameraStatusJson: JSONObject?,
    onSendCommand: (String, String) -> Unit,
    onSyncTelegram: () -> Unit,
    onNavigateBack: () -> Unit,
    onFetchLogs: (suspend () -> List<String>)? = null
) {"""
content = content.replace(old_sig, new_sig)

# Add state
old_state = """    var showResPicker by remember { mutableStateOf(false) }"""
new_state = """    var showResPicker by remember { mutableStateOf(false) }
    var showRemoteLogs by remember { mutableStateOf(false) }
    var remoteLogsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()"""
content = content.replace(old_state, new_state)

# Button click
old_btn = """                                        onClick = { 
                                            // TODO: View remote logs... we could just open a new dialog or ignore for now,
                                            // The user mainly asked for "系統日誌紀錄開關" and "查看系統日誌 (Log) 按鈕". 
                                            // Viewing remote logs requires fetching from API, which might take more work, 
                                            // but at least the button is there. Wait, is there a /logs endpoint? No.
                                            // So we should add a toast saying "請至鏡頭端本機查看，或等待後續支援遠端日誌拉取" 
                                            Toast.makeText(context, "請至鏡頭端本機查看，目前版本尚未支援遠端提取日誌", Toast.LENGTH_SHORT).show()
                                        },"""
new_btn = """                                        onClick = { 
                                            if (onFetchLogs != null) {
                                                isLoadingLogs = true
                                                showRemoteLogs = true
                                                coroutineScope.kotlinx.coroutines.launch {
                                                    remoteLogsList = onFetchLogs()
                                                    isLoadingLogs = false
                                                }
                                            } else {
                                                Toast.makeText(context, "暫不支援遠端提取日誌", Toast.LENGTH_SHORT).show()
                                            }
                                        },"""
content = content.replace(old_btn, new_btn)

# Add Dialog at the end
old_end = """    }
}

@Composable
fun ResolutionSelectionDialog"""

new_end = """    }

    if (showRemoteLogs) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showRemoteLogs = false }) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.Text(
                        "遠端系統日誌", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.padding(16.dp)
                    )
                    if (isLoadingLogs) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = brandPrimaryColor)
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        ) {
                            items(remoteLogsList.size) { index ->
                                val log = remoteLogsList[index]
                                val color = when {
                                    log.contains(" E/") -> Color(0xFFFF5252)
                                    log.contains(" W/") -> Color(0xFFFFD740)
                                    log.contains(" I/") -> Color(0xFF69F0AE)
                                    else -> Color(0xFFE0E0E0)
                                }
                                androidx.compose.material3.Text(
                                    text = log,
                                    color = color,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                )
                                androidx.compose.material3.Divider(color = Color(0xFF333333), thickness = 0.5.dp)
                            }
                            if (remoteLogsList.isEmpty()) {
                                item {
                                    androidx.compose.material3.Text("暫無日誌", color = Color.Gray, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { showRemoteLogs = false },
                        modifier = Modifier.align(Alignment.End).padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandPrimaryColor)
                    ) {
                        Text("關閉")
                    }
                }
            }
        }
    }
}

@Composable
fun ResolutionSelectionDialog"""

content = content.replace(old_end, new_end)

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(content)
