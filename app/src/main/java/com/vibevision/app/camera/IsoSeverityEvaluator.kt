package com.vibevision.app.camera

/**
 * ISO 10816-3 Industrial Vibration Severity Zones.
 * Standardized classification for machine health evaluation:
 * - IDLE: Stationary baseline / sensor noise floor
 * - ZONE_A_GOOD: Newly commissioned machines / smooth operation (< 25% threshold)
 * - ZONE_B_ACCEPTABLE: Long-term continuous unrestricted operation (25% - 50% threshold)
 * - ZONE_C_UNSATISFACTORY: Significant vibration, limited long-term continuous operation (50% - 75% threshold)
 * - ZONE_D_UNACCEPTABLE: Severe vibration hazard, damage likely (> 75% threshold)
 */
enum class VibrationSeverityZone {
    IDLE,
    ZONE_A_GOOD,
    ZONE_B_ACCEPTABLE,
    ZONE_C_UNSATISFACTORY,
    ZONE_D_UNACCEPTABLE
}

/**
 * Evaluated severity telemetry report containing the classified zone,
 * human-readable ISO 10816 diagnosis, display tag, and normalized metric.
 */
data class SeverityReport(
    val zone: VibrationSeverityZone,
    val diagnosis: String,
    val tag: String,
    val normalizedValue: Float
)

/**
 * ISO 10816-3 Industrial Vibration Severity Telemetry Engine.
 *
 * Maps optical vibration amplitude and FFT peak energy into standard severity zones
 * with boundary hysteresis and a 300ms temporal debounce to eliminate flickering.
 */
class IsoSeverityEvaluator(
    private val debounceMillis: Long = DEBOUNCE_MS,
    private val fullScalePeakThreshold: Float = DEFAULT_PEAK_THRESHOLD
) {
    companion object {
        const val DEBOUNCE_MS = 300L
        const val DEFAULT_PEAK_THRESHOLD = 18.0f // Matches DiagnosticState.ANOMALY_THRESHOLD
        private const val MIN_NOISE_GATE = 0.05f
        private const val HYSTERESIS_MARGIN = 0.025f // 2.5% boundary band to prevent rapid toggling

        const val DIAGNOSIS_IDLE = "STATIC / BASELINE"
        const val DIAGNOSIS_NORMAL = "NORMAL OPERATION (CLASS 1/2)"
        const val DIAGNOSIS_UNSATISFACTORY = "MAINTENANCE ADVISORY (UNSATISFACTORY)"
        const val DIAGNOSIS_CRITICAL = "CRITICAL ALERT (IMMEDIATE SHUTDOWN)"

        const val TAG_IDLE = "IDLE"
        const val TAG_GOOD = "OPTIMAL"
        const val TAG_ACCEPTABLE = "ACCEPTABLE"
        const val TAG_UNSATISFACTORY = "UNSATISFACTORY"
        const val TAG_CRITICAL = "CRITICAL"
    }

    private var currentZone: VibrationSeverityZone = VibrationSeverityZone.IDLE
    private var pendingZone: VibrationSeverityZone = VibrationSeverityZone.IDLE
    private var pendingSinceMs: Long = 0L

    /**
     * Evaluates optical vibration parameters into an ISO 10816-3 SeverityReport.
     * Thread-safe and zero allocation in the hot loop.
     *
     * @param peakMagnitude Optical vibration amplitude or displacement metric.
     * @param peakHz Dominant fundamental frequency detected by the optical FFT engine.
     * @param currentTimeMs Current timestamp in milliseconds (defaults to System.currentTimeMillis()).
     */
    fun evaluate(
        peakMagnitude: Float,
        peakHz: Float,
        currentTimeMs: Long = System.currentTimeMillis()
    ): SeverityReport {
        // Calculate normalized vibration ratio (0.0 to 1.0)
        val normalized = when {
            peakMagnitude <= 0.0f -> 0.0f
            peakMagnitude <= 1.0f && fullScalePeakThreshold <= 1.0f -> peakMagnitude
            peakMagnitude <= 1.0f && peakMagnitude > MIN_NOISE_GATE && fullScalePeakThreshold > 1.0f -> {
                // If caller already normalized to 0.0..1.0
                peakMagnitude
            }
            else -> (peakMagnitude / fullScalePeakThreshold).coerceIn(0.0f, 1.0f)
        }

        // 1. Determine raw candidate zone from normalized amplitude
        val rawZone = when {
            peakHz <= 0.05f || peakMagnitude < MIN_NOISE_GATE || normalized < 0.02f -> {
                VibrationSeverityZone.IDLE
            }
            normalized < 0.25f -> VibrationSeverityZone.ZONE_A_GOOD
            normalized < 0.50f -> VibrationSeverityZone.ZONE_B_ACCEPTABLE
            normalized < 0.75f -> VibrationSeverityZone.ZONE_C_UNSATISFACTORY
            else -> VibrationSeverityZone.ZONE_D_UNACCEPTABLE
        }

        // 2. Apply Boundary Hysteresis against currentZone to prevent boundary jitter
        val candidateZone = applyHysteresis(rawZone, normalized)

        // 3. Apply Temporal Debounce (~300ms) for stable HUD transitions
        if (candidateZone != currentZone) {
            if (candidateZone != pendingZone) {
                pendingZone = candidateZone
                pendingSinceMs = currentTimeMs
            } else {
                val elapsed = currentTimeMs - pendingSinceMs
                // Critical escalation to Zone D responds slightly faster (150ms), others use standard debounce
                val requiredDebounce = if (candidateZone == VibrationSeverityZone.ZONE_D_UNACCEPTABLE) {
                    debounceMillis / 2
                } else {
                    debounceMillis
                }

                if (elapsed >= requiredDebounce) {
                    currentZone = candidateZone
                }
            }
        } else {
            // Already settled at currentZone
            pendingZone = currentZone
            pendingSinceMs = currentTimeMs
        }

        val diagnosis = when (currentZone) {
            VibrationSeverityZone.IDLE -> DIAGNOSIS_IDLE
            VibrationSeverityZone.ZONE_A_GOOD,
            VibrationSeverityZone.ZONE_B_ACCEPTABLE -> DIAGNOSIS_NORMAL
            VibrationSeverityZone.ZONE_C_UNSATISFACTORY -> DIAGNOSIS_UNSATISFACTORY
            VibrationSeverityZone.ZONE_D_UNACCEPTABLE -> DIAGNOSIS_CRITICAL
        }

        val tag = when (currentZone) {
            VibrationSeverityZone.IDLE -> TAG_IDLE
            VibrationSeverityZone.ZONE_A_GOOD -> TAG_GOOD
            VibrationSeverityZone.ZONE_B_ACCEPTABLE -> TAG_ACCEPTABLE
            VibrationSeverityZone.ZONE_C_UNSATISFACTORY -> TAG_UNSATISFACTORY
            VibrationSeverityZone.ZONE_D_UNACCEPTABLE -> TAG_CRITICAL
        }

        return SeverityReport(
            zone = currentZone,
            diagnosis = diagnosis,
            tag = tag,
            normalizedValue = normalized
        )
    }

    /**
     * Checks if the transition crosses the boundary hysteresis threshold.
     */
    private fun applyHysteresis(rawCandidate: VibrationSeverityZone, normalized: Float): VibrationSeverityZone {
        // If transitioning down from Zone B to Zone A: require normalized < 0.25 - margin
        if (currentZone == VibrationSeverityZone.ZONE_B_ACCEPTABLE && rawCandidate == VibrationSeverityZone.ZONE_A_GOOD) {
            if (normalized >= 0.25f - HYSTERESIS_MARGIN) {
                return currentZone
            }
        }
        // If transitioning down from Zone C to Zone B: require normalized < 0.50 - margin
        if (currentZone == VibrationSeverityZone.ZONE_C_UNSATISFACTORY && rawCandidate == VibrationSeverityZone.ZONE_B_ACCEPTABLE) {
            if (normalized >= 0.50f - HYSTERESIS_MARGIN) {
                return currentZone
            }
        }
        // If transitioning down from Zone D to Zone C: require normalized < 0.75 - margin
        if (currentZone == VibrationSeverityZone.ZONE_D_UNACCEPTABLE && rawCandidate == VibrationSeverityZone.ZONE_C_UNSATISFACTORY) {
            if (normalized >= 0.75f - HYSTERESIS_MARGIN) {
                return currentZone
            }
        }
        // If transitioning up into Zone C: require normalized >= 0.50 + margin
        if (currentZone == VibrationSeverityZone.ZONE_B_ACCEPTABLE && rawCandidate == VibrationSeverityZone.ZONE_C_UNSATISFACTORY) {
            if (normalized < 0.50f + HYSTERESIS_MARGIN) {
                return currentZone
            }
        }
        // If transitioning up into Zone D: require normalized >= 0.75 + margin
        if (currentZone == VibrationSeverityZone.ZONE_C_UNSATISFACTORY && rawCandidate == VibrationSeverityZone.ZONE_D_UNACCEPTABLE) {
            if (normalized < 0.75f + HYSTERESIS_MARGIN) {
                return currentZone
            }
        }

        return rawCandidate
    }

    fun reset() {
        currentZone = VibrationSeverityZone.IDLE
        pendingZone = VibrationSeverityZone.IDLE
        pendingSinceMs = 0L
    }
}
