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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vibevision.app.journal.ErrorJournal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevision.app.model.DiagnosticState
import com.vibevision.app.model.DisplayMode
import com.vibevision.app.ui.theme.VibeAmber
import com.vibevision.app.ui.theme.VibeBackground
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeGreen
import com.vibevision.app.ui.theme.VibeRed
import com.vibevision.app.ui.theme.VibeRedGlow
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeSurfaceElevated
import com.vibevision.app.ui.theme.VibeTextMuted
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary
import java.util.Locale

/**
 * Modal Bottom Sheet containing:
 * Section A: "Diagnostic Configuration"
 * Section B: "About VibeVision"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    state: DiagnosticState,
    onDismissRequest: () -> Unit,
    onModeSelected: (DisplayMode) -> Unit,
    onGainChanged: (Float) -> Unit,
    onFrameDelayChanged: (Int) -> Unit,
    onTarget60FpsToggled: (Boolean) -> Unit,
    onOpenErrorJournal: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = VibeBackground,
        scrimColor = VibeBackground.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VibeBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(VibeCyan)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SYSTEM SETTINGS",
                        color = VibeTextPrimary,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = VibeTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = VibeBorder)

            // =============================================================
            // SECTION A: DIAGNOSTIC CONFIGURATION
            // =============================================================
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = VibeCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECTION A: DIAGNOSTIC CONFIGURATION",
                    color = VibeCyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
            }

            // 1. Visualization Mode Selector (Radio Cards)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "VISUALIZATION ENGINE MODE",
                    color = VibeTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                DisplayMode.entries.forEach { mode ->
                    val isSelected = mode == state.displayMode
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) VibeCyan.copy(alpha = 0.12f) else VibeSurface)
                            .border(
                                1.dp,
                                if (isSelected) VibeCyan else VibeBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onModeSelected(mode) }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onModeSelected(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = VibeCyan,
                                    unselectedColor = VibeBorder
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    color = if (isSelected) VibeCyan else VibeTextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.description,
                                    color = VibeTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Sub-Pixel Gain Multiplier Slider (1.0x to 30.0x)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VibeSurface)
                    .border(1.dp, VibeBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GAIN MULTIPLIER",
                        color = VibeTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fx", state.gain),
                        color = VibeCyan,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Slider(
                    value = state.gain,
                    onValueChange = onGainChanged,
                    valueRange = DiagnosticState.MIN_GAIN..DiagnosticState.MAX_GAIN,
                    steps = 57, // 0.5x increments
                    colors = SliderDefaults.colors(
                        thumbColor = VibeCyan,
                        activeTrackColor = VibeCyan,
                        inactiveTrackColor = VibeSurfaceElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1.0f, 5.0f, 8.0f, 15.0f, 30.0f).forEach { preset ->
                        val isPresetActive = (state.gain - preset) in -0.3f..0.3f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPresetActive) VibeCyan.copy(alpha = 0.2f) else VibeSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isPresetActive) VibeCyan else VibeBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onGainChanged(preset) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${preset.toInt()}x",
                                color = if (isPresetActive) VibeCyan else VibeTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Temporal Frame Delay (Δt) Slider (1 to 8 frames)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VibeSurface)
                    .border(1.dp, VibeBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val approxMs = state.delayFrames * 16.6f
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
                        text = String.format(Locale.US, "%d frames (~%.0fms)", state.delayFrames, approxMs),
                        color = VibeAmber,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Slider(
                    value = state.delayFrames.toFloat(),
                    onValueChange = { onFrameDelayChanged(it.toInt()) },
                    valueRange = DiagnosticState.MIN_DELAY.toFloat()..DiagnosticState.MAX_DELAY.toFloat(),
                    steps = 6, // 1 to 8 discrete frames
                    colors = SliderDefaults.colors(
                        thumbColor = VibeAmber,
                        activeTrackColor = VibeAmber,
                        inactiveTrackColor = VibeSurfaceElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 4, 6, 8).forEach { preset ->
                        val isPresetActive = state.delayFrames == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPresetActive) VibeAmber.copy(alpha = 0.2f) else VibeSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isPresetActive) VibeAmber else VibeBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onFrameDelayChanged(preset) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Δt=$preset",
                                color = if (isPresetActive) VibeAmber else VibeTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Target 60 FPS Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VibeSurface)
                    .border(1.dp, VibeBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TARGET 60 FPS CAPTURE",
                        color = VibeTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Requests high-speed camera session if supported by device hardware",
                        color = VibeTextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                Switch(
                    checked = state.target60Fps,
                    onCheckedChange = onTarget60FpsToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VibeCyan,
                        checkedTrackColor = VibeCyan.copy(alpha = 0.4f),
                        uncheckedThumbColor = VibeTextMuted,
                        uncheckedTrackColor = VibeSurfaceElevated
                    )
                )
            }

            HorizontalDivider(color = VibeBorder)

            // =============================================================
            // SECTION B: SYSTEM ERROR LOG JOURNAL
            // =============================================================
            val errorCount by ErrorJournal.errorCount.collectAsState()
            val warnCount by ErrorJournal.warnCount.collectAsState()
            val totalCount by ErrorJournal.totalCount.collectAsState()
            val lastErrorTime by ErrorJournal.lastErrorTime.collectAsState()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = if (errorCount > 0) VibeRed else VibeCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECTION B: SYSTEM ERROR JOURNAL",
                    color = if (errorCount > 0) VibeRed else VibeCyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VibeSurface)
                    .border(
                        1.dp,
                        if (errorCount > 0) VibeRed.copy(alpha = 0.5f) else VibeBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status indicator row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (errorCount > 0) VibeRed else if (warnCount > 0) VibeAmber else VibeGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (errorCount > 0) "FAULT DETECTED ($errorCount ERRORS)"
                                   else if (warnCount > 0) "WARNINGS RECORDED ($warnCount WARNS)"
                                   else "ALL SUBSYSTEMS NOMINAL",
                            color = if (errorCount > 0) VibeRed else if (warnCount > 0) VibeAmber else VibeGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "TOTAL: $totalCount",
                        color = VibeTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (!lastErrorTime.isNullOrBlank()) {
                    Text(
                        text = "Last Fault Recorded: $lastErrorTime",
                        color = VibeTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Action Buttons: View Journal, Simulate Test Error, Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenErrorJournal,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeCyan),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "VIEW JOURNAL",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { ErrorJournal.simulateTestError() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeAmber),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "TEST",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { ErrorJournal.clearJournal() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "CLEAR",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = VibeBorder)

            // =============================================================
            // SECTION C: ABOUT VIBEVISION
            // =============================================================
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = VibeAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECTION C: ABOUT VIBEVISION",
                    color = VibeAmber,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VibeSurface)
                    .border(1.dp, VibeBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Project Title
                Column {
                    Text(
                        text = "PROJECT TITLE",
                        color = VibeTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "VibeVision: Non-Contact Optical Vibration & Diagnostic System",
                        color = VibeTextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sector / Domain
                Column {
                    Text(
                        text = "SECTOR / DOMAIN",
                        color = VibeTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mobile Computer Vision & Industrial Predictive Maintenance",
                        color = VibeCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // System Overview & Usage
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SYSTEM OVERVIEW & USAGE",
                        color = VibeTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "VibeVision transforms commercial mobile hardware into a precision, non-contact optical vibrometer. By evaluating temporal frame differentials against a calibrated null baseline and amplifying sub-pixel displacement, the system visualizes microscopic structural flex, mechanical unbalance, and bearing chatter in real-time—eliminating the hazards and capital expenditure of physical contact accelerometers.",
                        color = VibeTextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }

                HorizontalDivider(color = VibeBorder)

                // Project Team & Division of Engineering
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PROJECT TEAM & DIVISION OF ENGINEERING",
                        color = VibeTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    val team = listOf(
                        "Harshvardhan" to "Backend Architecture, Core Frame Differencing Pipelines & System Stability",
                        "ShivaGanesh" to "Diagnostic Algorithms, Spectral Frequency Modeling & Signal Processing",
                        "Pritesh" to "UI/UX Architecture, Field Interaction Design & Mobile Experience",
                        "Darshan" to "Commercial Feasibility, Industrial Cost-Benefit Analysis & Project Strategy"
                    )

                    team.forEach { (member, role) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(VibeSurfaceElevated.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "• ",
                                color = VibeCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Column {
                                Text(
                                    text = member,
                                    color = VibeCyan,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = role,
                                    color = VibeTextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Version
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BUILD VERSION",
                        color = VibeTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "v1.0.0 (Field Evaluation Build)",
                        color = VibeGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
