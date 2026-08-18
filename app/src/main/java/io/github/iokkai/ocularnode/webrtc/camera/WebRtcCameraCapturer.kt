package io.github.iokkai.ocularnode.webrtc.camera

import android.content.Context
import android.util.Log
import io.github.iokkai.ocularnode.webrtc.WebRtcSessionManager
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Camera capturer wrapper integrating Android Camera2 API with WebRTC VideoSource,
 * SurfaceTextureHelper (EglBase OpenGL context), and VideoTrack.
 */
class WebRtcCameraCapturer(
    private val context: Context,
    private val sessionManager: WebRtcSessionManager
) {

    companion object {
        private const val TAG = "WebRtcCameraCapturer"
        const val VIDEO_TRACK_ID = "OCULAR_VIDEO_TRACK_0"
    }

    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null

    var videoTrack: VideoTrack? = null
        private set

    var isCapturing: Boolean = false
        private set

    var isFrontFacing: Boolean = false
        private set

    fun startCapture(
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        useFrontCamera: Boolean = false
    ): VideoTrack {
        if (isCapturing && videoTrack != null) {
            return videoTrack!!
        }

        Log.i(TAG, "Starting camera capture: ${width}x${height} @ ${fps}fps (Front: $useFrontCamera)")
        isFrontFacing = useFrontCamera

        val enumerator: CameraEnumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Find matching camera device name
        var selectedDeviceName: String? = null
        for (deviceName in deviceNames) {
            if (useFrontCamera && enumerator.isFrontFacing(deviceName)) {
                selectedDeviceName = deviceName
                break
            }
            if (!useFrontCamera && enumerator.isBackFacing(deviceName)) {
                selectedDeviceName = deviceName
                break
            }
        }

        if (selectedDeviceName == null && deviceNames.isNotEmpty()) {
            selectedDeviceName = deviceNames[0]
        }

        requireNotNull(selectedDeviceName) { "No available Camera2 device found on this system" }

        val capturer = enumerator.createCapturer(selectedDeviceName, object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String?) {
                Log.e(TAG, "Camera error: $errorDescription")
            }

            override fun onCameraDisconnected() {
                Log.w(TAG, "Camera disconnected")
            }

            override fun onCameraFreezed(errorDescription: String?) {
                Log.w(TAG, "Camera frozen: $errorDescription")
            }

            override fun onCameraOpening(cameraName: String?) {
                Log.i(TAG, "Camera opening: $cameraName")
            }

            override fun onFirstFrameAvailable() {
                Log.i(TAG, "First video frame available")
            }

            override fun onCameraClosed() {
                Log.i(TAG, "Camera closed")
            }
        })

        cameraCapturer = capturer

        val surfaceHelper = SurfaceTextureHelper.create("WebRtcSurfaceTextureHelper", sessionManager.eglBase.eglBaseContext)
        surfaceTextureHelper = surfaceHelper

        val source = sessionManager.peerConnectionFactory.createVideoSource(false)
        videoSource = source

        capturer.initialize(surfaceHelper, context.applicationContext, source.capturerObserver)
        capturer.startCapture(width, height, fps)

        val track = sessionManager.peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, source)
        track.setEnabled(true)
        videoTrack = track
        isCapturing = true

        return track
    }

    fun switchCamera(onSuccess: ((Boolean) -> Unit)? = null) {
        cameraCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                Log.i(TAG, "Camera switched successfully. Is front: $isFrontCamera")
                isFrontFacing = isFrontCamera
                onSuccess?.invoke(isFrontCamera)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.e(TAG, "Failed to switch camera: $errorDescription")
            }
        })
    }

    fun changeCaptureFormat(width: Int, height: Int, fps: Int) {
        if (isCapturing) {
            Log.i(TAG, "Changing capture format to ${width}x${height} @ ${fps}fps")
            cameraCapturer?.changeCaptureFormat(width, height, fps)
        }
    }

    fun stopCapture() {
        if (!isCapturing) return
        Log.i(TAG, "Stopping camera capture")
        try {
            cameraCapturer?.stopCapture()
            cameraCapturer?.dispose()
            cameraCapturer = null

            videoTrack?.dispose()
            videoTrack = null

            videoSource?.dispose()
            videoSource = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            isCapturing = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera capture", e)
        }
    }
}
