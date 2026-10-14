package com.vibevision.app.gl

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.vibevision.app.media.VideoRecorder
import com.vibevision.app.model.DiagnosticState
import com.vibevision.app.model.DisplayMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig as EGLConfigKhronos
import javax.microedition.khronos.opengles.GL10

/**
 * Custom OpenGL ES 2.0 Renderer executing real-time Differential Null Baseline Extraction,
 * Sub-Pixel Motion Amplification, FBO texture ring buffering, and video/photo recording.
 */
class VibeRenderer(
    private val surfaceView: GLSurfaceView,
    private val onFpsUpdated: (Float) -> Unit,
    private val onSurfaceTextureReady: (surfaceTexture: SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "VibeRenderer"
        private const val RING_BUFFER_CAPACITY = 12
    }

    // Dynamic processing parameters (thread-safe)
    @Volatile
    var gain: Float = 8.0f // Default 8.0x
        set(value) {
            field = value.coerceIn(DiagnosticState.MIN_GAIN, DiagnosticState.MAX_GAIN)
        }

    @Volatile
    var frameDelay: Int = 2 // Default 2 frames
        set(value) {
            field = value.coerceIn(DiagnosticState.MIN_DELAY, DiagnosticState.MAX_DELAY)
        }

    @Volatile
    var displayMode: DisplayMode = DisplayMode.DIFFERENTIAL_NULL

    // Viewport dimensions
    private var viewportWidth = 1920
    private var viewportHeight = 1080

    val currentViewportWidth: Int get() = viewportWidth
    val currentViewportHeight: Int get() = viewportHeight

    // Camera native resolution (separate from viewport — FBO always matches camera)
    private var cameraWidth = 1920
    private var cameraHeight = 1080

    // Camera texture & transformation
    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val stMatrix = FloatArray(16)
    private var frameAvailable = false
    private val frameLock = Object()

    // FBO Ring buffer for delayed frame comparison
    private val ringBuffer = FboRingBuffer(RING_BUFFER_CAPACITY)

    // Shader programs & handles
    private var oesProgram = 0
    private var oesPositionLoc = -1
    private var oesTexCoordLoc = -1
    private var oesMatrixLoc = -1
    private var oesTextureLoc = -1

    private var amplifierProgram = 0
    private var amplifierPositionLoc = -1
    private var amplifierTexCoordLoc = -1
    private var amplifierCurrentLoc = -1
    private var amplifierDelayedLoc = -1
    private var amplifierGainLoc = -1
    private var amplifierModeLoc = -1
    private var amplifierTexAspectLoc = -1
    private var amplifierViewAspectLoc = -1

    // Quad geometry buffers
    private val quadVertexBuffer: FloatBuffer = ShaderUtils.getQuadVertexBuffer()
    private val quadTexCoordBuffer: FloatBuffer = ShaderUtils.getQuadTexCoordBuffer()

    // FPS estimation
    private var lastFrameTimeNs = 0L
    private var smoothedFps = 60.0f
    private var fpsUpdateCounter = 0

    // Snapshot request handling
    @Volatile
    private var pendingSnapshotCallback: ((Bitmap) -> Unit)? = null

    // Freeze-Frame: static reference texture (Mode 2)
    private var freezeTextureId = 0
    @Volatile
    private var hasFreezeFrame = false
    @Volatile
    private var shouldCaptureFreezeFrame = false
    @Volatile
    private var pendingFreezeCaptureCallback: (() -> Unit)? = null

    // Video recording handling via EGL14 Surface
    private var videoRecorder: VideoRecorder? = null
    private var recordEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var recordWidth = 1920
    private var recordHeight = 1080
    @Volatile
    private var shouldStartRecording = false
    @Volatile
    private var shouldStopRecording = false
    private var pendingRecordSurface: Surface? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfigKhronos?) {
        Log.i(TAG, "onSurfaceCreated: Initializing OpenGL ES 2.0 context")

        GLES20.glClearColor(0.043f, 0.055f, 0.078f, 1.0f) // #0B0E14
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        initShaders()
        initOesTexture()
        initFreezeTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: ${width}x${height}")
        viewportWidth = width
        viewportHeight = height
        // Note: FBO ring buffer is sized to the camera resolution, not the viewport.
        // It is set up in setCameraPreviewSize() when the camera reports its resolution.
        // We only resize here if no camera size has been set yet (safety fallback).
        if (!ringBuffer.isReady) {
            ringBuffer.setup(width, height)
            updateFreezeTextureSize(width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        var updateSurface = false
        synchronized(frameLock) {
            if (frameAvailable) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)
                frameAvailable = false
                updateSurface = true
            }
        }

        // Measure FPS
        val now = System.nanoTime()
        if (lastFrameTimeNs > 0) {
            val deltaNs = now - lastFrameTimeNs
            if (deltaNs > 0) {
                val currentFps = 1_000_000_000.0f / deltaNs
                smoothedFps = smoothedFps * 0.85f + currentFps * 0.15f
                fpsUpdateCounter++
                if (fpsUpdateCounter >= 15) {
                    fpsUpdateCounter = 0
                    onFpsUpdated(smoothedFps)
                }
            }
        }
        lastFrameTimeNs = now

        if (!updateSurface && !ringBuffer.isReady) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        // -------------------------------------------------------------
        // PASS 1: Blit OES Camera texture into the next Ring Buffer FBO
        // -------------------------------------------------------------
        if (updateSurface) {
            val targetFboId = ringBuffer.advanceForNextWrite()

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFboId)
            GLES20.glViewport(0, 0, ringBuffer.bufferWidth, ringBuffer.bufferHeight)

            GLES20.glUseProgram(oesProgram)

            quadVertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(oesPositionLoc)
            GLES20.glVertexAttribPointer(oesPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)

            quadTexCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(oesTexCoordLoc)
            GLES20.glVertexAttribPointer(oesTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)

            GLES20.glUniformMatrix4fv(oesMatrixLoc, 1, false, stMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(oesTextureLoc, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(oesPositionLoc)
            GLES20.glDisableVertexAttribArray(oesTexCoordLoc)
        }

        // -------------------------------------------------------------
        // Check for Freeze Frame Capture Request
        // -------------------------------------------------------------
        if (shouldCaptureFreezeFrame && ringBuffer.isReady && ringBuffer.totalFrameCount > 0L) {
            shouldCaptureFreezeFrame = false
            copyCurrentFrameToFreezeTexture()
            hasFreezeFrame = true
            val cb = pendingFreezeCaptureCallback
            pendingFreezeCaptureCallback = null
            if (cb != null) {
                surfaceView.post(cb)
            }
        }

        // -------------------------------------------------------------
        // PASS 2: Sub-Pixel Motion Amplifier to Screen Framebuffer
        // -------------------------------------------------------------
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        renderAmplifierPass()

        // -------------------------------------------------------------
        // Check for Snapshot Capture Request
        // -------------------------------------------------------------
        val snapshotCb = pendingSnapshotCallback
        if (snapshotCb != null) {
            pendingSnapshotCallback = null
            captureFramePixels(snapshotCb)
        }

        // -------------------------------------------------------------
        // Video Recording Render Pass via EGL14
        // -------------------------------------------------------------
        handleVideoRecordingPass(now)
    }

    private fun renderAmplifierPass(targetWidth: Int = viewportWidth, targetHeight: Int = viewportHeight) {
        GLES20.glUseProgram(amplifierProgram)

        quadVertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(amplifierPositionLoc)
        GLES20.glVertexAttribPointer(amplifierPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)

        quadTexCoordBuffer.position(0)
        GLES20.glEnableVertexAttribArray(amplifierTexCoordLoc)
        GLES20.glVertexAttribPointer(amplifierTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)

        // Bind Current Frame (unit 0)
        val currentTexId = ringBuffer.getCurrentTextureId()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTexId)
        GLES20.glUniform1i(amplifierCurrentLoc, 0)

        // Bind Delayed Frame (unit 1) — or frozen static texture in FREEZE_FRAME mode
        val isFreezeMode = displayMode == DisplayMode.FREEZE_FRAME && hasFreezeFrame
        if (isFreezeMode) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, freezeTextureId)
        } else {
            val delayedTexId = ringBuffer.getDelayedTextureId(frameDelay)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, delayedTexId)
        }
        GLES20.glUniform1i(amplifierDelayedLoc, 1)

        GLES20.glUniform1f(amplifierGainLoc, gain)
        GLES20.glUniform1i(amplifierModeLoc, displayMode.shaderModeId)

        // Aspect ratio uniforms for center-crop UV correction in the display vertex shader
        val texAspect = cameraWidth.toFloat() / cameraHeight.toFloat()
        val viewAspect = targetWidth.toFloat() / targetHeight.toFloat()
        GLES20.glUniform1f(amplifierTexAspectLoc, texAspect)
        GLES20.glUniform1f(amplifierViewAspectLoc, viewAspect)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(amplifierPositionLoc)
        GLES20.glDisableVertexAttribArray(amplifierTexCoordLoc)
    }

    private fun captureFramePixels(callback: (Bitmap) -> Unit) {
        try {
            val pixelBuffer = ByteBuffer.allocateDirect(viewportWidth * viewportHeight * 4).apply {
                order(ByteOrder.nativeOrder())
                position(0)
            }
            GLES20.glReadPixels(
                0, 0, viewportWidth, viewportHeight,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer
            )

            val rawBitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
            pixelBuffer.position(0)
            rawBitmap.copyPixelsFromBuffer(pixelBuffer)

            // Flip vertically because OpenGL coordinates start at bottom-left
            val flipMatrix = Matrix().apply { postScale(1.0f, -1.0f) }
            val finalBitmap = Bitmap.createBitmap(
                rawBitmap, 0, 0, viewportWidth, viewportHeight, flipMatrix, true
            )
            rawBitmap.recycle()

            callback(finalBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error in glReadPixels snapshot", e)
        }
    }

    private fun handleVideoRecordingPass(nowNs: Long) {
        val recorder = videoRecorder ?: return

        val eglDisplay = EGL14.eglGetCurrentDisplay()
        val eglContext = EGL14.eglGetCurrentContext()

        // Initialize recording EGL surface if requested
        if (shouldStartRecording) {
            shouldStartRecording = false
            val surface = pendingRecordSurface
            if (surface != null && surface.isValid) {
                try {
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val numConfigs = IntArray(1)
                    val attribList = intArrayOf(
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_NONE
                    )
                    EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                    val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                    recordEglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay, configs[0], surface, surfaceAttribs, 0
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create record EGL surface", e)
                }
            }
        }

        // Render to encoder EGL surface
        if (recorder.isRecording && recordEglSurface != EGL14.EGL_NO_SURFACE) {
            try {
                val displayEglSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)

                EGL14.eglMakeCurrent(eglDisplay, recordEglSurface, recordEglSurface, eglContext)
                GLES20.glViewport(0, 0, recordWidth, recordHeight)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                renderAmplifierPass(recordWidth, recordHeight)

                EGLExt.eglPresentationTimeANDROID(eglDisplay, recordEglSurface, nowNs)
                EGL14.eglSwapBuffers(eglDisplay, recordEglSurface)

                // Restore display EGL surface
                EGL14.eglMakeCurrent(eglDisplay, displayEglSurface, displayEglSurface, eglContext)
                GLES20.glViewport(0, 0, viewportWidth, viewportHeight)

                recorder.drainEncoder(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering to encoder surface", e)
            }
        }

        // Tear down recording EGL surface if requested
        if (shouldStopRecording) {
            shouldStopRecording = false
            if (recordEglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, recordEglSurface)
                recordEglSurface = EGL14.EGL_NO_SURFACE
            }
            pendingRecordSurface = null
        }
    }

    fun requestSnapshot(callback: (Bitmap) -> Unit) {
        pendingSnapshotCallback = callback
        surfaceView.requestRender()
    }

    fun startRecording(recorder: VideoRecorder, surface: Surface, width: Int, height: Int) {
        this.videoRecorder = recorder
        this.pendingRecordSurface = surface
        this.recordWidth = width
        this.recordHeight = height
        this.shouldStartRecording = true
        surfaceView.requestRender()
    }

    fun stopRecording() {
        this.shouldStopRecording = true
        surfaceView.requestRender()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(frameLock) {
            frameAvailable = true
        }
        surfaceView.requestRender()
    }

    fun setCameraPreviewSize(width: Int, height: Int) {
        cameraWidth = width
        cameraHeight = height
        surfaceView.queueEvent {
            Log.i(TAG, "Configuring FBO Ring Buffer for camera size: ${width}x${height}")
            ringBuffer.setup(width, height)
            updateFreezeTextureSize(width, height)
        }
    }

    private fun initShaders() {
        // 1. OES -> FBO blit program
        oesProgram = ShaderUtils.createProgram(
            ShaderUtils.OES_VERTEX_SHADER,
            ShaderUtils.OES_FRAGMENT_SHADER
        )
        oesPositionLoc = GLES20.glGetAttribLocation(oesProgram, "aPosition")
        oesTexCoordLoc = GLES20.glGetAttribLocation(oesProgram, "aTexCoord")
        oesMatrixLoc = GLES20.glGetUniformLocation(oesProgram, "uSTMatrix")
        oesTextureLoc = GLES20.glGetUniformLocation(oesProgram, "uOesTexture")

        // 2. Sub-Pixel Motion Amplifier program
        amplifierProgram = ShaderUtils.createProgram(
            ShaderUtils.DISPLAY_VERTEX_SHADER,
            ShaderUtils.MOTION_AMPLIFIER_FRAGMENT_SHADER
        )
        amplifierPositionLoc = GLES20.glGetAttribLocation(amplifierProgram, "aPosition")
        amplifierTexCoordLoc = GLES20.glGetAttribLocation(amplifierProgram, "aTexCoord")
        amplifierCurrentLoc = GLES20.glGetUniformLocation(amplifierProgram, "uCurrentFrame")
        amplifierDelayedLoc = GLES20.glGetUniformLocation(amplifierProgram, "uDelayedFrame")
        amplifierGainLoc = GLES20.glGetUniformLocation(amplifierProgram, "uGain")
        amplifierModeLoc = GLES20.glGetUniformLocation(amplifierProgram, "uDisplayMode")
        amplifierTexAspectLoc = GLES20.glGetUniformLocation(amplifierProgram, "uTexAspect")
        amplifierViewAspectLoc = GLES20.glGetUniformLocation(amplifierProgram, "uViewAspect")
    }

    private fun initOesTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st

        surfaceView.post {
            onSurfaceTextureReady(st)
        }
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        ringBuffer.release()
        // Delete freeze-frame texture
        if (freezeTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(freezeTextureId), 0)
            freezeTextureId = 0
        }
        if (recordEglSurface != EGL14.EGL_NO_SURFACE) {
            val eglDisplay = EGL14.eglGetCurrentDisplay()
            EGL14.eglDestroySurface(eglDisplay, recordEglSurface)
            recordEglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private fun initFreezeTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        freezeTextureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, freezeTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        if (cameraWidth > 0 && cameraHeight > 0) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                cameraWidth, cameraHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        Log.i(TAG, "Freeze-frame texture initialized: id=$freezeTextureId")
    }

    private fun updateFreezeTextureSize(width: Int, height: Int) {
        if (freezeTextureId != 0 && width > 0 && height > 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, freezeTextureId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            Log.i(TAG, "Updated freeze texture size to ${width}x${height}")
        }
    }

    private fun copyCurrentFrameToFreezeTexture() {
        val currentFbo = ringBuffer.getCurrentFboId()
        if (currentFbo != 0 && freezeTextureId != 0 && ringBuffer.bufferWidth > 0 && ringBuffer.bufferHeight > 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, currentFbo)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, freezeTextureId)
            GLES20.glCopyTexSubImage2D(
                GLES20.GL_TEXTURE_2D, 0,
                0, 0, 0, 0,
                ringBuffer.bufferWidth, ringBuffer.bufferHeight
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            Log.i(TAG, "Direct GPU copy of current frame FBO $currentFbo to freeze texture $freezeTextureId")
        }
    }

    /**
     * Requests the GL thread to capture the current camera frame FBO directly on the GPU
     * into the static freeze-frame reference texture.
     */
    fun captureFreezeFrame(callback: (() -> Unit)? = null) {
        pendingFreezeCaptureCallback = callback
        shouldCaptureFreezeFrame = true
        surfaceView.requestRender()
        Log.i(TAG, "Freeze-frame capture scheduled on GPU")
    }

    /**
     * Clears the freeze-frame reference so the ring buffer delayed slot is used again.
     */
    fun clearFreezeFrame() {
        hasFreezeFrame = false
        shouldCaptureFreezeFrame = false
        pendingFreezeCaptureCallback = null
        surfaceView.requestRender()
        Log.i(TAG, "Freeze-frame reference cleared")
    }
}
