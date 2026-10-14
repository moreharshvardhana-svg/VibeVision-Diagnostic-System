package com.vibevision.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vibevision.app.ui.theme.VibeCyan

/**
 * Industrial optical reticle overlay with crosshairs and calibration marks
 * for targeting mechanical components (bearings, motors, speakers).
 */
@Composable
fun ReticleOverlay(
    isAnomaly: Boolean,
    modifier: Modifier = Modifier
) {
    val reticleColor = if (isAnomaly) Color(0x99FF1744) else Color(0x8800E5FF)

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.22f

        // Central crosshairs (reinforced industrial thickness)
        val crosshairLength = 26f
        val gap = 12f

        // Top line
        drawLine(
            color = reticleColor,
            start = Offset(centerX, centerY - gap - crosshairLength),
            end = Offset(centerX, centerY - gap),
            strokeWidth = 6f
        )
        // Bottom line
        drawLine(
            color = reticleColor,
            start = Offset(centerX, centerY + gap),
            end = Offset(centerX, centerY + gap + crosshairLength),
            strokeWidth = 6f
        )
        // Left line
        drawLine(
            color = reticleColor,
            start = Offset(centerX - gap - crosshairLength, centerY),
            end = Offset(centerX - gap, centerY),
            strokeWidth = 6f
        )
        // Right line
        drawLine(
            color = reticleColor,
            start = Offset(centerX + gap, centerY),
            end = Offset(centerX + gap + crosshairLength, centerY),
            strokeWidth = 6f
        )

        // Concentric target rings (dashed)
        drawCircle(
            color = reticleColor,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(
                width = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
        )

        drawCircle(
            color = reticleColor.copy(alpha = 0.35f),
            radius = radius * 0.5f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.8f)
        )

        // Corner optical framing brackets
        val bracketSize = 36f
        val margin = 48f
        val left = margin
        val top = margin + 120f
        val right = size.width - margin
        val bottom = size.height - margin - 220f

        // Top-Left bracket
        drawLine(reticleColor, Offset(left, top), Offset(left + bracketSize, top), 4.5f)
        drawLine(reticleColor, Offset(left, top), Offset(left, top + bracketSize), 4.5f)

        // Top-Right bracket
        drawLine(reticleColor, Offset(right, top), Offset(right - bracketSize, top), 4.5f)
        drawLine(reticleColor, Offset(right, top), Offset(right, top + bracketSize), 4.5f)

        // Bottom-Left bracket
        drawLine(reticleColor, Offset(left, bottom), Offset(left + bracketSize, bottom), 4.5f)
        drawLine(reticleColor, Offset(left, bottom), Offset(left, bottom - bracketSize), 4.5f)

        // Bottom-Right bracket
        drawLine(reticleColor, Offset(right, bottom), Offset(right - bracketSize, bottom), 4.5f)
        drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - bracketSize), 4.5f)
    }
}
