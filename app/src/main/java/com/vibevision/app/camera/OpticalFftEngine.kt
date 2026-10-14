package com.vibevision.app.camera

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-Device Optical Fast Fourier Transform (FFT) Frequency & RPM Analysis Engine.
 *
 * Implements:
 * - Thread-safe circular ring buffer with sliding window of N = 64 frames.
 * - Dynamic Noise Floor Gate: AC signal zero-mean centering, time-domain RMS calculation,
 *   spectral noise-floor SNR gating, and suppression of low-frequency sensor noise (Bins 1 & 2).
 * - In-place radix-2 Cooley-Tukey 1D FFT in pure Kotlin (zero external math dependencies).
 * - Pre-computed Hann window for spectral leakage suppression.
 * - DC bin (bin 0) suppression to eliminate static baseline offsets.
 * - Temporal Exponential Moving Average (EMA, alpha = 0.3) smoothing across analysis windows.
 * - Optical Stroboscopic / Aliasing Indication: Nyquist boundary monitoring and
 *   rapid harmonic fluctuation detection ("EST. FUNDAMENTAL", "NEAR NYQUIST", "ALIAS DETECTED").
 * - 32 positive frequency magnitude bins with exponential smoothing for live sparkline rendering.
 * - Zero memory allocations inside the frame processing callback.
 */
class OpticalFftEngine(
    val windowSize: Int = WINDOW_SIZE
) {
    companion object {
        const val WINDOW_SIZE = 64
        const val SPECTRUM_BINS = WINDOW_SIZE / 2 // 32 positive frequency bins

        const val TAG_IDLE = "IDLE (NO VIBRATION)"
        const val TAG_FUNDAMENTAL = "EST. FUNDAMENTAL"
        const val TAG_ALIAS = "ALIAS DETECTED"
        const val TAG_NYQUIST = "NEAR NYQUIST"

        private const val MIN_RMS_THRESHOLD = 0.05f
        private const val MIN_PEAK_MAGNITUDE = 0.08f
        private const val EMA_ALPHA = 0.3f
        private const val RECENT_HISTORY_SIZE = 4
    }

    init {
        require(windowSize > 0 && (windowSize and (windowSize - 1)) == 0) {
            "windowSize must be a power of 2 (e.g. 64)"
        }
    }

    private val numBits = Integer.numberOfTrailingZeros(windowSize)

    // Precomputed bit-reversal permutation indices
    private val bitReversedIndices = IntArray(windowSize) { i ->
        var rev = 0
        var temp = i
        for (b in 0 until numBits) {
            rev = (rev shl 1) or (temp and 1)
            temp = temp shr 1
        }
        rev
    }

    // Precomputed Hann window: w[n] = 0.5 * (1 - cos(2*pi*n / (N - 1)))
    val hannWindow = FloatArray(windowSize) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (windowSize - 1)))).toFloat()
    }

    // Precomputed Twiddle factor lookup tables for Cooley-Tukey stages
    private val cosTable = Array(numBits + 1) { stage ->
        if (stage == 0) FloatArray(0)
        else {
            val m = 1 shl stage
            FloatArray(m / 2) { j ->
                cos(-2.0 * Math.PI * j / m).toFloat()
            }
        }
    }

    private val sinTable = Array(numBits + 1) { stage ->
        if (stage == 0) FloatArray(0)
        else {
            val m = 1 shl stage
            FloatArray(m / 2) { j ->
                sin(-2.0 * Math.PI * j / m).toFloat()
            }
        }
    }

    // Thread-safe circular sample ring buffer (sliding window of N = 64 frames)
    private val ringBuffer = FloatArray(windowSize)
    private var writeIndex = 0
    private var totalSamplesRecorded = 0L
    private val bufferLock = Any()

    // Pre-allocated internal working buffers (Zero allocations in process)
    private val fftReal = FloatArray(windowSize)
    private val fftImag = FloatArray(windowSize)
    private val rawMagnitudes = FloatArray(SPECTRUM_BINS)

    // Recent peak bin circular buffer for alias fluctuation tracking (last 4 windows)
    private val recentPeakBins = IntArray(RECENT_HISTORY_SIZE)
    private var recentBinIndex = 0
    private var recentBinCount = 0

    // Smoothed RPM state for Temporal Exponential Moving Average (alpha = 0.3)
    private var smoothedRpm: Float = 0.0f

    // Output telemetry buffers (reused across calls)
    val smoothedMagnitudes = FloatArray(SPECTRUM_BINS)
    val snapshotMagnitudes = FloatArray(SPECTRUM_BINS)

    @Volatile
    var peakFrequencyHz: Float = 0.0f
        private set

    @Volatile
    var estimatedRpm: Float = 0.0f
        private set

    @Volatile
    var peakBinIndex: Int = 0
        private set

    @Volatile
    var signalRms: Float = 0.0f
        private set

    @Volatile
    var isIdle: Boolean = true
        private set

    @Volatile
    var isAliasDetected: Boolean = false
        private set

    @Volatile
    var isNearNyquist: Boolean = false
        private set

    @Volatile
    var statusTag: String = TAG_IDLE
        private set

    /**
     * Ingests a new motion-amplified / differential luma intensity scalar.
     * Thread-safe; called from the asynchronous background analysis dispatcher.
     * Zero allocations.
     */
    fun pushSample(intensity: Float) {
        synchronized(bufferLock) {
            ringBuffer[writeIndex] = intensity
            writeIndex = (writeIndex + 1) % windowSize
            totalSamplesRecorded++
        }
    }

    /**
     * Executes the complete FFT analysis pipeline:
     * 1. Window extraction & zero-mean centering (removes DC spectral leakage into Bin 1 & 2)
     * 2. Time-domain AC RMS calculation
     * 3. Hann windowing
     * 4. In-place Cooley-Tukey 1D FFT
     * 5. DC bin suppression & spectral noise-floor estimation
     * 6. Dynamic Noise Floor Gate & Bin 1/2 low-frequency sensor noise suppression
     * 7. Stroboscopic / Aliasing indication (Near-Nyquist & harmonic fluctuation)
     * 8. Temporal EMA RPM smoothing (alpha = 0.3)
     * 9. Spectrum magnitude normalization & smoothing for sparkline rendering
     *
     * Returns true if a full window of N = 64 frames was analyzed.
     * Zero allocations.
     */
    fun process(cameraFps: Float): Boolean {
        var mean = 0.0f
        var rms = 0.0f
        var maxAmp = 0.0f

        synchronized(bufferLock) {
            if (totalSamplesRecorded < windowSize) {
                return false
            }

            // Extract sliding window chronologically from oldest to newest
            var sum = 0.0f
            val head = writeIndex
            for (i in 0 until windowSize) {
                val sample = ringBuffer[(head + i) % windowSize]
                fftReal[i] = sample
                sum += sample
            }
            mean = sum / windowSize.toFloat()

            // Remove DC offset (zero-mean) before windowing to prevent DC leakage into Bins 1 & 2,
            // and compute time-domain AC RMS & max amplitude.
            var sumSqDiff = 0.0f
            for (i in 0 until windowSize) {
                val ac = fftReal[i] - mean
                val absAc = abs(ac)
                if (absAc > maxAmp) {
                    maxAmp = absAc
                }
                sumSqDiff += ac * ac

                // Apply precomputed Hann window to the zero-mean AC signal
                fftReal[i] = ac * hannWindow[i]
                fftImag[i] = 0.0f
            }
            rms = sqrt(sumSqDiff / windowSize.toFloat())
        }

        signalRms = rms

        // 1. Bit-reversal permutation (in-place)
        for (i in 0 until windowSize) {
            val j = bitReversedIndices[i]
            if (i < j) {
                val tempR = fftReal[i]
                fftReal[i] = fftReal[j]
                fftReal[j] = tempR

                val tempI = fftImag[i]
                fftImag[i] = fftImag[j]
                fftImag[j] = tempI
            }
        }

        // 2. Cooley-Tukey radix-2 butterfly stages (in-place)
        for (stage in 1..numBits) {
            val m = 1 shl stage
            val halfM = m shr 1
            val stageCos = cosTable[stage]
            val stageSin = sinTable[stage]

            var k = 0
            while (k < windowSize) {
                for (j in 0 until halfM) {
                    val wr = stageCos[j]
                    val wi = stageSin[j]

                    val idx1 = k + j
                    val idx2 = idx1 + halfM

                    val r2 = fftReal[idx2]
                    val i2 = fftImag[idx2]

                    // Complex multiply: (r2 + i*i2) * (wr + i*wi)
                    val tr = r2 * wr - i2 * wi
                    val ti = r2 * wi + i2 * wr

                    val uReal = fftReal[idx1]
                    val uImag = fftImag[idx1]

                    fftReal[idx1] = uReal + tr
                    fftImag[idx1] = uImag + ti

                    fftReal[idx2] = uReal - tr
                    fftImag[idx2] = uImag - ti
                }
                k += m
            }
        }

        // 3. Compute magnitudes for positive frequency bins (0 to 31)
        var maxMag = 0.0f
        var bestBin = 0
        var sumMag = 0.0f

        // Bin 0 is DC: explicitly zeroed
        rawMagnitudes[0] = 0.0f

        for (k in 1 until SPECTRUM_BINS) {
            val r = fftReal[k]
            val im = fftImag[k]
            val mag = sqrt(r * r + im * im)
            rawMagnitudes[k] = mag
            sumMag += mag

            if (mag > maxMag) {
                maxMag = mag
                bestBin = k
            }
        }

        val meanNoiseFloor = sumMag / (SPECTRUM_BINS - 1).toFloat()

        // 4. Dynamic Noise Floor Gate & Low-Frequency Sensor Noise Discrimination
        // When completely still, sensor noise & 1/f drift can slightly raise Bins 1 and 2 (~32 RPM).
        // Require strict SNR and AC RMS gating before identifying any valid mechanical peak.
        var validPeakFound = false

        if (rms >= MIN_RMS_THRESHOLD && maxMag >= MIN_PEAK_MAGNITUDE && bestBin > 0) {
            if (bestBin <= 2) {
                // Stricter SNR threshold for low-frequency sensor drift (Bins 1 & 2)
                if (maxMag >= meanNoiseFloor * 2.8f && rms >= 0.12f) {
                    validPeakFound = true
                } else {
                    // Check if there is another valid mechanical harmonic in bins 3..31
                    var nextBestBin = 0
                    var nextMaxMag = 0.0f
                    for (k in 3 until SPECTRUM_BINS) {
                        val mag = rawMagnitudes[k]
                        if (mag > nextMaxMag) {
                            nextMaxMag = mag
                            nextBestBin = k
                        }
                    }
                    if (nextMaxMag >= MIN_PEAK_MAGNITUDE && nextMaxMag >= meanNoiseFloor * 1.8f) {
                        bestBin = nextBestBin
                        maxMag = nextMaxMag
                        validPeakFound = true
                    }
                }
            } else {
                // Higher bins: standard SNR gate against the average spectral noise floor
                if (maxMag >= meanNoiseFloor * 1.6f) {
                    validPeakFound = true
                }
            }
        }

        val effectiveFps = if (cameraFps > 1.0f) cameraFps else 60.0f

        if (validPeakFound) {
            isIdle = false
            peakBinIndex = bestBin
            peakFrequencyHz = (bestBin * effectiveFps) / windowSize.toFloat()
            val rawRpm = peakFrequencyHz * 60.0f

            // Temporal Exponential Moving Average (EMA, alpha = 0.3)
            smoothedRpm = if (smoothedRpm <= 0.0f) {
                rawRpm
            } else {
                smoothedRpm * (1.0f - EMA_ALPHA) + rawRpm * EMA_ALPHA
            }
            estimatedRpm = smoothedRpm

            // Track recent peak bins in circular buffer of size 4 for alias fluctuation detection
            recentPeakBins[recentBinIndex] = bestBin
            recentBinIndex = (recentBinIndex + 1) % RECENT_HISTORY_SIZE
            if (recentBinCount < RECENT_HISTORY_SIZE) {
                recentBinCount++
            }

            // Optical Aliasing / Nyquist Boundary Detection:
            // Bin >= 29 out of 31 is right at the Nyquist edge (>= 90% of Nyquist frequency)
            isNearNyquist = (bestBin >= SPECTRUM_BINS - 3)

            // High alias fluctuation: check spread across the last 4 windows
            var minRecent = bestBin
            var maxRecent = bestBin
            for (b in 0 until recentBinCount) {
                val binVal = recentPeakBins[b]
                if (binVal < minRecent) minRecent = binVal
                if (binVal > maxRecent) maxRecent = binVal
            }
            val binSpread = maxRecent - minRecent
            isAliasDetected = (recentBinCount >= 3 && binSpread >= 6)

            statusTag = when {
                isAliasDetected -> TAG_ALIAS
                isNearNyquist -> TAG_NYQUIST
                else -> TAG_FUNDAMENTAL
            }
        } else {
            // Signal is below noise floor or idle: suppress peak reporting
            isIdle = true
            peakBinIndex = 0
            peakFrequencyHz = 0.0f

            // Decay smoothed RPM towards zero
            smoothedRpm *= (1.0f - EMA_ALPHA)
            if (smoothedRpm < 1.0f) {
                smoothedRpm = 0.0f
            }
            estimatedRpm = smoothedRpm

            isAliasDetected = false
            isNearNyquist = false
            statusTag = TAG_IDLE
            recentBinCount = 0
        }

        // 5. Magnitude Normalization & Smoothing for Sparkline Rendering
        val normFactor = if (maxMag > 0.0001f && !isIdle) 1.0f / maxMag else 0.0f
        for (k in 0 until SPECTRUM_BINS) {
            val targetNorm = if (k == 0 || isIdle) 0.0f else (rawMagnitudes[k] * normFactor).coerceIn(0.0f, 1.0f)
            // Smooth height transitions across frames (0.7 old + 0.3 new)
            smoothedMagnitudes[k] = smoothedMagnitudes[k] * 0.7f + targetNorm * 0.3f
            snapshotMagnitudes[k] = smoothedMagnitudes[k]
        }

        return true
    }

    /**
     * Resets the ring buffer and accumulated metrics.
     */
    fun reset() {
        synchronized(bufferLock) {
            writeIndex = 0
            totalSamplesRecorded = 0L
            ringBuffer.fill(0.0f)
        }
        for (i in 0 until SPECTRUM_BINS) {
            smoothedMagnitudes[i] = 0.0f
            snapshotMagnitudes[i] = 0.0f
            rawMagnitudes[i] = 0.0f
        }
        peakFrequencyHz = 0.0f
        estimatedRpm = 0.0f
        smoothedRpm = 0.0f
        peakBinIndex = 0
        signalRms = 0.0f
        isIdle = true
        isAliasDetected = false
        isNearNyquist = false
        statusTag = TAG_IDLE
        recentBinCount = 0
        recentBinIndex = 0
    }
}
