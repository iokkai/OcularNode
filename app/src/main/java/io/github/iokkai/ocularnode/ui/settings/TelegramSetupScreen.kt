package io.github.iokkai.ocularnode.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSetupScreen(
    onBack: () -> Unit = {},
    viewModel: TelegramSetupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var tokenInput by remember { mutableStateOf(viewModel.savedToken) }

    // Android 13+ (API 33) 通知權限請求 Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "未開啟通知權限，背景切換時將無法接收提示通知", Toast.LENGTH_LONG).show()
        }
    }

    // 自動於頁面載入時檢查通知權限
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Telegram 自動綁定",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1C1B1F)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color(0xFF1C1B1F))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFDF8FF))
            )
        },
        containerColor = Color(0xFFFDF8FF)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when (val state = uiState) {
                is TelegramSetupUiState.Step1_InputToken -> {
                    Step1InputTokenContent(
                        tokenInput = tokenInput,
                        onTokenChange = { tokenInput = it },
                        onNext = {
                            // 若需發送通知且尚未授權則觸發權限要求
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.startPairing(tokenInput)
                        }
                    )
                }
                is TelegramSetupUiState.Step2_Listening -> {
                    Step2ListeningContent(
                        pin = state.pin,
                        botName = state.botName,
                        botUsername = state.botUsername,
                        remainingSeconds = state.remainingSeconds,
                        isLoadingBotInfo = state.isLoadingBotInfo,
                        onCancel = { viewModel.resetToStep1() },
                        onCopyPin = {
                            clipboardManager.setText(AnnotatedString(state.pin))
                            Toast.makeText(context, "已複製配對碼 ${state.pin}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                is TelegramSetupUiState.Timeout -> {
                    TimeoutContent(
                        onRetry = { viewModel.retryPairing(tokenInput) },
                        onBackToStep1 = { viewModel.resetToStep1() }
                    )
                }
                is TelegramSetupUiState.Success -> {
                    SuccessContent(
                        chatId = state.chatId,
                        token = state.token,
                        onDone = onBack
                    )
                }
                is TelegramSetupUiState.Error -> {
                    ErrorContent(
                        errorMessage = state.message,
                        tokenInput = tokenInput,
                        onTokenChange = { tokenInput = it },
                        onRetry = { viewModel.startPairing(tokenInput) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step1InputTokenContent(
    tokenInput: String,
    onTokenChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFE8DEF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF6750A4))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "步驟 1：取得 Bot Token",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1C1B1F)
                    )
                    Text(
                        text = "建立您的專屬 Telegram 警報機器人",
                        fontSize = 12.sp,
                        color = Color(0xFF49454F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 教學步驟卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF3EDF7),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📖 3 步驟快速教學：",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF6750A4)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 在 Telegram 搜尋 @BotFather \n2. 發送 /newbot 建立機器人 \n3. 複製並在下方貼上您的 HTTP API Token",
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFF1C1B1F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = tokenInput,
                onValueChange = onTokenChange,
                label = { Text("HTTP API Token") },
                placeholder = { Text("例如：123456789:ABCdefGhIJKlmNoPQRstuVWX") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF6750A4)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6750A4),
                    unfocusedBorderColor = Color(0xFFCAC4D0),
                    focusedTextColor = Color(0xFF1C1B1F),
                    unfocusedTextColor = Color(0xFF1C1B1F)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNext,
                enabled = tokenInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "下一步 (產生配對碼)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun Step2ListeningContent(
    pin: String,
    botName: String,
    botUsername: String,
    remainingSeconds: Int,
    isLoadingBotInfo: Boolean,
    onCancel: () -> Unit,
    onCopyPin: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(pin) {
        onCopyPin()
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "步驟 2：請傳送配對碼給機器人",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF1C1B1F)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "請回到 Telegram，發送此數字給您的機器人",
                fontSize = 13.sp,
                color = Color(0xFF49454F),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoadingBotInfo) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0F7FF),
                    border = BorderStroke(1.dp, Color(0xFFD0E4FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF0061A4),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "載入機器人資訊中...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0061A4)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            } else if (botName.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0F7FF),
                    border = BorderStroke(1.dp, Color(0xFFD0E4FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (botUsername.isNotEmpty()) {
                                try {
                                    uriHandler.openUri("https://t.me/$botUsername")
                                } catch (e: Exception) {
                                    // Handle missing browser if needed
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFD0E4FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = botName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF0061A4)
                            )
                            if (botUsername.isNotEmpty()) {
                                Text(
                                    text = "@$botUsername",
                                    fontSize = 13.sp,
                                    color = Color(0xFF0061A4)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 超大字體 4 位數驗證碼卡片
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF3EDF7),
                border = BorderStroke(2.dp, Color(0xFF6750A4)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pin,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                        color = Color(0xFF6750A4)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = onCopyPin,
                        modifier = Modifier
                            .background(Color(0xFFE8DEF8), CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "複製驗證碼",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 輪詢進度條與剩餘秒數
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF6750A4),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "正等待訊息... 剩餘 $remainingSeconds 秒",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1C1B1F)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 操作指南區塊 1：個人接收
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8F5FF),
                border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFE8DEF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "個人接收",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF6750A4)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. 在 Telegram 搜尋您的機器人 ID\n2. 點擊「開始 (Start)」\n3. 發送上述 4 位數字",
                        fontSize = 13.sp,
                        color = Color(0xFF1C1B1F),
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作指南區塊 2：群組接收
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF0F7FF),
                border = BorderStroke(1.dp, Color(0xFFD0E4FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFD0E4FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👥", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "群組接收",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0061A4)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. 將您的機器人邀請至群組\n2. 在群組內發送上述 4 位數字",
                        fontSize = 13.sp,
                        color = Color(0xFF1C1B1F),
                        lineHeight = 22.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消配對", color = Color(0xFF49454F))
            }
        }
    }
}

@Composable
private fun TimeoutContent(
    onRetry: () -> Unit,
    onBackToStep1: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFFFB4AB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFFFDAD6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⏳", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "⏳ 配對逾時",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFBA1A1A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "未在 2 分鐘內收到驗證碼訊息。\n請確認已開啟 Telegram，並將 4 位數字傳送給您的機器人後再試一次。",
                fontSize = 13.sp,
                color = Color(0xFF49454F),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新配對", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onBackToStep1,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("修改 Token", color = Color(0xFF49454F))
            }
        }
    }
}

@Composable
private fun SuccessContent(
    chatId: String,
    token: String,
    onDone: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFA8E0B0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "綁定成功",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "綁定成功！",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "系統已自動擷取您的 Chat ID 並儲存設定，隨時準備接收警報與系統動態通知。",
                fontSize = 13.sp,
                color = Color(0xFF49454F),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF1F8E9),
                border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📋 綁定資訊：",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Chat ID: $chatId",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        text = "• Bot Status: 已連線測試完成",
                        fontSize = 12.sp,
                        color = Color(0xFF388E3C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("完成並返回", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    tokenInput: String,
    onTokenChange: (String) -> Unit,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFFFB4AB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "⚠️ 輸入錯誤",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFFBA1A1A)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage,
                fontSize = 13.sp,
                color = Color(0xFF49454F)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = tokenInput,
                onValueChange = onTokenChange,
                label = { Text("HTTP API Token") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6750A4),
                    unfocusedBorderColor = Color(0xFFCAC4D0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("重新試試", fontWeight = FontWeight.Bold)
            }
        }
    }
}
