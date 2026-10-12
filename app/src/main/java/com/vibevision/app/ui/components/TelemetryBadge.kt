package com.vibevision.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevision.app.model.DiagnosticState
import com.vibevision.app.ui.theme.VibeAmber
import com.vibevision.app.ui.theme.VibeBackground
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeGreen
import com.vibevision.app.ui.theme.VibeRed
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary
import java.util.Locale

/**
 * Real-time industrial telemetry HUD badge displaying:
 * - FPS estimate
 * - Vibration Intensity Index (VII)
 * - Visual amplitude gauge
 * - Status banner: "NORMAL / STABLE" vs "VIBRATION ANOMALY DETECTED"
 */
@Composable
fun TelemetryBadge(
    state: DiagnosticState,
    modifier: Modifier = Modifier
) {
    val isAnomaly = state.isAnomaly
    val effectiveIndex = state.effectiveVibrationIndex

    // Dynamic color coding based on vibration severity
    val statusColor by animateColorAsState(
        targetValue = when {
            isAnomaly -> VibeRed
            effectiveIndex > 10.0f -> VibeAmber
            else -> VibeGreen
        },
        label = "statusColor"
    )

    // Pulsing animation when anomaly is detected
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VibeSurface.copy(alpha = 0.92f))
            .border(
                width = if (isAnomaly) 1.5.dp else 1.dp,
                color = if (isAnomaly) statusColor.copy(alpha = pulseAlpha) else VibeBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Telemetry Readouts (FPS & VII)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FPS Telemetry Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(VibeBackground)
                        .border(1.dp, VibeBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (state.fps >= 28f) VibeGreen else VibeAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f FPS", state.fps),
                        color = VibeTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Vibration Intensity Index (VII)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(VibeBackground)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VII: ",
                        color = VibeTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", effectiveIndex),
                        color = statusColor,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Row 2: Vibration Intensity Gauge Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val progress = (effectiveIndex / 50.0f).coerceIn(0.0f, 1.0f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusColor,
                    trackColor = VibeBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0.0",
                        color = VibeTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "THRESHOLD [18.0]",
                        color = if (isAnomaly) VibeRed else VibeTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isAnomaly) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "50.0+",
                        color = VibeTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Row 3: Health Status Badge Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isAnomaly) VibeRed.copy(alpha = 0.15f * pulseAlpha)
                        else VibeGreen.copy(alpha = 0.12f)
                    )
                    .border(
                        1.dp,
                        if (isAnomaly) VibeRed.copy(alpha = 0.7f * pulseAlpha)
                        else VibeGreen.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isAnomaly) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAnomaly) DiagnosticState.STATUS_ANOMALY else DiagnosticState.STATUS_NORMAL,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
