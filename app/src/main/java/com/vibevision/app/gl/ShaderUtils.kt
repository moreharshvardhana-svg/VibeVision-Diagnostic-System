package com.vibevision.app.gl

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 2.0 Shader and Buffer utilities for the Differential Null Baseline Engine
 * and Sub-Pixel Motion Amplifier.
 */
object ShaderUtils {
    private const val TAG = "VibeShaderUtils"

    // Fullscreen quad vertices (-1 to 1) for OpenGL viewport
    private val QUAD_VERTICES = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f
    )

    // Standard Texture UV coordinates (0 to 1)
    private val QUAD_TEX_COORDS = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    // Inverted Y UV coordinates for FBO read-out when needed
    private val QUAD_TEX_COORDS_FLIPPED = floatArrayOf(
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f
    )

    fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    fun getQuadVertexBuffer(): FloatBuffer = createFloatBuffer(QUAD_VERTICES)
    fun getQuadTexCoordBuffer(): FloatBuffer = createFloatBuffer(QUAD_TEX_COORDS)
    fun getQuadTexCoordFlippedBuffer(): FloatBuffer = createFloatBuffer(QUAD_TEX_COORDS_FLIPPED)

    /**
     * Compiles a GLSL shader.
     */
    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Error creating shader of type $type")
        }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val infoLog = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader $type:\n$infoLog\nSource:\n$shaderCode")
        }

        return shader
    }

    /**
     * Links a vertex and fragment shader into a program.
     */
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Could not create GL program")
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val infoLog = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Could not link program:\n$infoLog")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError 0x${Integer.toHexString(error)}")
        }
    }

    // --- Shaders ---

    /**
     * Vertex shader for capturing CameraX SurfaceTexture (OES) to standard FBO.
     */
    const val OES_VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uSTMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uSTMatrix * aTexCoord).xy;
        }
    """

    /**
     * Fragment shader for capturing CameraX SurfaceTexture (OES).
     */
    const val OES_FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uOesTexture;
        void main() {
            gl_FragColor = texture2D(uOesTexture, vTexCoord);
        }
    """

    /**
     * Vertex shader for final display quad rendering.
     */
    const val DISPLAY_VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    /**
     * Sub-Pixel Motion Amplifier fragment shader supporting 4 industrial visualization modes:
     * Mode 0: Differential Null Baseline (50% neutral gray baseline difference)
     * Mode 1: Spectral Turbo Heatmap (Color-mapped absolute motion gradients)
     * Mode 2: Dynamic Edge Accent (Darkened raw background with glowing motion fringes)
     * Mode 3: Raw Optical Feed (Unprocessed inspection view)
     */
    const val MOTION_AMPLIFIER_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTexCoord;

        uniform sampler2D uCurrentFrame;
        uniform sampler2D uDelayedFrame;
        uniform float uGain;
        uniform int uDisplayMode;

        // Spectral Turbo colormap polynomial approximation
        vec3 turboColormap(float x) {
            x = clamp(x, 0.0, 1.0);
            const vec4 kRedVec4   = vec4(0.13572138, 4.61539260, -42.66032258, 132.13108234);
            const vec4 kGreenVec4 = vec4(0.09140261, 2.19418839, 4.84296658, -14.18503333);
            const vec4 kBlueVec4  = vec4(0.10667330, 12.64194608, -60.58204836, 110.36276771);
            const vec2 kRedVec2   = vec2(-152.94239396, 59.28637943);
            const vec2 kGreenVec2 = vec2(4.27729857, 2.82956604);
            const vec2 kBlueVec2  = vec2(-89.90310912, 27.34824973);

            vec4 v4 = vec4(1.0, x, x * x, x * x * x);
            vec2 v2 = v4.zw * v4.z;

            return vec3(
                dot(v4, kRedVec4)   + dot(v2, kRedVec2),
                dot(v4, kGreenVec4) + dot(v2, kGreenVec2),
                dot(v4, kBlueVec4)  + dot(v2, kBlueVec2)
            );
        }

        void main() {
            vec4 current = texture2D(uCurrentFrame, vTexCoord);

            if (uDisplayMode == 3) {
                // Mode 3: Raw Optical Feed
                gl_FragColor = current;
                return;
            }

            vec4 delayed = texture2D(uDelayedFrame, vTexCoord);

            if (uDisplayMode == 0) {
                // Mode 0: Differential Null Baseline (50% neutral gray baseline difference)
                vec3 diff = (current.rgb - delayed.rgb) * uGain + vec3(0.5);
                gl_FragColor = vec4(clamp(diff, 0.0, 1.0), 1.0);
            } else if (uDisplayMode == 1) {
                // Mode 1: Spectral Turbo Heatmap (Color-mapped absolute motion gradients)
                vec3 absDiff = abs(current.rgb - delayed.rgb) * uGain;
                float motionMag = dot(absDiff, vec3(0.299, 0.587, 0.114));
                motionMag = clamp(motionMag, 0.0, 1.0);

                vec3 heatColor = turboColormap(motionMag);
                float ambientLuma = dot(current.rgb, vec3(0.299, 0.587, 0.114)) * 0.15;
                vec3 finalColor = heatColor + vec3(ambientLuma);
                gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0), 1.0);
            } else {
                // Mode 2: Dynamic Edge Accent (Darkened raw background with glowing motion fringes)
                vec3 absDiff = abs(current.rgb - delayed.rgb) * uGain;
                float motionMag = dot(absDiff, vec3(0.299, 0.587, 0.114));
                // High-luminescence electric cyan fringe glow on moving/vibrating edges
                vec3 fringeColor = vec3(0.0, 0.898, 1.0) * clamp(motionMag * 2.5, 0.0, 1.0);
                // Attenuated raw background for spatial orientation
                vec3 composite = current.rgb * 0.25 + fringeColor;
                gl_FragColor = vec4(clamp(composite, 0.0, 1.0), 1.0);
            }
        }
    """
}
