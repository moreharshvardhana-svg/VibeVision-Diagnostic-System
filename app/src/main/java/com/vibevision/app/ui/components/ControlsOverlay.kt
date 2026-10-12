package com.vibevision.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevision.app.ui.theme.VibeAmber
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeSurfaceElevated
import com.vibevision.app.ui.theme.VibeTextMuted
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary
import java.util.Locale

/**
 * Bottom control card providing:
 * - Amplification Gain slider (1x to 25x)
 * - Frame Delay slider (1 to 5 frames)
 * - Presets and Zero Baseline calibration
 */
@Composable
fun ControlsOverlay(
    gain: Float,
    onGainChanged: (Float) -> Unit,
    frameDelay: Int,
    onFrameDelayChanged: (Int) -> Unit,
    onCalibrateBaseline: () -> Unit,
    onResetBaseline: () -> Unit,
    isCalibrated: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VibeSurface.copy(alpha = 0.94f))
            .border(1.dp, VibeBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // -------------------------------------------------------------
            // 1. Amplification Gain Control (1x - 25x)
            // -------------------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AMPLIFICATION GAIN",
                        color = VibeTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fx", gain),
                        color = VibeCyan,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Slider(
                    value = gain,
                    onValueChange = onGainChanged,
                    valueRange = 1.0f..25.0f,
                    steps = 47, // 0.5 step increments
                    colors = SliderDefaults.colors(
                        thumbColor = VibeCyan,
                        activeTrackColor = VibeCyan,
                        inactiveTrackColor = VibeSurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                )

                // Gain quick preset chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1.0f, 5.0f, 10.0f, 18.0f, 25.0f).forEach { preset ->
                        val isSelected = (gain - preset) in -0.3f..0.3f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) VibeCyan.copy(alpha = 0.2f) else VibeSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) VibeCyan else VibeBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onGainChanged(preset) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${preset.toInt()}x",
                                color = if (isSelected) VibeCyan else VibeTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // -------------------------------------------------------------
            // 2. Frame Delay Control (1 to 5 frames)
            // -------------------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val approxMs = frameDelay * 16.6f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FRAME DELAY (Δt)",
                        color = VibeTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%d f (~%.0fms)", frameDelay, approxMs),
                        color = VibeAmber,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Slider(
                    value = frameDelay.toFloat(),
                    onValueChange = { onFrameDelayChanged(it.toInt()) },
                    valueRange = 1.0f..5.0f,
                    steps = 3, // Discrete frames 1, 2, 3, 4, 5
                    colors = SliderDefaults.colors(
                        thumbColor = VibeAmber,
                        activeTrackColor = VibeAmber,
                        inactiveTrackColor = VibeSurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                )

                // Frame delay quick chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 3, 4, 5).forEach { frames ->
                        val isSelected = frameDelay == frames
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) VibeAmber.copy(alpha = 0.2f) else VibeSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) VibeAmber else VibeBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onFrameDelayChanged(frames) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Δt=$frames",
                                color = if (isSelected) VibeAmber else VibeTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 3. Calibration & Zero Noise Baseline
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zero Noise Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCalibrated) VibeCyan.copy(alpha = 0.15f) else VibeSurfaceElevated)
                        .border(1.dp, if (isCalibrated) VibeCyan else VibeBorder, RoundedCornerShape(8.dp))
                        .clickable { onCalibrateBaseline() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (isCalibrated) VibeCyan else VibeTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCalibrated) "CALIBRATED (ZEROED)" else "ZERO AMBIENT NOISE",
                            color = if (isCalibrated) VibeCyan else VibeTextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isCalibrated) {
                    // Reset Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(VibeSurfaceElevated)
                            .border(1.dp, VibeBorder, RoundedCornerShape(8.dp))
                            .clickable { onResetBaseline() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Calibration",
                            tint = VibeTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
