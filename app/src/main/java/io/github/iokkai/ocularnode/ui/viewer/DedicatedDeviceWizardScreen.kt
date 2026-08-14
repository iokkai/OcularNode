package io.github.iokkai.ocularnode.ui.viewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.iokkai.ocularnode.R

/**
 * 觀看端專用設備 (Device Owner) 部署引導精靈
 * 支援 4 個步驟導引、Tailscale API Key 加密儲存與動態 Auth Key 原生 DO QR Code 渲染
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedicatedDeviceWizardScreen(
    onBack: () -> Unit = {},
    viewModel: WizardViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val filled = viewModel.autoFillCurrentWifi(context)
            if (!filled) {
                Toast.makeText(context, context.getString(R.string.wizard_wifi_autofill_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.wizard_wifi_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    val onAutoFillWifiRequested: () -> Unit = {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            val filled = viewModel.autoFillCurrentWifi(context)
            if (!filled) {
                Toast.makeText(context, context.getString(R.string.wizard_wifi_autofill_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.wizard_wifi_permission_prompt), Toast.LENGTH_SHORT).show()
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSavedApiKey(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.currentStep) {
                            1 -> stringResource(R.string.wizard_nav_title_1)
                            2 -> stringResource(R.string.wizard_nav_title_2)
                            3 -> stringResource(R.string.wizard_nav_title_3)
                            4 -> stringResource(R.string.wizard_nav_title_4)
                            else -> stringResource(R.string.wizard_nav_title_default)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.currentStep > 1) {
                                viewModel.goToStep(uiState.currentStep - 1)
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wizard_btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 頂部視覺化進度指示器 (Stepper)
            WizardStepper(currentStep = uiState.currentStep)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "step_transition"
                ) { step ->
                    when (step) {
                        1 -> StepWarningScreen(
                            onNext = { viewModel.goToStep(2) }
                        )

                        2 -> StepResetScreen(
                            onNext = {
                                onAutoFillWifiRequested()
                                viewModel.goToStep(3)
                            }
                        )

                        3 -> StepNetworkAndApiKeyScreen(
                            uiState = uiState,
                            onSsidChange = { viewModel.setWifiSsid(it) },
                            onPasswordChange = { viewModel.setWifiPassword(it) },
                            onAutoFillWifi = onAutoFillWifiRequested,
                            onApiKeyChange = { viewModel.setApiKey(it) },
                            onVerifyApiKey = { viewModel.verifyApiKey(context) },
                            onNext = {
                                viewModel.goToStep(4)
                                viewModel.generateProvisioningQrCode(context)
                            }
                        )

                        4 -> StepScanScreen(
                            uiState = uiState,
                            onRetryGenQrCode = {
                                viewModel.generateProvisioningQrCode(context)
                            },
                            onFinish = onBack
                        )
                    }
                }
            }
        }
    }
}

/**
 * 頂部視覺化 Stepper 進度指示器
 */
@Composable
private fun WizardStepper(currentStep: Int) {
    val steps = listOf(
        stringResource(R.string.wizard_step_prep),
        stringResource(R.string.wizard_step_reset),
        stringResource(R.string.wizard_step_config),
        stringResource(R.string.wizard_step_provision)
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            steps.forEachIndexed { index, title ->
                val stepNum = index + 1
                val isCompleted = stepNum < currentStep
                val isCurrent = stepNum == currentStep

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(
                                color = when {
                                    isCompleted -> Color(0xFF2E7D32)
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = stepNum.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = title.substringAfter(". "),
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(1.dp)
                            .background(
                                if (stepNum < currentStep) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

/**
 * 畫面一：價值傳遞與心理建設 (Step 1: Warning & Value)
 */
@Composable
fun StepWarningScreen(
    onNext: () -> Unit
) {
    var isConfirmed by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 標題與說明
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.wizard_s1_header),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.wizard_s1_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }

        // 警告卡片 (Card，警告色 ContainerColor)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.wizard_s1_warn_icon),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.wizard_s1_warn_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.wizard_s1_warn_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // 防呆勾選框 (Checkbox)
        Card(
            onClick = { isConfirmed = !isConfirmed },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isConfirmed,
                    onCheckedChange = { isConfirmed = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.wizard_s1_chk_erase),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 按鈕
        Button(
            onClick = onNext,
            enabled = isConfirmed,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_s1_btn_next),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 畫面二：手把手重置教學 (Step 2: Reset Instructions)
 */
@Composable
fun StepResetScreen(
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    val resetSteps = listOf(
        stringResource(R.string.wizard_s2_step1),
        stringResource(R.string.wizard_s2_step2),
        stringResource(R.string.wizard_s2_step3),
        stringResource(R.string.wizard_s2_step4)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 標題
        Text(
            text = stringResource(R.string.wizard_s2_header),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 步驟清單
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            resetSteps.forEachIndexed { index, stepText ->
                StepNumberItem(
                    number = index + 1,
                    text = stepText
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // 按鈕
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_s2_btn_next),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 畫面三：網路設定與 Tailscale 授權 (Step 3: Network & API Key)
 */
@Composable
fun StepNetworkAndApiKeyScreen(
    uiState: WizardUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAutoFillWifi: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onVerifyApiKey: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isApiKeyVisible by rememberSaveable { mutableStateOf(false) }

    val canProceed = uiState.wifiSsid.isNotBlank() &&
            uiState.isApiKeyVerified

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.wizard_s3_header),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // UI 區塊 1：網路資訊
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.wizard_s3_wifi_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = onAutoFillWifi,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.wizard_s3_wifi_btn_autofill), fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = uiState.wifiSsid,
                    onValueChange = onSsidChange,
                    label = { Text(stringResource(R.string.wizard_s3_wifi_ssid_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = uiState.wifiPassword,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.wizard_s3_wifi_pwd_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = stringResource(R.string.wizard_s3_wifi_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // UI 區塊 2：Tailscale Key 設定
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.wizard_s3_ts_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = stringResource(R.string.wizard_s3_ts_prompt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://login.tailscale.com/admin/settings/keys")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.wizard_s3_ts_btn_web))
                }

                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text(stringResource(R.string.wizard_s3_ts_key_label)) },
                    placeholder = { Text(stringResource(R.string.wizard_s3_ts_key_placeholder)) },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isApiKeyVisible) stringResource(R.string.wizard_s3_ts_key_hide) else stringResource(R.string.wizard_s3_ts_key_show)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = onVerifyApiKey,
                    enabled = uiState.apiKey.isNotBlank() && !uiState.isVerifyingApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isVerifyingApiKey) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.wizard_s3_ts_btn_verifying))
                    } else {
                        Text(stringResource(R.string.wizard_s3_ts_btn_verify))
                    }
                }

                // 狀態反饋
                if (uiState.isApiKeyVerified) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.wizard_s3_ts_success_icon),
                            tint = Color(0xFF2E7D32)
                        )
                        Text(
                            text = stringResource(R.string.wizard_s3_ts_success_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (!uiState.apiKeyVerifyError.isNullOrBlank()) {
                    Text(
                        text = uiState.apiKeyVerifyError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // 按鈕：產生部署條碼
        Button(
            onClick = onNext,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_s3_btn_gen_qr),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 畫面四：動態產鑰與掃碼部署 (Step 4: QR Code Provisioning)
 */
@Composable
fun StepScanScreen(
    uiState: WizardUiState,
    onRetryGenQrCode: () -> Unit,
    onFinish: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val secretSteps = listOf(
        stringResource(R.string.wizard_s4_step1),
        stringResource(R.string.wizard_s4_step2),
        stringResource(R.string.wizard_s4_step3)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 標題
        Text(
            text = stringResource(R.string.wizard_s4_header),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        // 步驟清單
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            secretSteps.forEachIndexed { index, stepText ->
                StepNumberItem(
                    number = index + 1,
                    text = stepText
                )
            }
        }

        // QR Code 顯示區 (220x220 dp Box)
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isGeneratingQrCode -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.wizard_s4_req_ts_key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                uiState.qrCodeBitmap != null -> {
                    Image(
                        bitmap = uiState.qrCodeBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.wizard_s4_qr_desc),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }

                !uiState.qrCodeError.isNullOrBlank() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_s4_qr_error, uiState.qrCodeError ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetryGenQrCode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.wizard_s4_btn_retry))
                        }
                    }
                }

                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.wizard_s4_qr_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 連線狀態區
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.wizard_waiting_scan),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // 完成並返回按鈕
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.wizard_btn_finish),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 貼心常見問題與排錯卡片 (FAQ Accordion)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.wizard_s4_faq_header),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.wizard_s4_faq1),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Text(
                    text = stringResource(R.string.wizard_s4_faq2),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * 通用的帶數字圖標清單項目 Component
 */
@Composable
private fun StepNumberItem(
    number: Int,
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
