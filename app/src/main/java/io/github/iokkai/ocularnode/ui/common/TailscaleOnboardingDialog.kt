package io.github.iokkai.ocularnode.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.ui.theme.*
import io.github.iokkai.ocularnode.util.NetworkUtils

/**
 * 首次安裝 / 未安裝 Tailscale 時的新手引導與註冊安裝對話框
 */
@Composable
fun TailscaleOnboardingDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AppSurface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, AppBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 頂部圖示徽章
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AppSecondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // 標題與副標題
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ts_onboarding_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    // 企業級標章 Badge
                    Surface(
                        color = AppSecondaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = stringResource(R.string.ts_onboarding_badge),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.ts_onboarding_subtitle),
                        fontSize = 12.sp,
                        color = AppTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                }

                // 為什麼需要 Tailscale (價值卡片)
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                    border = BorderStroke(1.dp, AppBorderLight),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = stringResource(R.string.ts_onboarding_why_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTextPrimary
                            )
                            Text(
                                text = stringResource(R.string.ts_onboarding_why_desc),
                                fontSize = 11.sp,
                                color = AppTextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // 3 步驟安裝與連線清單
                Surface(
                    color = AppSurfaceSubtle,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 步驟 1
                        StepRow(
                            number = 1,
                            title = stringResource(R.string.ts_onboarding_step1_title),
                            desc = stringResource(R.string.ts_onboarding_step1_desc)
                        )

                        HorizontalDivider(color = AppBorderSubtle, thickness = 0.5.dp)

                        // 步驟 2
                        StepRow(
                            number = 2,
                            title = stringResource(R.string.ts_onboarding_step2_title),
                            desc = stringResource(R.string.ts_onboarding_step2_desc)
                        )

                        HorizontalDivider(color = AppBorderSubtle, thickness = 0.5.dp)

                        // 步驟 3
                        StepRow(
                            number = 3,
                            title = stringResource(R.string.ts_onboarding_step3_title),
                            desc = stringResource(R.string.ts_onboarding_step3_desc)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 按鈕 1：前往 Google Play 下載安裝
                Button(
                    onClick = {
                        NetworkUtils.openTailscaleApp(context)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ts_onboarding_btn_install),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 按鈕 2：前往 Tailscale 網頁註冊/登入
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://login.tailscale.com/start")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppTextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ts_onboarding_btn_web),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 按鈕 3：稍後再說
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ts_onboarding_btn_later),
                        fontSize = 12.sp,
                        color = AppTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(AppPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = AppTextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
