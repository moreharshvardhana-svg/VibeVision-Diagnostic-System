package com.vibevision.app.model

/**
 * Real-time diagnostic metrics and telemetry state.
 */
data class DiagnosticState(
    val fps: Float = 0.0f,
    val vibrationIndex: Float = 0.0f,
    val isAnomaly: Boolean = false,
    val healthStatus: String = "INITIALIZING...",
    val gain: Float = 10.0f,
    val delayFrames: Int = 2,
    val displayMode: DisplayMode = DisplayMode.POSY_GRAY,
    val isTorchAvailable: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isFrontCamera: Boolean = false,
    val showReticle: Boolean = true,
    val baselineOffset: Float = 0.0f
) {
    val effectiveVibrationIndex: Float
        get() = (vibrationIndex - baselineOffset).coerceAtLeast(0.0f)

    companion object {
        const val STATUS_NORMAL = "NORMAL / STABLE"
        const val STATUS_ANOMALY = "VIBRATION ANOMALY DETECTED"
        const val ANOMALY_THRESHOLD = 18.0f
    }
}
