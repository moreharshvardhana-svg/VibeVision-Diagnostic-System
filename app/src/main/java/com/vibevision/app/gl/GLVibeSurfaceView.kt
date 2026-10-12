package com.vibevision.app.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.vibevision.app.model.DisplayMode

/**
 * Custom GLSurfaceView configured with OpenGL ES 2.0 and event-driven rendering.
 */
class GLVibeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var vibeRenderer: VibeRenderer? = null

    init {
        setEGLContextClientVersion(2)
        // Preserve EGL context on pause for instant resume
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

    fun releaseRenderer() {
        vibeRenderer?.release()
    }
}
