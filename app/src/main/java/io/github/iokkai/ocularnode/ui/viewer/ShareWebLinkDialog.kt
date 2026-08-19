package io.github.iokkai.ocularnode.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.ui.theme.AppBackground
import io.github.iokkai.ocularnode.ui.theme.AppBorder
import io.github.iokkai.ocularnode.ui.theme.AppBorderSubtle
import io.github.iokkai.ocularnode.ui.theme.AppDarkSurface
import io.github.iokkai.ocularnode.ui.theme.AppOnPrimaryContainer
import io.github.iokkai.ocularnode.ui.theme.AppOnSecondaryContainer
import io.github.iokkai.ocularnode.ui.theme.AppPrimary
import io.github.iokkai.ocularnode.ui.theme.AppPrimaryContainer
import io.github.iokkai.ocularnode.ui.theme.AppSecondaryContainer
import io.github.iokkai.ocularnode.ui.theme.AppSuccess
import io.github.iokkai.ocularnode.ui.theme.AppSurface
import io.github.iokkai.ocularnode.ui.theme.AppTextMuted
import io.github.iokkai.ocularnode.ui.theme.AppTextPrimary
import io.github.iokkai.ocularnode.ui.theme.AppTextSecondary

/**
 * Dialog for copying and sharing decentralized Web Viewer links from Android Viewer.
 */
@Composable
fun ShareWebLinkDialog(
    camera: CameraDevice,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val webViewerUrl = remember(camera) { camera.getWebViewerUrl() }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(AppPrimaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.share_web_link_title),
                            color = AppTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = camera.name,
                            color = AppTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = AppPrimaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "⚡ WebRTC P2P (E2EE)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (!camera.ipv6Address.isNullOrBlank()) {
                        Surface(
                            color = AppSecondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AppBorderSubtle)
                        ) {
                            Text(
                                text = "🌐 IPv6",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.share_web_link_desc),
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // URL Preview Box
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppDarkSurface),
                    border = BorderStroke(1.dp, AppBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = webViewerUrl,
                            color = Color(0xFF64B5F6),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy Note
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = AppPrimaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.share_web_link_privacy_notice),
                        color = AppOnPrimaryContainer,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Copy Web Link Button
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("OcularNode Web Link", webViewerUrl)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.toast_web_link_copied), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_copy_web_link),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Share Link to Apps
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "OcularNode Web Viewer - ${camera.name}")
                                putExtra(Intent.EXTRA_TEXT, webViewerUrl)
                            }
                            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.btn_share_web_link))
                            context.startActivity(shareIntent)
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AppBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = AppTextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_share_web_link),
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Open in Browser (Direct preview on mobile)
                    OutlinedButton(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webViewerUrl))
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AppBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = AppTextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_open_in_browser),
                            color = AppTextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }

                    // Close Button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.btn_close),
                            color = AppTextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
