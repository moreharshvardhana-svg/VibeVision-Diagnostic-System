package com.vibevision.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vibevision.app.ui.theme.VibeTextMuted
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary
import java.util.Locale

/**
 * Top HUD Bar on the main inspection viewport:
 * - Subtle telemetry pill (Live FPS, active mode)
 * - Pulsing REC [00:00] recording pill when filming
 * - Fast-access Torch & Reticle icons
 * - Gear / Settings icon button
 */
@Composable
fun TopHudBar(
    state: DiagnosticState,
    onOpenSettings: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleReticle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Live Telemetry Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(VibeSurface.copy(alpha = 0.88f))
                .border(1.dp, VibeBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Live FPS indicator dot
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
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(1.dp, 10.dp)
                    .background(VibeBorder)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.displayMode.shortName.uppercase(Locale.US),
                color = VibeCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Center: Active Recording Timer Pill (Displayed when filming)
        if (state.isRecordingVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VibeRed.copy(alpha = 0.18f * recAlpha))
                    .border(1.dp, VibeRed.copy(alpha = 0.8f * recAlpha), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VibeRed.copy(alpha = recAlpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "REC [${state.formattedRecordingTime}]",
                    color = VibeRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Right Side: Quick Action Icons & Gear / Settings Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Reticle Toggle
            IconButton(
                onClick = onToggleReticle,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VibeSurface.copy(alpha = 0.85f))
                    .border(1.dp, VibeBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Toggle Reticle",
                    tint = if (state.showReticle) VibeCyan else VibeTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Torch Toggle (if available)
            if (state.isTorchAvailable) {
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VibeSurface.copy(alpha = 0.85f))
                        .border(1.dp, VibeBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Toggle Torch",
                        tint = if (state.isTorchEnabled) VibeAmber else VibeTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Gear / Settings Icon Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VibeSurface.copy(alpha = 0.95f))
                    .border(1.dp, VibeCyan.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = VibeCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
