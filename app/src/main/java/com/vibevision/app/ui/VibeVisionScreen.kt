package com.vibevision.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.vibevision.app.camera.CameraManager
import com.vibevision.app.camera.VibrationAnalyzer
import com.vibevision.app.gl.GLVibeSurfaceView
import com.vibevision.app.model.DiagnosticState
import com.vibevision.app.model.DisplayMode
import com.vibevision.app.ui.components.ControlsOverlay
import com.vibevision.app.ui.components.ModeSelector
import com.vibevision.app.ui.components.ReticleOverlay
import com.vibevision.app.ui.components.TelemetryBadge
import com.vibevision.app.ui.theme.VibeAmber
import com.vibevision.app.ui.theme.VibeBackground
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeSurfaceElevated
import com.vibevision.app.ui.theme.VibeTextMuted
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary

/**
 * Main application screen composing real-time GLSurfaceView,
 * CameraX pipeline, and dark industrial HUD controls.
 */
@Composable
fun VibeVisionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionScreen(onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        })
        return
    }

    // Telemetry & UI State
    var diagnosticState by remember { mutableStateOf(DiagnosticState()) }
    var glSurfaceViewRef by remember { mutableStateOf<GLVibeSurfaceView?>(null) }
    var cameraManagerRef by remember { mutableStateOf<CameraManager?>(null) }
    var isCalibrated by remember { mutableStateOf(false) }

    // Vibration Analyzer instance
    val vibrationAnalyzer = remember {
        VibrationAnalyzer(
            onDiagnosticUpdate = { vibrationIndex, isAnomaly, status ->
                diagnosticState = diagnosticState.copy(
                    vibrationIndex = vibrationIndex,
                    isAnomaly = isAnomaly,
                    healthStatus = status
                )
            }
        ).apply {
            gain = diagnosticState.gain
            frameDelay = diagnosticState.delayFrames
        }
    }

    // Camera Manager instance
    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            vibrationAnalyzer = vibrationAnalyzer,
            onResolutionConfigured = { w, h ->
                glSurfaceViewRef?.notifyCameraResolution(w, h)
            },
            onTorchAvailabilityChanged = { available ->
                diagnosticState = diagnosticState.copy(isTorchAvailable = available)
            }
        ).also {
            cameraManagerRef = it
        }
    }

    DisposableEffect(Unit) {
        cameraManager.initialize()
        onDispose {
            cameraManager.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBackground)
    ) {
        // -------------------------------------------------------------
        // 1. Fullscreen OpenGL ES 2.0 SurfaceView
        // -------------------------------------------------------------
        AndroidView(
            factory = { ctx ->
                GLVibeSurfaceView(ctx).apply {
                    initRenderer(
                        onFpsUpdated = { fps ->
                            diagnosticState = diagnosticState.copy(fps = fps)
                        },
                        onSurfaceTextureReady = { st ->
                            cameraManager.onSurfaceTextureAvailable(st)
                        }
                    )
                    setGain(diagnosticState.gain)
                    setFrameDelay(diagnosticState.delayFrames)
                    setDisplayMode(diagnosticState.displayMode)
                    glSurfaceViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        cameraManager.focusOnPoint(
                            x = offset.x,
                            y = offset.y,
                            viewWidth = size.width,
                            viewHeight = size.height
                        )
                    }
                }
        )

        // -------------------------------------------------------------
        // 2. Optical Reticle Crosshairs (Toggleable)
        // -------------------------------------------------------------
        if (diagnosticState.showReticle) {
            ReticleOverlay(isAnomaly = diagnosticState.isAnomaly)
        }

        // -------------------------------------------------------------
        // 3. Industrial HUD Layer (Header & Telemetry Badge)
        // -------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Title Bar & Live Telemetry
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VibeSurface.copy(alpha = 0.90f))
                        .border(1.dp, VibeBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(VibeCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "VIBEVISION",
                                color = VibeTextPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "OPTICAL VIBRATION DIAGNOSTICS",
                                color = VibeCyan,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Action Icons: Reticle, Torch, Camera Flip
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Reticle Toggle
                        IconButton(
                            onClick = {
                                diagnosticState = diagnosticState.copy(
                                    showReticle = !diagnosticState.showReticle
                                )
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "Toggle Reticle",
                                tint = if (diagnosticState.showReticle) VibeCyan else VibeTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Torch Toggle
                        if (diagnosticState.isTorchAvailable) {
                            IconButton(
                                onClick = {
                                    val isNowOn = cameraManager.toggleTorch()
                                    diagnosticState = diagnosticState.copy(isTorchEnabled = isNowOn)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = "Toggle Torch",
                                    tint = if (diagnosticState.isTorchEnabled) VibeAmber else VibeTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Camera Flip
                        IconButton(
                            onClick = {
                                cameraManager.toggleCamera()
                                diagnosticState = diagnosticState.copy(
                                    isFrontCamera = cameraManager.isFrontCamera,
                                    isTorchEnabled = false
                                )
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Flip Camera",
                                tint = VibeTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Telemetry Badge
                TelemetryBadge(state = diagnosticState)

                // Mode Selector
                ModeSelector(
                    selectedMode = diagnosticState.displayMode,
                    onModeSelected = { newMode ->
                        diagnosticState = diagnosticState.copy(displayMode = newMode)
                        glSurfaceViewRef?.setDisplayMode(newMode)
                    }
                )
            }

            // Bottom Section: Sliders and Calibration Controls
            ControlsOverlay(
                gain = diagnosticState.gain,
                onGainChanged = { newGain ->
                    diagnosticState = diagnosticState.copy(gain = newGain)
                    vibrationAnalyzer.gain = newGain
                    glSurfaceViewRef?.setGain(newGain)
                },
                frameDelay = diagnosticState.delayFrames,
                onFrameDelayChanged = { newDelay ->
                    diagnosticState = diagnosticState.copy(delayFrames = newDelay)
                    vibrationAnalyzer.frameDelay = newDelay
                    glSurfaceViewRef?.setFrameDelay(newDelay)
                },
                onCalibrateBaseline = {
                    vibrationAnalyzer.calibrateBaseline()
                    diagnosticState = diagnosticState.copy(baselineOffset = vibrationAnalyzer.baselineOffset)
                    isCalibrated = true
                },
                onResetBaseline = {
                    vibrationAnalyzer.resetBaseline()
                    diagnosticState = diagnosticState.copy(baselineOffset = 0.0f)
                    isCalibrated = false
                },
                isCalibrated = isCalibrated,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * Camera Permission Request Screen
 */
@Composable
private fun CameraPermissionScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(VibeSurface)
                .border(1.dp, VibeBorder, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VibeCyan.copy(alpha = 0.15f))
                    .border(1.dp, VibeCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = VibeCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "VIBEVISION SENSOR ACCESS",
                color = VibeTextPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Camera sensor access is required to perform high-frequency optical motion extraction and vibration diagnostics.",
                color = VibeTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibeCyan,
                    contentColor = VibeBackground
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "INITIALIZE SENSOR",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
