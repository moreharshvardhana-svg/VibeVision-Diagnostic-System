package com.vibevision.app.model

import com.vibevision.app.camera.VibrationSeverityZone

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
    val isSettingsOpen: Boolean = false,
    val isFrozen: Boolean = false,
    val peakFrequencyHz: Float = 0.0f,
    val estimatedRpm: Float = 0.0f,
    val spectrumMagnitudes: FloatArray = FloatArray(32),
    val fftStatusTag: String = "IDLE (NO VIBRATION)",
    val isAliasDetected: Boolean = false,
    val severityZone: VibrationSeverityZone = VibrationSeverityZone.IDLE,
    val severityDiagnosis: String = "STATIC / BASELINE",
    val severityTag: String = "IDLE"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DiagnosticState
        if (fps != other.fps) return false
        if (vibrationIndex != other.vibrationIndex) return false
        if (isAnomaly != other.isAnomaly) return false
        if (healthStatus != other.healthStatus) return false
        if (gain != other.gain) return false
        if (delayFrames != other.delayFrames) return false
        if (displayMode != other.displayMode) return false
        if (target60Fps != other.target60Fps) return false
        if (isTorchAvailable != other.isTorchAvailable) return false
        if (isTorchEnabled != other.isTorchEnabled) return false
        if (isRecordingVideo != other.isRecordingVideo) return false
        if (recordingDurationSeconds != other.recordingDurationSeconds) return false
        if (showReticle != other.showReticle) return false
        if (baselineOffset != other.baselineOffset) return false
        if (isSettingsOpen != other.isSettingsOpen) return false
        if (isFrozen != other.isFrozen) return false
        if (peakFrequencyHz != other.peakFrequencyHz) return false
        if (estimatedRpm != other.estimatedRpm) return false
        if (!spectrumMagnitudes.contentEquals(other.spectrumMagnitudes)) return false
        if (fftStatusTag != other.fftStatusTag) return false
        if (isAliasDetected != other.isAliasDetected) return false
        if (severityZone != other.severityZone) return false
        if (severityDiagnosis != other.severityDiagnosis) return false
        if (severityTag != other.severityTag) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fps.hashCode()
        result = 31 * result + vibrationIndex.hashCode()
        result = 31 * result + isAnomaly.hashCode()
        result = 31 * result + healthStatus.hashCode()
        result = 31 * result + gain.hashCode()
        result = 31 * result + delayFrames
        result = 31 * result + displayMode.hashCode()
        result = 31 * result + target60Fps.hashCode()
        result = 31 * result + isTorchAvailable.hashCode()
        result = 31 * result + isTorchEnabled.hashCode()
        result = 31 * result + isRecordingVideo.hashCode()
        result = 31 * result + recordingDurationSeconds.hashCode()
        result = 31 * result + showReticle.hashCode()
        result = 31 * result + baselineOffset.hashCode()
        result = 31 * result + isSettingsOpen.hashCode()
        result = 31 * result + isFrozen.hashCode()
        result = 31 * result + peakFrequencyHz.hashCode()
        result = 31 * result + estimatedRpm.hashCode()
        result = 31 * result + spectrumMagnitudes.contentHashCode()
        result = 31 * result + fftStatusTag.hashCode()
        result = 31 * result + isAliasDetected.hashCode()
        result = 31 * result + severityZone.hashCode()
        result = 31 * result + severityDiagnosis.hashCode()
        result = 31 * result + severityTag.hashCode()
        return result
    }
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
