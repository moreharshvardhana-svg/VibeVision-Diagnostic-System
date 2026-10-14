package com.vibevision.app.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import com.vibevision.app.media.VideoRecorder
import com.vibevision.app.model.DisplayMode

/**
 * Custom GLSurfaceView configured with OpenGL ES 2.0 and event-driven rendering
 * for the Differential Null Baseline Engine.
 */
class GLVibeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var vibeRenderer: VibeRenderer? = null

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
    }

    fun initRenderer(
        onFpsUpdated: (Float) -> Unit,
        onSurfaceTextureReady: (SurfaceTexture) -> Unit
    ) {
        val renderer = VibeRenderer(
            surfaceView = this,
            onFpsUpdated = onFpsUpdated,
            onSurfaceTextureReady = onSurfaceTextureReady
        )
        this.vibeRenderer = renderer
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    val viewportWidth: Int
        get() = vibeRenderer?.currentViewportWidth ?: width

    val viewportHeight: Int
        get() = vibeRenderer?.currentViewportHeight ?: height

    fun setGain(gain: Float) {
        vibeRenderer?.gain = gain
        requestRender()
    }

    fun setFrameDelay(delay: Int) {
        vibeRenderer?.frameDelay = delay
        requestRender()
    }

    fun setDisplayMode(mode: DisplayMode) {
        vibeRenderer?.displayMode = mode
        requestRender()
    }

    fun notifyCameraResolution(width: Int, height: Int) {
        vibeRenderer?.setCameraPreviewSize(width, height)
    }

    fun requestSnapshot(callback: (Bitmap) -> Unit) {
        vibeRenderer?.requestSnapshot(callback)
    }

    fun startRecording(recorder: VideoRecorder, surface: Surface, width: Int, height: Int) {
        vibeRenderer?.startRecording(recorder, surface, width, height)
    }

    fun stopRecording() {
        vibeRenderer?.stopRecording()
    }

    fun releaseRenderer() {
        vibeRenderer?.release()
    }

    /**
     * Captures the current live frame directly on the GPU and pins it as the static Freeze-Frame reference.
     * Direct GPU texture copy ensures zero pixel format, flip, or aspect ratio mismatches.
     */
    fun captureAndSetFreezeFrame(callback: (() -> Unit)? = null) {
        vibeRenderer?.captureFreezeFrame(callback)
    }

    /**
     * Clears the freeze-frame reference, restoring rolling ring-buffer comparisons.
     */
    fun clearFreezeFrame() {
        vibeRenderer?.clearFreezeFrame()
    }
}
