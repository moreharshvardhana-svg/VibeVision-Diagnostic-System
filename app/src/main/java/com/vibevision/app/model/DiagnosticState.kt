package com.vibevision.app.model

/**
 * Real-time diagnostic metrics, telemetry, and engineering configuration state.
 */
data class DiagnosticState(
    val fps: Float = 0.0f,
    val vibrationIndex: Float = 0.0f,
    val isAnomaly: Boolean = false,
    val healthStatus: String = "INITIALIZING...",
    val gain: Float = 8.0f, // Default 8.0x per industrial specification
    val delayFrames: Int = 2, // Default 2 frames
    val displayMode: DisplayMode = DisplayMode.DIFFERENTIAL_NULL,
    val target60Fps: Boolean = true,
    val isTorchAvailable: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val recordingDurationSeconds: Long = 0L,
    val showReticle: Boolean = false,
    val baselineOffset: Float = 0.0f,
    val isSettingsOpen: Boolean = false
) {
    val effectiveVibrationIndex: Float
        get() = (vibrationIndex - baselineOffset).coerceAtLeast(0.0f)

    val formattedRecordingTime: String
        get() {
            val minutes = recordingDurationSeconds / 60
            val seconds = recordingDurationSeconds % 60
            return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }

    companion object {
        const val STATUS_NORMAL = "NORMAL / STABLE"
        const val STATUS_ANOMALY = "VIBRATION ANOMALY DETECTED"
        const val ANOMALY_THRESHOLD = 18.0f
        const val MIN_GAIN = 1.0f
        const val MAX_GAIN = 30.0f
        const val MIN_DELAY = 1
        const val MAX_DELAY = 8
    }
}
