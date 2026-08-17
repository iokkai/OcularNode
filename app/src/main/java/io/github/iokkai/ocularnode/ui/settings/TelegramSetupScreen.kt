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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.ui.theme.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.ui.theme.*

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
            Toast.makeText(context, context.getString(R.string.tg_setup_toast_notif_perm), Toast.LENGTH_LONG).show()
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
                        text = stringResource(R.string.tg_setup_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AppTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tg_setup_back), tint = AppTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
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
                            Toast.makeText(context, context.getString(R.string.tg_setup_toast_pin_copied, state.pin), Toast.LENGTH_SHORT).show()
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
    var isTokenVisible by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppSecondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(AppSecondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = AppPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.tg_setup_step1_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.tg_setup_step1_subtitle),
                        fontSize = 12.sp,
                        color = AppTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 教學步驟卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.tg_setup_guide_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tg_setup_guide_content),
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = AppTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = tokenInput,
                onValueChange = onTokenChange,
                label = { Text(stringResource(R.string.tg_setup_token_label)) },
                placeholder = { Text(stringResource(R.string.tg_setup_token_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = AppPrimary) },
                trailingIcon = {
                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                        Icon(
                            imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isTokenVisible) "Hide Token" else "Show Token",
                            tint = AppTextSecondary
                        )
                    }
                },
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPrimary,
                    unfocusedBorderColor = AppBorder,
                    focusedTextColor = AppTextPrimary,
                    unfocusedTextColor = AppTextPrimary
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNext,
                enabled = tokenInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPrimary,
                    contentColor = AppSurface
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(R.string.tg_setup_btn_next),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.tg_setup_privacy_hint),
                fontSize = 11.sp,
                color = AppTextMuted,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppSecondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.tg_setup_step2_title),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = AppTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.tg_setup_step2_desc),
                fontSize = 13.sp,
                color = AppTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoadingBotInfo) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppInfoContainerLight,
                    border = BorderStroke(1.dp, AppInfoContainer),
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
                            color = AppInfo,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.tg_setup_loading_bot),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppInfo
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            } else if (botName.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppInfoContainerLight,
                    border = BorderStroke(1.dp, AppInfoContainer),
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
                                .background(AppInfoContainer, CircleShape),
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
                                color = AppInfo
                            )
                            if (botUsername.isNotEmpty()) {
                                Text(
                                    text = "@$botUsername",
                                    fontSize = 13.sp,
                                    color = AppInfo
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
                color = AppSurfaceVariant,
                border = BorderStroke(2.dp, AppPrimary),
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
                        color = AppPrimary
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = onCopyPin,
                        modifier = Modifier
                            .background(AppSecondaryContainer, CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.tg_setup_copy_pin),
                            tint = AppPrimary,
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
                    color = AppPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tg_setup_waiting_msg, remainingSeconds),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 操作指南區塊 1：個人接收
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppSurfaceCardAlt,
                border = BorderStroke(1.dp, AppSecondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(AppSecondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tg_setup_personal_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.tg_setup_personal_guide),
                        fontSize = 13.sp,
                        color = AppTextPrimary,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作指南區塊 2：群組接收
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppInfoContainerLight,
                border = BorderStroke(1.dp, AppInfoContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(AppInfoContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👥", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tg_setup_group_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppInfo
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.tg_setup_group_guide),
                        fontSize = 13.sp,
                        color = AppTextPrimary,
                        lineHeight = 22.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tg_setup_btn_cancel), color = AppTextSecondary)
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
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppErrorBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(AppErrorContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⏳", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.tg_setup_timeout_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppError
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.tg_setup_timeout_desc),
                fontSize = 13.sp,
                color = AppTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = AppSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tg_setup_btn_retry), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onBackToStep1,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tg_setup_btn_modify_token), color = AppTextSecondary)
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
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppSuccessBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(AppSuccessContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.tg_setup_success_title),
                    tint = AppSuccess,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.tg_setup_success_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppSuccess
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.tg_setup_success_desc),
                fontSize = 13.sp,
                color = AppTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppSuccessContainer,
                border = BorderStroke(1.dp, AppSuccessBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.tg_setup_info_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AppSuccess
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Chat ID: $chatId",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = AppSuccessDark
                    )
                    Text(
                        text = stringResource(R.string.tg_setup_bot_status),
                        fontSize = 12.sp,
                        color = AppSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = AppSuccess, contentColor = AppSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.tg_setup_btn_done), fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
    var isTokenVisible by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppErrorBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.tg_setup_error_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = AppError
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage,
                fontSize = 13.sp,
                color = AppTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = tokenInput,
                onValueChange = onTokenChange,
                label = { Text(stringResource(R.string.tg_setup_token_label)) },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = AppPrimary) },
                trailingIcon = {
                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                        Icon(
                            imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isTokenVisible) "Hide Token" else "Show Token",
                            tint = AppTextSecondary
                        )
                    }
                },
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPrimary,
                    unfocusedBorderColor = AppBorder,
                    focusedTextColor = AppTextPrimary,
                    unfocusedTextColor = AppTextPrimary
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = AppSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tg_setup_btn_retry_simple), fontWeight = FontWeight.Bold)
            }
        }
    }
}
