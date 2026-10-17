package com.vibevision.app.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vibevision.app.journal.ErrorJournal
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages back-camera hardware lifecycle strictly locked to DEFAULT_BACK_CAMERA,
 * configuring continuous autofocus, auto-exposure stabilization, and target 60 FPS preview.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val vibrationAnalyzer: VibrationAnalyzer,
    private val onResolutionConfigured: (width: Int, height: Int) -> Unit,
    private val onTorchAvailabilityChanged: (Boolean) -> Unit
) {

    companion object {
        private const val TAG = "VibeCameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var activeSurfaceTexture: SurfaceTexture? = null
    private var activeSurface: Surface? = null

    var target60Fps: Boolean = true
        private set

    var isTorchEnabled: Boolean = false
        private set

    fun initialize() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera()
            } catch (e: Exception) {
                ErrorJournal.e(TAG, "Failed to get ProcessCameraProvider: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture) {
        this.activeSurfaceTexture = surfaceTexture
        bindCamera()
    }

    fun setTarget60FpsPreference(enabled: Boolean) {
        if (target60Fps != enabled) {
            target60Fps = enabled
            bindCamera()
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun bindCamera() {
        val provider = cameraProvider ?: return
        val st = activeSurfaceTexture ?: return

        try {
            provider.unbindAll()

            // Hardware Constraint: Locked strictly to rear inspection camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // 1. Build Preview targeting 60 FPS and Continuous Autofocus
            val previewBuilder = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))

            val previewExtender = Camera2Interop.Extender(previewBuilder)
            
            // Continuous autofocus for maximum optical inspection sharpness
            previewExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            previewExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO
            )

            // High-speed frame rate targeting
            val fpsRange = if (target60Fps) Range(30, 60) else Range(30, 30)
            previewExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                fpsRange
            )

            val newPreview = previewBuilder.build()

            newPreview.setSurfaceProvider { request ->
                val resolution = request.resolution
                Log.i(TAG, "Camera negotiated resolution: ${resolution.width}x${resolution.height}")
                st.setDefaultBufferSize(resolution.width, resolution.height)
                onResolutionConfigured(resolution.width, resolution.height)

                activeSurface?.release()
                val surface = Surface(st)
                activeSurface = surface

                request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { result ->
                    Log.d(TAG, "Surface result: ${result.resultCode}")
                    surface.release()
                }
            }
            this.preview = newPreview

            // 2. Build ImageAnalysis for Real-time Signal Processing
            val analysisBuilder = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)

            val analysisExtender = Camera2Interop.Extender(analysisBuilder)
            analysisExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            analysisExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                fpsRange
            )

            val newAnalysis = analysisBuilder.build()
            newAnalysis.setAnalyzer(analysisExecutor, vibrationAnalyzer)
            this.imageAnalysis = newAnalysis

            // 3. Bind to Lifecycle
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                newPreview,
                newAnalysis
            )
            this.camera = boundCamera

            val hasFlash = boundCamera.cameraInfo.hasFlashUnit()
            onTorchAvailabilityChanged(hasFlash)
            if (hasFlash && isTorchEnabled) {
                boundCamera.cameraControl.enableTorch(true)
            }

        } catch (e: Exception) {
            ErrorJournal.e(TAG, "Camera binding error: ${e.message}", e)
        }
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return false
        if (!cam.cameraInfo.hasFlashUnit()) return false

        isTorchEnabled = !isTorchEnabled
        cam.cameraControl.enableTorch(isTorchEnabled)
        return isTorchEnabled
    }

    fun focusOnPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth.toFloat(), viewHeight.toFloat())
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun shutdown() {
        try {
            activeSurface?.release()
            activeSurface = null
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
        } catch (e: Exception) {
            ErrorJournal.e(TAG, "Camera shutdown error: ${e.message}", e)
        }
    }
}
