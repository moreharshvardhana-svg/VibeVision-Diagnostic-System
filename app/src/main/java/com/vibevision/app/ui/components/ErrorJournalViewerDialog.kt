package com.vibevision.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vibevision.app.journal.ErrorJournal
import com.vibevision.app.journal.JournalEntry
import com.vibevision.app.journal.LogLevel
import com.vibevision.app.ui.theme.VibeAmber
import com.vibevision.app.ui.theme.VibeBackground
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeGreen
import com.vibevision.app.ui.theme.VibeRed
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeSurfaceElevated
import com.vibevision.app.ui.theme.VibeTextMuted
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary

private enum class JournalFilter {
    ALL,
    ERRORS_ONLY,
    WARNINGS_AND_ERRORS
}

/**
 * Industrial Terminal Dialog for viewing and exporting the VibeVision Error Log Journal.
 */
@Composable
fun ErrorJournalViewerDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val entries by ErrorJournal.entries.collectAsState()
    val errorCount by ErrorJournal.errorCount.collectAsState()
    val warnCount by ErrorJournal.warnCount.collectAsState()
    val totalCount by ErrorJournal.totalCount.collectAsState()

    var activeFilter by remember { mutableStateOf(JournalFilter.ALL) }

    val filteredEntries = remember(entries, activeFilter) {
        when (activeFilter) {
            JournalFilter.ALL -> entries.reversed()
            JournalFilter.ERRORS_ONLY -> entries.filter { it.level == LogLevel.ERROR || it.level == LogLevel.CRITICAL }.reversed()
            JournalFilter.WARNINGS_AND_ERRORS -> entries.filter { it.level != LogLevel.DEBUG && it.level != LogLevel.INFO }.reversed()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, VibeCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            color = VibeBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (errorCount > 0) VibeRed else VibeGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYSTEM ERROR JOURNAL",
                            color = VibeTextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
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

                // Summary Stats Pill Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(
                        label = "TOTAL EVENTS",
                        value = totalCount.toString(),
                        color = VibeCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "ERRORS",
                        value = errorCount.toString(),
                        color = if (errorCount > 0) VibeRed else VibeTextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "WARNINGS",
                        value = warnCount.toString(),
                        color = if (warnCount > 0) VibeAmber else VibeTextMuted,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        label = "ALL (${entries.size})",
                        selected = activeFilter == JournalFilter.ALL,
                        onClick = { activeFilter = JournalFilter.ALL }
                    )
                    FilterChip(
                        label = "ERRORS ($errorCount)",
                        selected = activeFilter == JournalFilter.ERRORS_ONLY,
                        accentColor = VibeRed,
                        onClick = { activeFilter = JournalFilter.ERRORS_ONLY }
                    )
                    FilterChip(
                        label = "WARN+ERR (${errorCount + warnCount})",
                        selected = activeFilter == JournalFilter.WARNINGS_AND_ERRORS,
                        accentColor = VibeAmber,
                        onClick = { activeFilter = JournalFilter.WARNINGS_AND_ERRORS }
                    )
                }

                // Main Log List / Terminal View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(VibeSurface.copy(alpha = 0.95f))
                        .border(1.dp, VibeBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (filteredEntries.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VibeGreen,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "NO MATCHING JOURNAL ENTRIES",
                                color = VibeGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "All optical and hardware subsystems nominal.",
                                color = VibeTextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredEntries, key = { it.id }) { entry ->
                                JournalEntryCard(entry = entry)
                            }
                        }
                    }
                }

                // Bottom Action Buttons: Copy, Share, Clear, Test
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy All
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("VibeVision Error Journal", ErrorJournal.getJournalContent())
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Journal copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeCyan),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "COPY", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    // Share
                    OutlinedButton(
                        onClick = {
                            val intent = ErrorJournal.createShareIntent(context)
                            context.startActivity(Intent.createChooser(intent, "Share Error Journal"))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeCyan),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "SHARE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    // Test Error Simulation
                    OutlinedButton(
                        onClick = {
                            ErrorJournal.simulateTestError()
                            Toast.makeText(context, "Test error recorded in journal", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeAmber),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "TEST", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    // Clear
                    OutlinedButton(
                        onClick = {
                            ErrorJournal.clearJournal()
                            Toast.makeText(context, "Journal cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VibeRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "CLEAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(VibeSurfaceElevated.copy(alpha = 0.6f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                color = VibeTextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = color,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color = VibeCyan,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) accentColor.copy(alpha = 0.18f) else VibeSurface)
            .border(
                width = 0.6.dp,
                color = if (selected) accentColor else VibeBorder,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else VibeTextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntry) {
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (entry.level) {
        LogLevel.CRITICAL -> VibeRed
        LogLevel.ERROR -> VibeRed
        LogLevel.WARN -> VibeAmber
        LogLevel.INFO -> VibeCyan
        LogLevel.DEBUG -> VibeTextMuted
    }

    val isCritical = entry.level == LogLevel.CRITICAL
    val infiniteTransition = rememberInfiniteTransition(label = "criticalPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(VibeBackground.copy(alpha = 0.7f))
            .border(
                width = 0.7.dp,
                color = if (isCritical) VibeRed.copy(alpha = pulseAlpha) else levelColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Header: Level Badge, Tag, Thread, and Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Level Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(levelColor.copy(alpha = 0.15f))
                            .border(0.5.dp, levelColor.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = entry.level.name,
                            color = levelColor,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tag
                    Text(
                        text = "[${entry.tag}]",
                        color = VibeCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    // Thread
                    Text(
                        text = "(${entry.threadName})",
                        color = VibeTextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Timestamp
                Text(
                    text = entry.formattedTime,
                    color = VibeTextSecondary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Message
            Text(
                text = entry.message,
                color = VibeTextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )

            // Stack Trace Expandable section
            if (!entry.stackTrace.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "HIDE STACK TRACE" else "VIEW STACK TRACE",
                        color = VibeAmber,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = VibeAmber,
                        modifier = Modifier.size(14.dp)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(VibeSurfaceElevated)
                            .border(0.5.dp, VibeBorder, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = entry.stackTrace,
                            color = VibeTextSecondary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}
