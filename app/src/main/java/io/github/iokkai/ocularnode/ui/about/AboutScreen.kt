package io.github.iokkai.ocularnode.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.iokkai.ocularnode.util.UpdateCheckResult
import io.github.iokkai.ocularnode.util.UpdateInstallStage
import io.github.iokkai.ocularnode.util.UpdateManager
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iokkai.ocularnode.BuildConfig
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.ui.theme.*

data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val description: String,
    val url: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }

    val brandPrimaryColor = AppPrimary
    val textPrimaryColor = AppTextPrimary
    val textSecondaryColor = AppTextSecondary
    val cardBorderColor = AppBorder

    val ossLibraries = remember {
        listOf(
            OpenSourceLibrary(
                name = "Jetpack Compose & Material 3",
                author = "Google / Android Open Source Project",
                license = "Apache 2.0",
                description = "Modern declarative UI toolkit and Material Design 3 system.",
                url = "https://developer.android.com/jetpack/compose"
            ),
            OpenSourceLibrary(
                name = "AndroidX CameraX",
                author = "Google",
                license = "Apache 2.0",
                description = "Camera2 abstraction for device compatibility, preview, and streaming pipelines.",
                url = "https://developer.android.com/training/camerax"
            ),
            OpenSourceLibrary(
                name = "Google ML Kit (Object Detection & Image Labeling)",
                author = "Google",
                license = "Apache 2.0 / SDK Terms",
                description = "On-device real-time machine learning for smart motion filtering.",
                url = "https://developers.google.com/ml-kit"
            ),
            OpenSourceLibrary(
                name = "Tailscale Integration",
                author = "Tailscale Inc.",
                license = "BSD-3-Clause",
                description = "Zero-config Mesh VPN for secure point-to-point cross-network traversal.",
                url = "https://tailscale.com"
            ),
            OpenSourceLibrary(
                name = "ZXing (\"Zebra Crossing\")",
                author = "ZXing Authors",
                license = "Apache 2.0",
                description = "Multi-format 1D/2D barcode image processing and QR Code generation.",
                url = "https://github.com/zxing/zxing"
            ),
            OpenSourceLibrary(
                name = "OkHttp & Retrofit 2",
                author = "Square Inc.",
                license = "Apache 2.0",
                description = "HTTP client and type-safe REST framework for video streaming and Telegram API.",
                url = "https://square.github.io/okhttp/"
            ),
            OpenSourceLibrary(
                name = "Coil Compose",
                author = "Coil Contributors",
                license = "Apache 2.0",
                description = "Coroutine-first image loading library for Android.",
                url = "https://coil-kt.github.io/coil/"
            ),
            OpenSourceLibrary(
                name = "AndroidX Room & SQLite",
                author = "Google",
                license = "Apache 2.0",
                description = "Persistent local database storage for motion event logs and camera devices.",
                url = "https://developer.android.com/training/data-storage/room"
            ),
            OpenSourceLibrary(
                name = "AndroidX Security Crypto",
                author = "Google",
                license = "Apache 2.0",
                description = "Hardware-backed key encryption for sensitive authentication tokens.",
                url = "https://developer.android.com/topic/security/data"
            ),
            OpenSourceLibrary(
                name = "Kotlin Coroutines & Flow",
                author = "JetBrains",
                license = "Apache 2.0",
                description = "Asynchronous programming and reactive stream processing.",
                url = "https://github.com/Kotlin/kotlinx.coroutines"
            )
        )
    }

    var isOssExpanded by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    if (showUpdateDialog && updateResult != null) {
        UpdateAvailableDialog(
            result = updateResult!!,
            onDismiss = { showUpdateDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.about_title),
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header Hero Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppSecondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "App Logo",
                            tint = brandPrimaryColor,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "OcularNode",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimaryColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.about_slogan),
                        fontSize = 13.sp,
                        color = textSecondaryColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Clean version badge: directly display version number
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppPrimaryContainer,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = AppOnPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 檢查更新按鈕 (Check for Updates Button)
                    OutlinedButton(
                        onClick = {
                            if (!isCheckingUpdate) {
                                isCheckingUpdate = true
                                coroutineScope.launch {
                                    val result = UpdateManager.checkLatestUpdate(context)
                                    isCheckingUpdate = false
                                    if (result.hasUpdate) {
                                        updateResult = result
                                        showUpdateDialog = true
                                    } else if (result.errorMessage != null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.about_update_failed, result.errorMessage),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.about_update_latest_toast, "v${BuildConfig.VERSION_NAME}"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, brandPrimaryColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = brandPrimaryColor),
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = brandPrimaryColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.about_checking_update),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = brandPrimaryColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.about_btn_check_update).removePrefix("🔄 "),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Project & Author Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = brandPrimaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.about_section_author),
                            color = textPrimaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // Author Item
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.about_author_label),
                            fontSize = 14.sp,
                            color = textSecondaryColor
                        )
                        Text(
                            stringResource(R.string.about_author_name),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimaryColor
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // License Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLicenseDialog = true }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = brandPrimaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.about_license_label),
                                fontSize = 14.sp,
                                color = textPrimaryColor,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppSecondaryContainer,
                            border = BorderStroke(1.dp, AppBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    stringResource(R.string.about_license_name),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppOnPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = AppOnPrimaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // GitHub Repo Link
                    AboutLinkRow(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.about_github_repo),
                        url = "https://github.com/iokkai/OcularNode",
                        context = context
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Issues Link
                    AboutLinkRow(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.about_github_issues),
                        url = "https://github.com/iokkai/OcularNode/issues",
                        context = context
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Releases Link
                    AboutLinkRow(
                        icon = Icons.Default.NewReleases,
                        title = stringResource(R.string.about_github_releases),
                        url = "https://github.com/iokkai/OcularNode/releases",
                        context = context
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    // Privacy Policy Link
                    AboutLinkRow(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.about_privacy_policy),
                        url = "https://github.com/iokkai/OcularNode/blob/main/PRIVACY.md",
                        context = context
                    )
                }
            }

            // Key Highlights Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = brandPrimaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.about_section_highlights),
                            color = textPrimaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    HighlightItem(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.about_hl_kiosk_title),
                        description = stringResource(R.string.about_hl_kiosk_desc)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    HighlightItem(
                        icon = Icons.Default.VpnKey,
                        title = stringResource(R.string.about_hl_tailscale_title),
                        description = stringResource(R.string.about_hl_tailscale_desc)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    HighlightItem(
                        icon = Icons.Default.SmartToy,
                        title = stringResource(R.string.about_hl_ai_title),
                        description = stringResource(R.string.about_hl_ai_desc)
                    )
                }
            }

            // Open Source Acknowledgements Card (Expandable)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isOssExpanded = !isOssExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = brandPrimaryColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.about_section_oss),
                                color = textPrimaryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Icon(
                            imageVector = if (isOssExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isOssExpanded) "Collapse" else "Expand",
                            tint = brandPrimaryColor
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_oss_desc),
                        fontSize = 11.sp,
                        color = textSecondaryColor
                    )

                    AnimatedVisibility(visible = isOssExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            ossLibraries.forEachIndexed { index, lib ->
                                if (index > 0) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppSurfaceVariant)
                                }
                                OssLibraryItem(library = lib, context = context)
                            }
                        }
                    }
                }
            }

            // Disclaimers & Trademarks Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = brandPrimaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.about_section_disclaimer),
                            color = textPrimaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        stringResource(R.string.about_trademark_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_trademark_content),
                        fontSize = 12.sp,
                        color = textSecondaryColor,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    Text(
                        stringResource(R.string.about_liability_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_liability_content),
                        fontSize = 12.sp,
                        color = textSecondaryColor,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    Text(
                        stringResource(R.string.about_battery_safety_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_battery_safety_content),
                        fontSize = 12.sp,
                        color = textSecondaryColor,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    Text(
                        stringResource(R.string.about_kiosk_safety_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_kiosk_safety_content),
                        fontSize = 12.sp,
                        color = textSecondaryColor,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AppBorderLight)

                    Text(
                        stringResource(R.string.about_non_medical_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_non_medical_content),
                        fontSize = 12.sp,
                        color = textSecondaryColor,
                        lineHeight = 18.sp
                    )
                }
            }

            // Footer
            Text(
                text = stringResource(R.string.about_footer_copyright),
                fontSize = 11.sp,
                color = AppTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    url: String,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openBrowserUrl(context, url) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, fontSize = 14.sp, color = AppTextPrimary, fontWeight = FontWeight.Medium)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open Link",
            tint = AppTextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun HighlightItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AppSecondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppTextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 11.sp, color = AppTextSecondary, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun OssLibraryItem(
    library: OpenSourceLibrary,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (library.url != null) {
                    Modifier.clickable { openBrowserUrl(context, library.url) }
                } else Modifier
            )
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = library.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = AppTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppSurfaceVariant,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Text(
                    text = library.license,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = library.author,
            fontSize = 11.sp,
            color = AppPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = library.description,
            fontSize = 11.sp,
            color = AppTextSecondary
        )
    }
}

private fun openBrowserUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.about_license_dialog_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppTextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                    border = BorderStroke(1.dp, AppBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "GNU General Public License v3.0 (Copyleft)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AppPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "• 自由使用、學習、修改與分享原始碼\n• 衍生作品必須以相同 GPL v3 協議開源\n• 保障使用者的數位自由與隱私權",
                            fontSize = 11.sp,
                            color = AppTextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text(
                    stringResource(R.string.about_disclaimer_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = AppError
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.about_disclaimer_content),
                    fontSize = 11.sp,
                    color = AppTextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { openBrowserUrl(context, "https://github.com/iokkai/OcularNode/blob/main/LICENSE") },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.about_btn_view_license),
                        fontSize = 12.sp,
                        color = AppPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close), fontWeight = FontWeight.Bold, color = AppPrimary)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = AppSurface
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun UpdateAvailableDialog(
    result: UpdateCheckResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var installStage by remember { mutableStateOf(UpdateInstallStage.IDLE) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isBusy = installStage == UpdateInstallStage.DOWNLOADING ||
            installStage == UpdateInstallStage.VERIFYING ||
            installStage == UpdateInstallStage.INSTALLING_SILENT ||
            installStage == UpdateInstallStage.PROMPTING_SYSTEM_INSTALL

    AlertDialog(
        onDismissRequest = {
            if (!isBusy) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.about_update_found_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AppTextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.about_update_found_desc),
                    fontSize = 13.sp,
                    color = AppTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // New Version Badge
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppPrimaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "最新版本: ${result.latestVersionName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AppOnPrimaryContainer
                            )
                            Text(
                                text = "目前版本: v${BuildConfig.VERSION_NAME}",
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                        }
                    }
                }

                if (result.releaseNotes.isNotBlank() && installStage == UpdateInstallStage.IDLE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.about_update_notes_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AppTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                        border = BorderStroke(1.dp, AppBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.releaseNotes.trim(),
                            fontSize = 11.sp,
                            color = AppTextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Progress and Actions Area ──
                when (installStage) {
                    UpdateInstallStage.IDLE -> {
                        Button(
                            onClick = {
                                val targetUrl = result.downloadUrl.ifBlank { result.htmlUrl }
                                coroutineScope.launch {
                                    errorMessage = null
                                    UpdateManager.downloadAndInstallApk(
                                        context = context,
                                        downloadUrl = targetUrl,
                                        onProgress = { progress, downloaded, total ->
                                            downloadProgress = progress
                                            downloadedBytes = downloaded
                                            totalBytes = total
                                        },
                                        onStageChange = { stage, msg ->
                                            installStage = stage
                                            if (stage == UpdateInstallStage.FAILED) {
                                                errorMessage = msg
                                            }
                                        }
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppPrimary,
                                contentColor = AppSurface
                            ),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.about_btn_download_now),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Fallback browser download link
                        TextButton(
                            onClick = {
                                val targetUrl = result.downloadUrl.ifBlank { result.htmlUrl }
                                openBrowserUrl(context, targetUrl)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.about_btn_browser_fallback),
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                        }
                    }

                    UpdateInstallStage.DOWNLOADING -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                            border = BorderStroke(1.dp, AppBorder),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                val pct = (downloadProgress * 100).toInt().coerceIn(0, 100)
                                Text(
                                    text = stringResource(R.string.about_update_downloading, pct),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AppTextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (downloadProgress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = AppPrimary,
                                        trackColor = AppBorder
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = AppPrimary,
                                        trackColor = AppBorder
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(
                                        R.string.about_update_download_progress,
                                        formatFileSize(downloadedBytes),
                                        if (totalBytes > 0) formatFileSize(totalBytes) else "--"
                                    ),
                                    fontSize = 11.sp,
                                    color = AppTextSecondary,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }

                    UpdateInstallStage.VERIFYING -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = AppPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.about_update_verifying),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTextPrimary
                                )
                            }
                        }
                    }

                    UpdateInstallStage.INSTALLING_SILENT -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = AppPrimaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = AppPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.about_update_installing_silent),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppOnPrimaryContainer
                                )
                            }
                        }
                    }

                    UpdateInstallStage.PROMPTING_SYSTEM_INSTALL,
                    UpdateInstallStage.COMPLETED -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = AppPrimaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stringResource(R.string.about_update_complete),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppOnPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.about_update_prompting_install),
                                    fontSize = 11.sp,
                                    color = AppTextSecondary
                                )
                            }
                        }
                    }

                    UpdateInstallStage.FAILED -> {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x22FF5252)),
                            border = BorderStroke(1.dp, Color(0x66FF5252)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "❌ 下載或更新失敗",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                                if (!errorMessage.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = errorMessage!!,
                                        fontSize = 11.sp,
                                        color = AppTextPrimary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Retry button
                        Button(
                            onClick = {
                                val targetUrl = result.downloadUrl.ifBlank { result.htmlUrl }
                                coroutineScope.launch {
                                    errorMessage = null
                                    UpdateManager.downloadAndInstallApk(
                                        context = context,
                                        downloadUrl = targetUrl,
                                        onProgress = { progress, downloaded, total ->
                                            downloadProgress = progress
                                            downloadedBytes = downloaded
                                            totalBytes = total
                                        },
                                        onStageChange = { stage, msg ->
                                            installStage = stage
                                            if (stage == UpdateInstallStage.FAILED) {
                                                errorMessage = msg
                                            }
                                        }
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = AppSurface),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重試下載更新", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                val targetUrl = result.downloadUrl.ifBlank { result.htmlUrl }
                                openBrowserUrl(context, targetUrl)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.about_btn_browser_fallback),
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isBusy) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_close), fontWeight = FontWeight.Bold, color = AppPrimary)
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = AppSurface
    )
}
