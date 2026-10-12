package com.vibevision.app.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.vibevision.app.model.DiagnosticState
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time differential signal processing analyzer calculating the Vibration Intensity Index (VII)
 * from spatial luminance differences across temporal frame buffers.
 */
class VibrationAnalyzer(
    private val onDiagnosticUpdate: (vibrationIndex: Float, isAnomaly: Boolean, status: String) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val GRID_ROWS = 32
        private const val GRID_COLS = 32
        private const val SAMPLE_COUNT = GRID_ROWS * GRID_COLS
        private const val HISTORY_CAPACITY = 12
    }

    @Volatile
    var gain: Float = 8.0f // Default 8.0x

    @Volatile
    var frameDelay: Int = 2 // Default 2 frames

    @Volatile
    var baselineOffset: Float = 0.0f

    // Circular history buffer for sample points
    private val history = Array(HISTORY_CAPACITY) { FloatArray(SAMPLE_COUNT) }
    private var historyIndex = 0
    private var totalFramesProcessed = 0L

    // Smoothed vibration metric
    private var smoothedIntensity = 0.0f

    // Current sample buffer reuse
    private val currentSamples = FloatArray(SAMPLE_COUNT)

    override fun analyze(image: ImageProxy) {
        try {
            val planes = image.planes
            if (planes.isEmpty()) return

            val yBuffer = planes[0].buffer
            val rowStride = planes[0].rowStride
            val pixelStride = planes[0].pixelStride
            val width = image.width
            val height = image.height

            // Sample central 80% region of interest for mechanical vibration
            val xMargin = (width * 0.1f).toInt()
            val yMargin = (height * 0.1f).toInt()
            val sampleWidth = width - 2 * xMargin
            val sampleHeight = height - 2 * yMargin

            val stepX = sampleWidth / GRID_COLS
            val stepY = sampleHeight / GRID_ROWS

            var sampleIdx = 0
            for (r in 0 until GRID_ROWS) {
                val y = (yMargin + r * stepY).coerceIn(0, height - 1)
                val rowOffset = y * rowStride

                for (c in 0 until GRID_COLS) {
                    val x = (xMargin + c * stepX).coerceIn(0, width - 1)
                    val pixelOffset = rowOffset + x * pixelStride

                    if (pixelOffset < yBuffer.limit()) {
                        val luma = yBuffer.get(pixelOffset).toInt() and 0xFF
                        currentSamples[sampleIdx] = luma.toFloat()
                    } else {
                        currentSamples[sampleIdx] = 0.0f
                    }
                    sampleIdx++
                }
            }

            // Copy to circular history buffer
            val writeSlot = (historyIndex + 1) % HISTORY_CAPACITY
            System.arraycopy(currentSamples, 0, history[writeSlot], 0, SAMPLE_COUNT)
            historyIndex = writeSlot
            totalFramesProcessed++

            // Calculate differences against delayed null frame
            val clampedDelay = frameDelay.coerceIn(1, HISTORY_CAPACITY - 1)
            val delayedSlot = if (totalFramesProcessed < clampedDelay) {
                writeSlot
            } else {
                (writeIndexWithOffset(-clampedDelay) + HISTORY_CAPACITY) % HISTORY_CAPACITY
            }

            val delayedSamples = history[delayedSlot]

            var sumSqDiff = 0.0
            var sumAbsDiff = 0.0
            for (i in 0 until SAMPLE_COUNT) {
                val diff = abs(currentSamples[i] - delayedSamples[i])
                sumAbsDiff += diff
                sumSqDiff += (diff * diff)
            }

            val rmsDiff = sqrt(sumSqDiff / SAMPLE_COUNT).toFloat()
            val madDiff = (sumAbsDiff / SAMPLE_COUNT).toFloat()

            // Sub-Pixel Vibration Intensity Index (0.0 to 100.0)
            val baseDiff = (rmsDiff * 0.7f + madDiff * 0.3f)
            val gainScaling = (gain / 5.0f).coerceAtLeast(0.5f)
            val rawIndex = (baseDiff * gainScaling).coerceIn(0.0f, 100.0f)

            // Low-pass exponential moving average for steady instrumentation telemetry
            smoothedIntensity = smoothedIntensity * 0.75f + rawIndex * 0.25f

            // Differential null baseline subtraction
            val effectiveIndex = (smoothedIntensity - baselineOffset).coerceAtLeast(0.0f)

            val isAnomaly = effectiveIndex >= DiagnosticState.ANOMALY_THRESHOLD
            val status = if (isAnomaly) {
                DiagnosticState.STATUS_ANOMALY
            } else {
                DiagnosticState.STATUS_NORMAL
            }

            onDiagnosticUpdate(effectiveIndex, isAnomaly, status)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun writeIndexWithOffset(offset: Int): Int {
        return (historyIndex + offset + HISTORY_CAPACITY) % HISTORY_CAPACITY
    }

    fun calibrateBaseline() {
        baselineOffset = smoothedIntensity
    }

    fun resetBaseline() {
        baselineOffset = 0.0f
    }
}
