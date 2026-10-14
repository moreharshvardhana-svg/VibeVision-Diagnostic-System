package com.vibevision.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevision.app.camera.OpticalFftEngine
import com.vibevision.app.camera.VibrationSeverityZone
import com.vibevision.app.model.DiagnosticState
import com.vibevision.app.ui.theme.VibeAmber
import com.vibevision.app.ui.theme.VibeBackground
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeGreen
import com.vibevision.app.ui.theme.VibeRed
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeTextMuted
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Industrial HUD Telemetry Pill displaying:
 * - On-Device Optical FFT Peak Frequency & Estimated RPM readout.
 * - ISO 10816-3 Industrial Vibration Severity Telemetry badge (IDLE, OPTIMAL, ACCEPTABLE, UNSATISFACTORY, CRITICAL).
 * - Dynamic noise floor gate telemetry tag.
 * - Nyquist ceiling cap indication on HUD.
 * - Compact live 32-bin frequency spectrum sparkline with smooth height transitions.
 * - Visual threshold alerts: highlights pill in warning amber/red when anomalous displacement,
 *   rapid optical aliasing fluctuations, or ISO Zone C/D conditions occur.
 */
@Composable
fun FftSpectrumPill(
    state: DiagnosticState,
    modifier: Modifier = Modifier
) {
    val isAnomaly = state.isAnomaly
    val isAlias = state.isAliasDetected
    val isNearNyquist = state.fftStatusTag == OpticalFftEngine.TAG_NYQUIST
    val isIdle = state.fftStatusTag == OpticalFftEngine.TAG_IDLE || state.peakFrequencyHz <= 0.05f
    val hasMotion = !isIdle && state.peakFrequencyHz > 0.05f

    val isCritical = isAnomaly || state.severityZone == VibrationSeverityZone.ZONE_D_UNACCEPTABLE
    val isWarning = isAlias || state.severityZone == VibrationSeverityZone.ZONE_C_UNSATISFACTORY || state.effectiveVibrationIndex > 10.0f
    val isAlert = isCritical || isWarning

    // Alert border & background pulse animation for anomalies, aliasing, and Zone C/D
    val infiniteTransition = rememberInfiniteTransition(label = "fftAlertPulse")
    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alertAlpha"
    )

    val pillBorderColor by animateColorAsState(
        targetValue = when {
            isCritical -> VibeRed.copy(alpha = 0.85f * alertAlpha)
            isWarning -> VibeAmber.copy(alpha = 0.85f * alertAlpha)
            isNearNyquist -> VibeAmber.copy(alpha = 0.65f)
            hasMotion -> VibeCyan.copy(alpha = 0.45f)
            else -> VibeBorder
        },
        label = "pillBorderColor"
    )

    val pillBgColor by animateColorAsState(
        targetValue = when {
            isCritical -> VibeRed.copy(alpha = 0.12f * alertAlpha)
            isWarning -> VibeAmber.copy(alpha = 0.10f * alertAlpha)
            else -> VibeSurface.copy(alpha = 0.88f)
        },
        label = "pillBgColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(pillBgColor)
            .border(
                width = if (isAlert) 1.5.dp else 1.dp,
                color = pillBorderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Header: Status indicator dot, title, tag badge, and live Peak Frequency & RPM + ISO Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: FFT Status Dot & Badge Title + Indicator Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCritical -> VibeRed.copy(alpha = alertAlpha)
                                    isWarning -> VibeAmber.copy(alpha = alertAlpha)
                                    isNearNyquist -> VibeAmber
                                    hasMotion -> VibeGreen
                                    else -> VibeTextMuted
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OPTICAL FFT",
                        color = when {
                            isCritical -> VibeRed.copy(alpha = 0.9f)
                            isWarning -> VibeAmber
                            else -> VibeTextSecondary
                        },
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Status Tag Badge (Dynamic Noise Floor / Nyquist Tag)
                    val tagBg = when {
                        isCritical -> VibeRed.copy(alpha = 0.2f)
                        isWarning -> VibeAmber.copy(alpha = 0.2f)
                        isNearNyquist -> VibeAmber.copy(alpha = 0.15f)
                        hasMotion -> VibeCyan.copy(alpha = 0.15f)
                        else -> VibeBackground.copy(alpha = 0.6f)
                    }
                    val tagBorder = when {
                        isCritical -> VibeRed.copy(alpha = 0.5f)
                        isWarning -> VibeAmber.copy(alpha = 0.6f)
                        isNearNyquist -> VibeAmber.copy(alpha = 0.45f)
                        hasMotion -> VibeCyan.copy(alpha = 0.35f)
                        else -> VibeBorder.copy(alpha = 0.5f)
                    }
                    val tagText = when {
                        isCritical -> VibeRed
                        isWarning -> VibeAmber
                        isNearNyquist -> VibeAmber
                        hasMotion -> VibeCyan
                        else -> VibeTextMuted
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(tagBg)
                            .border(0.5.dp, tagBorder, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = state.fftStatusTag,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = tagText
                        )
                    }
                }

                // Right: RPM Readout & directly beneath it the ISO 10816 Status Badge
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val readoutText = if (hasMotion) {
                        String.format(
                            Locale.US,
                            "PEAK: %.1f Hz | %d RPM",
                            state.peakFrequencyHz,
                            state.estimatedRpm.roundToInt()
                        )
                    } else {
                        "PEAK: -- Hz | 0 RPM"
                    }

                    Text(
                        text = readoutText,
                        color = when {
                            isCritical -> VibeRed
                            isWarning -> VibeAmber
                            hasMotion -> VibeCyan
                            else -> VibeTextMuted
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )

                    // ISO 10816-3 Industrial Severity Badge
                    IsoSeverityBadge(
                        zone = state.severityZone,
                        tag = state.severityTag,
                        alertAlpha = alertAlpha
                    )
                }
            }

            // Compact Live Frequency Spectrum Sparkline (32 positive frequency bins)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(VibeBackground.copy(alpha = 0.75f))
                    .border(0.5.dp, VibeBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val magnitudes = state.spectrumMagnitudes
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                ) {
                    val count = 32
                    val spacing = 2.dp.toPx()
                    val totalSpacing = spacing * (count - 1)
                    val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(1f)
                    val canvasHeight = size.height

                    for (i in 0 until count) {
                        val mag = if (i < magnitudes.size) magnitudes[i] else 0.0f
                        // Minimum bar height so inactive bins appear as subtle baseline ticks
                        val barHeight = (canvasHeight * mag).coerceIn(1.5f, canvasHeight)
                        val left = i * (barWidth + spacing)
                        val top = canvasHeight - barHeight

                        val barColor = when {
                            isCritical -> {
                                if (mag > 0.5f) VibeRed.copy(alpha = alertAlpha)
                                else VibeAmber.copy(alpha = 0.7f)
                            }
                            isWarning -> {
                                if (mag > 0.5f) VibeAmber.copy(alpha = alertAlpha)
                                else VibeAmber.copy(alpha = 0.5f)
                            }
                            mag > 0.7f -> Color(0xFF64FFDA) // Glowing cyan accent on peak harmonic
                            mag > 0.3f -> VibeCyan
                            mag > 0.1f -> VibeCyan.copy(alpha = 0.55f)
                            else -> VibeCyan.copy(alpha = 0.25f)
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(1.5f, 1.5f)
                        )
                    }
                }
            }

            // Sub-axis Frequency Labels & ISO Diagnosis: 0 Hz -> Diagnosis -> Nyquist ceiling cap
            val effectiveFps = if (state.fps > 1.0f) state.fps else 60.0f
            val nyquistHz = (effectiveFps / 2.0f).roundToInt()
            val nyquistRpm = nyquistHz * 60
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0 Hz",
                    color = VibeTextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Human-readable ISO 10816 diagnosis banner
                Text(
                    text = state.severityDiagnosis,
                    color = when (state.severityZone) {
                        VibrationSeverityZone.ZONE_D_UNACCEPTABLE -> VibeRed
                        VibrationSeverityZone.ZONE_C_UNSATISFACTORY -> VibeAmber
                        VibrationSeverityZone.ZONE_A_GOOD,
                        VibrationSeverityZone.ZONE_B_ACCEPTABLE -> Color(0xFF80CBC4)
                        VibrationSeverityZone.IDLE -> VibeTextMuted
                    },
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )

                Text(
                    text = "NYQUIST [${nyquistHz}Hz/${nyquistRpm}RPM]",
                    color = if (isNearNyquist || isAlias) VibeAmber else VibeTextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isNearNyquist || isAlias) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Industrial status badge for ISO 10816-3 severity classification.
 * Displays a color-coded status dot (gently pulsing if Zone C or D)
 * and `ISO 10816: [ZONE NAME]` in monospace typography.
 */
@Composable
fun IsoSeverityBadge(
    zone: VibrationSeverityZone,
    tag: String,
    alertAlpha: Float,
    modifier: Modifier = Modifier
) {
    val isPulsing = zone == VibrationSeverityZone.ZONE_C_UNSATISFACTORY || zone == VibrationSeverityZone.ZONE_D_UNACCEPTABLE

    val zoneColor = when (zone) {
        VibrationSeverityZone.IDLE -> VibeTextMuted
        VibrationSeverityZone.ZONE_A_GOOD -> Color(0xFF00E676) // Emerald green
        VibrationSeverityZone.ZONE_B_ACCEPTABLE -> VibeCyan // Cyan
        VibrationSeverityZone.ZONE_C_UNSATISFACTORY -> VibeAmber // Warning amber
        VibrationSeverityZone.ZONE_D_UNACCEPTABLE -> VibeRed // Critical hazard red
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(zoneColor.copy(alpha = if (isPulsing) 0.16f * alertAlpha else 0.12f))
            .border(
                width = 0.6.dp,
                color = zoneColor.copy(alpha = if (isPulsing) 0.8f * alertAlpha else 0.45f),
                shape = RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Color-coded status dot (gently pulsing if Zone C or D)
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(zoneColor.copy(alpha = if (isPulsing) alertAlpha else 1.0f))
            )
            // Monospace industrial tag text
            Text(
                text = "ISO 10816: $tag",
                color = zoneColor,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
