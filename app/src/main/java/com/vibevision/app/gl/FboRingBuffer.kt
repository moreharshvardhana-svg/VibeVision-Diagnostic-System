package com.vibevision.app.gl

import android.opengl.GLES20
import android.util.Log

/**
 * Manages a ring buffer of OpenGL ES 2.0 Framebuffer Objects (FBOs) and 2D textures
 * to store historical frames for temporal differential null baseline displacement extraction.
 */
class FboRingBuffer(private val capacity: Int = 12) {

    companion object {
        private const val TAG = "FboRingBuffer"
    }

    private var width = 0
    private var height = 0
    private var fboIds: IntArray = IntArray(capacity)
    private var textureIds: IntArray = IntArray(capacity)

    private var writeIndex = 0
    private var frameCount = 0L
    private var isInitialized = false

    val isReady: Boolean
        get() = isInitialized && width > 0 && height > 0

    val bufferWidth: Int get() = width
    val bufferHeight: Int get() = height

    fun setup(newWidth: Int, newHeight: Int) {
        if (isInitialized && newWidth == width && newHeight == height) {
            return
        }

        release()

        width = newWidth
        height = newHeight

        if (width <= 0 || height <= 0) return

        GLES20.glGenTextures(capacity, textureIds, 0)
        GLES20.glGenFramebuffers(capacity, fboIds, 0)

        for (i in 0 until capacity) {
            // Allocate texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // Attach texture to FBO
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textureIds[i],
                0
            )

            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "FBO $i incomplete: status 0x${Integer.toHexString(status)}")
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        writeIndex = 0
        frameCount = 0L
        isInitialized = true
        Log.i(TAG, "Initialized ring buffer of $capacity FBOs at ${width}x${height}")
    }

    /**
     * Advances to the next write slot in the ring buffer.
     * Returns the target FBO ID for rendering the new frame.
     */
    fun advanceForNextWrite(): Int {
        if (!isInitialized) return 0
        writeIndex = (writeIndex + 1) % capacity
        frameCount++
        return fboIds[writeIndex]
    }

    val totalFrameCount: Long get() = frameCount

    /**
     * Returns the current (most recent) frame's GL FBO ID.
     */
    fun getCurrentFboId(): Int {
        if (!isInitialized) return 0
        return fboIds[writeIndex]
    }

    /**
     * Returns the current (most recent) frame's GL_TEXTURE_2D ID.
     */
    fun getCurrentTextureId(): Int {
        if (!isInitialized) return 0
        return textureIds[writeIndex]
    }

    /**
     * Returns the delayed frame's GL_TEXTURE_2D ID based on requested frame delay (Δt).
     */
    fun getDelayedTextureId(delay: Int): Int {
        if (!isInitialized) return 0
        val clampedDelay = delay.coerceIn(1, capacity - 1)
        val delayedIndex = if (frameCount < clampedDelay) {
            // During startup, fall back to current frame
            writeIndex
        } else {
            (writeIndex - clampedDelay + capacity) % capacity
        }
        return textureIds[delayedIndex]
    }

    fun release() {
        if (isInitialized) {
            GLES20.glDeleteFramebuffers(capacity, fboIds, 0)
            GLES20.glDeleteTextures(capacity, textureIds, 0)
            isInitialized = false
            writeIndex = 0
            frameCount = 0L
            Log.i(TAG, "Released FBO ring buffer")
        }
    }
}
