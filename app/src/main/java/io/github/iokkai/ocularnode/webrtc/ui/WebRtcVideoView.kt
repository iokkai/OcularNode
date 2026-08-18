package io.github.iokkai.ocularnode.webrtc.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.iokkai.ocularnode.webrtc.WebRtcSessionManager
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Jetpack Compose wrapper for WebRTC's SurfaceViewRenderer with hardware acceleration
 * and proper lifecycle/EglBase management.
 */
@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    sessionManager: WebRtcSessionManager,
    modifier: Modifier = Modifier,
    isMirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL
) {
    val context = LocalContext.current

    val surfaceRenderer = remember {
        SurfaceViewRenderer(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            init(sessionManager.eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setMirror(isMirror)
            setScalingType(scalingType)
        }
    }

    DisposableEffect(videoTrack) {
        videoTrack?.addSink(surfaceRenderer)

        onDispose {
            videoTrack?.removeSink(surfaceRenderer)
        }
    }

    DisposableEffect(isMirror, scalingType) {
        surfaceRenderer.setMirror(isMirror)
        surfaceRenderer.setScalingType(scalingType)
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            surfaceRenderer.release()
        }
    }

    AndroidView(
        factory = { surfaceRenderer },
        modifier = modifier
    )
}
