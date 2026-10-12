package com.vibevision.app.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.vibevision.app.model.DisplayMode
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Custom OpenGL ES 2.0 Renderer executing real-time Posy motion extraction,
 * FBO texture ring buffering, and Google Turbo colormap heatmap rendering.
 */
class VibeRenderer(
    private val surfaceView: GLSurfaceView,
    private val onFpsUpdated: (Float) -> Unit,
    private val onSurfaceTextureReady: (surfaceTexture: SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "VibeRenderer"
        private const val RING_BUFFER_CAPACITY = 8
    }

    // Dynamic processing parameters (thread-safe)
    @Volatile
    var gain: Float = 10.0f
        set(value) {
            field = value.coerceIn(1.0f, 25.0f)
        }

    @Volatile
    var frameDelay: Int = 2
        set(value) {
            field = value.coerceIn(1, 5)
        }

    @Volatile
    var displayMode: DisplayMode = DisplayMode.POSY_GRAY

    // Viewport dimensions
    private var viewportWidth = 1920
    private var viewportHeight = 1080

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

    private var posyProgram = 0
    private var posyPositionLoc = -1
    private var posyTexCoordLoc = -1
    private var posyCurrentLoc = -1
    private var posyDelayedLoc = -1
    private var posyGainLoc = -1
    private var posyModeLoc = -1

    // Quad geometry buffers
    private val quadVertexBuffer: FloatBuffer = ShaderUtils.getQuadVertexBuffer()
    private val quadTexCoordBuffer: FloatBuffer = ShaderUtils.getQuadTexCoordBuffer()
    private val quadTexCoordFlippedBuffer: FloatBuffer = ShaderUtils.getQuadTexCoordFlippedBuffer()

    // FPS estimation
    private var lastFrameTimeNs = 0L
    private var smoothedFps = 60.0f
    private var fpsUpdateCounter = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: Initializing OpenGL ES 2.0 context")

        GLES20.glClearColor(0.043f, 0.055f, 0.078f, 1.0f) // #0B0E14
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        initShaders()
        initOesTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: ${width}x${height}")
        viewportWidth = width
        viewportHeight = height

        // Initialize FBO ring buffer textures to match incoming frame resolution
        ringBuffer.setup(width, height)
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
            // Nothing to render yet
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

            // Setup vertex coordinates
            quadVertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(oesPositionLoc)
            GLES20.glVertexAttribPointer(oesPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)

            // Setup texture UV coordinates
            quadTexCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(oesTexCoordLoc)
            GLES20.glVertexAttribPointer(oesTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)

            // Pass camera transformation matrix
            GLES20.glUniformMatrix4fv(oesMatrixLoc, 1, false, stMatrix, 0)

            // Bind Camera OES texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(oesTextureLoc, 0)

            // Draw fullscreen quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(oesPositionLoc)
            GLES20.glDisableVertexAttribArray(oesTexCoordLoc)
        }

        // -------------------------------------------------------------
        // PASS 2: Posy Motion Extraction / Heatmap to Screen Framebuffer
        // -------------------------------------------------------------
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(posyProgram)

        // Setup vertex geometry
        quadVertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posyPositionLoc)
        GLES20.glVertexAttribPointer(posyPositionLoc, 2, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)

        // Setup UV coordinates (standard quad)
        quadTexCoordBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posyTexCoordLoc)
        GLES20.glVertexAttribPointer(posyTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)

        // Bind Current Frame (unit 0)
        val currentTexId = ringBuffer.getCurrentTextureId()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTexId)
        GLES20.glUniform1i(posyCurrentLoc, 0)

        // Bind Delayed Frame (unit 1)
        val delayedTexId = ringBuffer.getDelayedTextureId(frameDelay)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, delayedTexId)
        GLES20.glUniform1i(posyDelayedLoc, 1)

        // Pass Uniforms
        GLES20.glUniform1f(posyGainLoc, gain)
        GLES20.glUniform1i(posyModeLoc, displayMode.shaderModeId)

        // Draw fullscreen quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posyPositionLoc)
        GLES20.glDisableVertexAttribArray(posyTexCoordLoc)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(frameLock) {
            frameAvailable = true
        }
        surfaceView.requestRender()
    }

    /**
     * Called when Camera resolution is negotiated by CameraX.
     */
    fun setCameraPreviewSize(width: Int, height: Int) {
        surfaceView.queueEvent {
            Log.i(TAG, "Configuring FBO Ring Buffer for camera size: ${width}x${height}")
            ringBuffer.setup(width, height)
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

        // 2. Posy Motion Extraction & Diagnostic program
        posyProgram = ShaderUtils.createProgram(
            ShaderUtils.DISPLAY_VERTEX_SHADER,
            ShaderUtils.POSY_FRAGMENT_SHADER
        )
        posyPositionLoc = GLES20.glGetAttribLocation(posyProgram, "aPosition")
        posyTexCoordLoc = GLES20.glGetAttribLocation(posyProgram, "aTexCoord")
        posyCurrentLoc = GLES20.glGetUniformLocation(posyProgram, "uCurrentFrame")
        posyDelayedLoc = GLES20.glGetUniformLocation(posyProgram, "uDelayedFrame")
        posyGainLoc = GLES20.glGetUniformLocation(posyProgram, "uGain")
        posyModeLoc = GLES20.glGetUniformLocation(posyProgram, "uDisplayMode")
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

        // Notify camera provider on main thread
        surfaceView.post {
            onSurfaceTextureReady(st)
        }
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        ringBuffer.release()
    }
}
