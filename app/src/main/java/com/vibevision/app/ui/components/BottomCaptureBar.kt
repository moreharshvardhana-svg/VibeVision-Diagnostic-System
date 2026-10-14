package com.vibevision.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevision.app.model.DisplayMode
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeRed
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeTextMuted
import com.vibevision.app.ui.theme.VibeTextSecondary

/**
 * Minimalist bottom capture bar:
 * - High-speed processed frame snapshot button
 * - Freeze / Unfreeze button (only in FREEZE_FRAME mode)
 * - Video record toggle button
 * - Null baseline zero-noise calibration pill
 */
@Composable
fun BottomCaptureBar(
    isRecording: Boolean,
    displayMode: DisplayMode,
    isFrozen: Boolean,
    onSnapshotClicked: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleFreeze: () -> Unit,
    onCalibrateBaseline: () -> Unit,
    onResetBaseline: () -> Unit,
    isCalibrated: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isFreezeMode = displayMode == DisplayMode.FREEZE_FRAME

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)

    ) {
        // Zero Ambient Noise Baseline Button (Clean, compact industrial pill)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(VibeSurface.copy(alpha = 0.88f))
                    .border(
                        1.dp,
                        if (isCalibrated) VibeCyan else VibeBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onCalibrateBaseline() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isCalibrated) VibeCyan else VibeTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCalibrated) "NULL BASELINE CALIBRATED" else "CALIBRATE NULL BASELINE",
                        color = if (isCalibrated) VibeCyan else VibeTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    if (isCalibrated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            tint = VibeTextMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onResetBaseline() }
                        )
                    }
                }
            }
        }

        // Primary Capture Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Snapshot Button (White Shutter)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VibeSurface.copy(alpha = 0.85f))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSnapshotClicked() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Capture Snapshot",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Freeze / Unfreeze Button — only shown in FREEZE_FRAME mode
            if (isFreezeMode) {
                val freezeColor = if (isFrozen) VibeCyan else Color(0xFFB388FF)
                val freezeBorderAlpha = if (isFrozen) pulseAlpha else 1.0f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(VibeSurface.copy(alpha = 0.85f))
                            .border(
                                2.dp,
                                freezeColor.copy(alpha = if (isFrozen) pulseAlpha else 1f),
                                CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onToggleFreeze() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(freezeColor.copy(alpha = if (isFrozen) 0.18f else 0.22f))
                                .border(1.dp, freezeColor.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = if (isFrozen) "Unfreeze" else "Freeze Frame",
                                tint = freezeColor,
                                modifier = Modifier
                                    .size(26.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                    Text(
                        text = if (isFrozen) "FROZEN" else "FREEZE",
                        color = freezeColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Video Record Toggle Button (Industrial Red Shutter)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VibeSurface.copy(alpha = 0.85f))
                    .border(
                        2.dp,
                        if (isRecording) VibeRed.copy(alpha = pulseAlpha) else VibeRed,
                        CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleRecording() },
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    // Recording Active: Morph to Red Stop Square
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(VibeRed)
                    )
                } else {
                    // Idle: Red Record Shutter Circle
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(VibeRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Record Video",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}
