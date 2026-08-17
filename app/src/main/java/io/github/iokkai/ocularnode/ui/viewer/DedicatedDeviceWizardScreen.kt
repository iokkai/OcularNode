package io.github.iokkai.ocularnode.ui.viewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.ui.theme.*

private val WizardBackgroundColor = AppBackground
private val WizardSurfaceCardColor = AppSurface
private val WizardBorderColor = AppBorder
private val WizardPrimaryColor = AppPrimary
private val WizardPrimaryContainerColor = AppSecondaryContainer
private val WizardTextPrimaryColor = AppTextPrimary
private val WizardTextSecondaryColor = AppTextSecondary
private val WizardTextMutedColor = AppTextMuted
private val WizardWarningContainerColor = AppErrorContainer
private val WizardWarningTextColor = AppErrorTextDark
private val WizardErrorColor = AppError
private val WizardSuccessColor = AppSuccess
private val WizardSuccessContainerColor = AppSuccessContainer

/**
 * 專用監控設備部署精靈主畫面 (Dedicated Device Wizard)
 * 統一採用乾淨俐落的 Light Theme (紫白質感配色)，與主 App 其他頁面一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedicatedDeviceWizardScreen(
    onBack: () -> Unit = {},
    viewModel: WizardViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showWifiPermissionRationaleDialog by remember { mutableStateOf(false) }

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
            showWifiPermissionRationaleDialog = true
        }
    }

    if (showWifiPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showWifiPermissionRationaleDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = WizardPrimaryColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.wizard_wifi_permission_dialog_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    color = WizardTextPrimaryColor
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.wizard_wifi_permission_dialog_msg),
                    fontSize = 13.sp,
                    color = WizardTextSecondaryColor,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWifiPermissionRationaleDialog = false
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WizardPrimaryColor, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.wizard_wifi_permission_btn_grant),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWifiPermissionRationaleDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.wizard_wifi_permission_btn_cancel),
                        color = WizardTextMutedColor
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
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
                        fontSize = 18.sp,
                        color = WizardTextPrimaryColor
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
                            contentDescription = stringResource(R.string.wizard_btn_back),
                            tint = WizardTextPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WizardBackgroundColor
                )
            )
        },
        containerColor = WizardBackgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(WizardBackgroundColor)
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
                            onNext = {
                                onAutoFillWifiRequested()
                                viewModel.goToStep(2)
                            }
                        )

                        2 -> StepNetworkAndApiKeyScreen(
                            uiState = uiState,
                            onSsidChange = { viewModel.setWifiSsid(it) },
                            onPasswordChange = { viewModel.setWifiPassword(it) },
                            onAutoFillWifi = onAutoFillWifiRequested,
                            onApiKeyChange = { viewModel.setApiKey(it) },
                            onVerifyApiKey = { viewModel.verifyApiKey(context) },
                            onNext = {
                                viewModel.goToStep(3)
                                viewModel.generateProvisioningQrCode(context)
                            }
                        )

                        3 -> StepResetScreen(
                            onNext = {
                                viewModel.goToStep(4)
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
        stringResource(R.string.wizard_step_config),
        stringResource(R.string.wizard_step_reset),
        stringResource(R.string.wizard_step_provision)
    )

    Surface(
        color = AppSurfaceVariant,
        border = BorderStroke(1.dp, AppBorderLight),
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
                                    isCompleted -> WizardSuccessColor
                                    isCurrent -> WizardPrimaryColor
                                    else -> AppBorder
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
                                color = if (isCurrent) Color.White else AppTextSecondary
                            )
                        }
                    }

                    Text(
                        text = title.substringAfter(". "),
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) WizardPrimaryColor else WizardTextSecondaryColor
                    )
                }

                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(1.5.dp)
                            .background(
                                if (stepNum < currentStep) WizardSuccessColor else AppBorder
                            )
                    )
                }
            }
        }
    }
}

/**
 * 畫面一：價值傳遞與主副手機安全確認 (Step 1: Warning & Safety Assurance)
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 標題與說明
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.wizard_s1_header),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WizardTextPrimaryColor
            )
            Text(
                text = stringResource(R.string.wizard_s1_desc),
                fontSize = 13.sp,
                color = WizardTextSecondaryColor,
                lineHeight = 20.sp
            )
        }

        // 1. 主用機安全卡 (Soft Green Tint)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = WizardSuccessContainerColor.copy(alpha = 0.6f),
                contentColor = AppSuccessDark
            ),
            border = BorderStroke(1.dp, WizardSuccessColor.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = WizardSuccessColor.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = WizardSuccessColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.wizard_s1_safe_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppSuccessDark
                    )
                    Text(
                        text = stringResource(R.string.wizard_s1_safe_desc),
                        fontSize = 12.sp,
                        color = AppSuccessDark.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 2. 舊手機清除重置卡 (Soft Red Tint)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = WizardWarningContainerColor,
                contentColor = WizardWarningTextColor
            ),
            border = BorderStroke(1.dp, AppErrorBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = WizardErrorColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.wizard_s1_warn_icon),
                        tint = WizardErrorColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.wizard_s1_warn_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WizardWarningTextColor
                    )
                    Text(
                        text = stringResource(R.string.wizard_s1_warn_body),
                        fontSize = 12.sp,
                        color = WizardWarningTextColor.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // 防呆勾選框 (Checkbox Card)
        Card(
            onClick = { isConfirmed = !isConfirmed },
            colors = CardDefaults.cardColors(containerColor = WizardSurfaceCardColor),
            border = BorderStroke(1.dp, WizardBorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isConfirmed,
                    onCheckedChange = { isConfirmed = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = WizardPrimaryColor,
                        uncheckedColor = WizardTextSecondaryColor
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.wizard_s1_chk_erase),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WizardTextPrimaryColor,
                    lineHeight = 18.sp
                )
            }
        }

        // 按鈕
        Button(
            onClick = onNext,
            enabled = isConfirmed,
            colors = ButtonDefaults.buttonColors(
                containerColor = WizardPrimaryColor,
                contentColor = Color.White,
                disabledContainerColor = AppBorderSubtle,
                disabledContentColor = AppTextDisabled
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_s1_btn_next),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 畫面二：網路設定與 Tailscale 授權 (Step 2: Network & Key Config)
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

    val canProceed = uiState.wifiSsid.isNotBlank() && uiState.isApiKeyVerified

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = stringResource(R.string.wizard_s2_config_header),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = WizardTextPrimaryColor
        )

        // UI 區塊 1：Wi-Fi 設定卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = WizardSurfaceCardColor),
            border = BorderStroke(1.dp, WizardBorderColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            tint = WizardPrimaryColor
                        )
                        Text(
                            text = stringResource(R.string.wizard_s3_wifi_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WizardTextPrimaryColor
                        )
                    }

                    TextButton(
                        onClick = onAutoFillWifi,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_s3_wifi_btn_autofill),
                            fontSize = 12.sp,
                            color = WizardPrimaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Wi-Fi SSID (清晰高對比深色文字)
                OutlinedTextField(
                    value = uiState.wifiSsid,
                    onValueChange = onSsidChange,
                    label = { Text(stringResource(R.string.wizard_s3_wifi_ssid_label)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WizardTextPrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WizardTextPrimaryColor,
                        unfocusedTextColor = WizardTextPrimaryColor,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = WizardPrimaryColor,
                        unfocusedBorderColor = WizardBorderColor,
                        focusedLabelColor = WizardPrimaryColor,
                        unfocusedLabelColor = WizardTextSecondaryColor
                    )
                )

                if (uiState.wifiSsid.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.wizard_s3_wifi_autofilled_badge),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = WizardSuccessColor
                    )
                }

                // Wi-Fi 密碼
                OutlinedTextField(
                    value = uiState.wifiPassword,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.wizard_s3_wifi_pwd_label)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = WizardTextPrimaryColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WizardTextPrimaryColor,
                        unfocusedTextColor = WizardTextPrimaryColor,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = WizardPrimaryColor,
                        unfocusedBorderColor = WizardBorderColor,
                        focusedLabelColor = WizardPrimaryColor,
                        unfocusedLabelColor = WizardTextSecondaryColor
                    )
                )

                Text(
                    text = stringResource(R.string.wizard_s3_wifi_hint),
                    fontSize = 11.sp,
                    color = WizardTextSecondaryColor,
                    lineHeight = 16.sp
                )
            }
        }

        // UI 區塊 2：Tailscale 金鑰設定卡片
        var isFaqExpanded by rememberSaveable { mutableStateOf(false) }

        Card(
            colors = CardDefaults.cardColors(containerColor = WizardSurfaceCardColor),
            border = BorderStroke(1.dp, WizardBorderColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = WizardPrimaryColor
                    )
                    Text(
                        text = stringResource(R.string.wizard_s3_ts_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WizardTextPrimaryColor
                    )
                }

                // 2 步取得金鑰簡明引導
                Surface(
                    color = WizardPrimaryContainerColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, WizardPrimaryContainerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_s3_ts_prompt),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WizardPrimaryColor
                        )
                        Text(
                            text = stringResource(R.string.wizard_s3_ts_newuser_step1),
                            fontSize = 12.sp,
                            color = WizardTextPrimaryColor,
                            lineHeight = 17.sp
                        )
                        Text(
                            text = stringResource(R.string.wizard_s3_ts_newuser_step2),
                            fontSize = 12.sp,
                            color = WizardTextPrimaryColor,
                            lineHeight = 17.sp
                        )
                    }
                }

                // 前往網頁按鈕
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://login.tailscale.com/admin/settings/keys")
                        )
                        context.startActivity(intent)
                    },
                    border = BorderStroke(1.dp, WizardPrimaryColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WizardPrimaryColor),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WizardPrimaryColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.wizard_s3_ts_btn_web),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 摺疊常見疑問 (FAQ)
                Surface(
                    color = AppSurfaceSubtle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isFaqExpanded = !isFaqExpanded }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.wizard_s3_ts_faq_toggle),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = WizardPrimaryColor
                            )
                            Icon(
                                imageVector = if (isFaqExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = WizardPrimaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = isFaqExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.wizard_s3_ts_faq_content),
                                    fontSize = 11.sp,
                                    color = WizardTextSecondaryColor,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // 2.5 金鑰輸入欄位
                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text(stringResource(R.string.wizard_s3_ts_key_label)) },
                    placeholder = { Text(stringResource(R.string.wizard_s3_ts_key_placeholder)) },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = WizardTextPrimaryColor
                    ),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isApiKeyVisible) stringResource(R.string.wizard_s3_ts_key_hide) else stringResource(R.string.wizard_s3_ts_key_show),
                                tint = WizardTextSecondaryColor
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WizardTextPrimaryColor,
                        unfocusedTextColor = WizardTextPrimaryColor,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = WizardPrimaryColor,
                        unfocusedBorderColor = WizardBorderColor,
                        focusedLabelColor = WizardPrimaryColor
                    )
                )

                // 驗證按鈕
                Button(
                    onClick = onVerifyApiKey,
                    enabled = uiState.apiKey.isNotBlank() && !uiState.isVerifyingApiKey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WizardPrimaryColor,
                        contentColor = Color.White,
                        disabledContainerColor = AppBorderSubtle,
                        disabledContentColor = AppTextDisabled
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isVerifyingApiKey) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.wizard_s3_ts_btn_verifying), fontSize = 13.sp)
                    } else {
                        Text(
                            text = stringResource(R.string.wizard_s3_ts_btn_verify),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 驗證狀態
                if (uiState.isApiKeyVerified) {
                    Surface(
                        color = WizardSuccessContainerColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.wizard_s3_ts_success_icon),
                                tint = WizardSuccessColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.wizard_s3_ts_success_text),
                                fontSize = 13.sp,
                                color = WizardSuccessColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (!uiState.apiKeyVerifyError.isNullOrBlank()) {
                    Text(
                        text = uiState.apiKeyVerifyError,
                        fontSize = 12.sp,
                        color = WizardErrorColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // 下一步：前往重置舊手機教學
        Button(
            onClick = onNext,
            enabled = canProceed,
            colors = ButtonDefaults.buttonColors(
                containerColor = WizardPrimaryColor,
                contentColor = Color.White,
                disabledContainerColor = AppBorderSubtle,
                disabledContentColor = AppTextDisabled
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_s2_config_btn_next),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 畫面三：手把手重置教學與重要防呆警告 (Step 3: Reset Instructions & Critical Alert)
 */
@Composable
fun StepResetScreen(
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    val resetSteps = listOf(
        stringResource(R.string.wizard_s2_step1),
        stringResource(R.string.wizard_s2_step2),
        stringResource(R.string.wizard_s2_step3)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 標題
        Text(
            text = stringResource(R.string.wizard_s3_reset_header),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = WizardTextPrimaryColor
        )

        // 步驟清單
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            resetSteps.forEachIndexed { index, stepText ->
                StepNumberItem(
                    number = index + 1,
                    text = stepText
                )
            }
        }

        // 🚨 超大醒目防呆紅卡：重置開機後「絕對不要點擊開始」！
        Card(
            colors = CardDefaults.cardColors(
                containerColor = WizardWarningContainerColor,
                contentColor = WizardWarningTextColor
            ),
            border = BorderStroke(2.dp, WizardErrorColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = WizardErrorColor.copy(alpha = 0.18f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = WizardErrorColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.wizard_s3_reset_critical_warn_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WizardErrorColor,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = stringResource(R.string.wizard_s3_reset_critical_warn_desc),
                        fontSize = 13.sp,
                        color = WizardWarningTextColor,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // 按鈕
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = WizardPrimaryColor, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wizard_s3_reset_btn_next),
                fontSize = 15.sp,
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
    val context = LocalContext.current
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 標題
        Text(
            text = stringResource(R.string.wizard_s4_header),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = WizardTextPrimaryColor,
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

        // QR Code 顯示區 (240x240 dp Box)
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(
                    width = 1.5.dp,
                    color = WizardBorderColor,
                    shape = RoundedCornerShape(22.dp)
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
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp,
                            color = WizardPrimaryColor
                        )
                        Text(
                            text = stringResource(R.string.wizard_s4_req_ts_key),
                            fontSize = 13.sp,
                            color = WizardTextSecondaryColor,
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
                            .padding(14.dp)
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
                            fontSize = 12.sp,
                            color = WizardErrorColor,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetryGenQrCode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WizardErrorColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.wizard_s4_btn_retry), fontSize = 12.sp)
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
                            tint = WizardPrimaryColor
                        )
                        Text(
                            text = stringResource(R.string.wizard_s4_qr_placeholder),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WizardTextSecondaryColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 狀態指示卡片 (靜態圖示，不再一直無限轉圈圈)
        Surface(
            color = WizardPrimaryContainerColor.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, WizardPrimaryContainerColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(WizardPrimaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.wizard_s4_status_ready_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppOnPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.wizard_s4_status_ready_desc),
                        fontSize = 12.sp,
                        color = WizardTextSecondaryColor,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Tailscale Machines 後台停用過期跳轉按鈕
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://login.tailscale.com/admin/machines"))
                context.startActivity(intent)
            },
            border = BorderStroke(1.dp, WizardPrimaryColor),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardPrimaryColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.btn_open_tailscale_machines),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WizardPrimaryColor
            )
        }

        // 完成並返回按鈕
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WizardPrimaryColor,
                contentColor = Color.White
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

        // 貼心常見問題與排錯卡片 (FAQ Card)
        Card(
            colors = CardDefaults.cardColors(containerColor = WizardSurfaceCardColor),
            border = BorderStroke(1.dp, WizardBorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.wizard_s4_faq_header),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = WizardPrimaryColor
                )

                Text(
                    text = stringResource(R.string.wizard_s4_faq1),
                    fontSize = 11.sp,
                    color = WizardTextSecondaryColor,
                    lineHeight = 16.sp
                )

                Text(
                    text = stringResource(R.string.wizard_s4_faq2),
                    fontSize = 11.sp,
                    color = WizardTextSecondaryColor,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * 通用的帶數字圖標清單項目 Component (White Card + Purple Badge)
 */
@Composable
private fun StepNumberItem(
    number: Int,
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WizardSurfaceCardColor),
        border = BorderStroke(1.dp, WizardBorderColor),
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
                        color = WizardPrimaryColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            Text(
                text = text,
                fontSize = 13.sp,
                color = WizardTextPrimaryColor,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
