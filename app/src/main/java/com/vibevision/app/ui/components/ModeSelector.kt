package com.vibevision.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibevision.app.model.DisplayMode
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeSurfaceElevated
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary

/**
 * Segmented toggle control for switching between:
 * 1. Posy 50% Neutral Gray
 * 2. High-contrast Turbo Heatmap
 * 3. Raw Camera Feed
 */
@Composable
fun ModeSelector(
    selectedMode: DisplayMode,
    onModeSelected: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VibeSurface.copy(alpha = 0.95f))
            .border(1.dp, VibeBorder, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DisplayMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) VibeCyan.copy(alpha = 0.15f)
                        else VibeSurfaceElevated.copy(alpha = 0.5f)
                    )
                    .border(
                        1.dp,
                        if (isSelected) VibeCyan else VibeBorder.copy(alpha = 0.6f),
                        RoundedCornerShape(7.dp)
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.shortName,
                    color = if (isSelected) VibeCyan else VibeTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
