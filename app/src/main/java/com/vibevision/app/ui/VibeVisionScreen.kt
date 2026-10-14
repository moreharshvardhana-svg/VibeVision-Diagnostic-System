package com.vibevision.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vibevision.app.camera.CameraManager
import com.vibevision.app.camera.VibrationAnalyzer
import com.vibevision.app.gl.GLVibeSurfaceView
import com.vibevision.app.media.SnapshotManager
import com.vibevision.app.media.VideoRecorder
import com.vibevision.app.model.DiagnosticState
import com.vibevision.app.model.DisplayMode
import com.vibevision.app.ui.components.BottomCaptureBar
import com.vibevision.app.ui.components.FftSpectrumPill
import com.vibevision.app.ui.components.ReticleOverlay
import com.vibevision.app.ui.components.SettingsBottomSheet
import com.vibevision.app.ui.components.TopHudBar
import com.vibevision.app.ui.theme.VibeBackground
import com.vibevision.app.ui.theme.VibeBorder
import com.vibevision.app.ui.theme.VibeCyan
import com.vibevision.app.ui.theme.VibeSurface
import com.vibevision.app.ui.theme.VibeTextPrimary
import com.vibevision.app.ui.theme.VibeTextSecondary
import kotlinx.coroutines.launch

/**
 * Main application screen composing the clean inspection viewport,
 * top telemetry HUD, bottom capture controls, and settings bottom sheet.
 */
@Composable
fun VibeVisionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Permissions handling
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionScreen(onRequestPermission = {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        })
        return
    }

    // Telemetry & Engineering State
    var diagnosticState by remember { mutableStateOf(DiagnosticState()) }
    var glSurfaceViewRef by remember { mutableStateOf<GLVibeSurfaceView?>(null) }
    var cameraManagerRef by remember { mutableStateOf<CameraManager?>(null) }
    var isCalibrated by remember { mutableStateOf(false) }

    // Video Recorder instance
    val videoRecorder = remember { VideoRecorder(context) }
    val recordDurationSeconds by videoRecorder.recordingDurationSeconds.collectAsState()

    LaunchedEffect(recordDurationSeconds) {
        diagnosticState = diagnosticState.copy(recordingDurationSeconds = recordDurationSeconds)
    }

    // Vibration Signal Analyzer instance
    val vibrationAnalyzer = remember {
        VibrationAnalyzer(
            onDiagnosticUpdate = { vibrationIndex, isAnomaly, status, peakHz, rpm, magnitudes, fftTag, isAlias, zone, diagnosis, tag ->
                diagnosticState = diagnosticState.copy(
                    vibrationIndex = vibrationIndex,
                    isAnomaly = isAnomaly,
                    healthStatus = status,
                    peakFrequencyHz = peakHz,
                    estimatedRpm = rpm,
                    spectrumMagnitudes = magnitudes,
                    fftStatusTag = fftTag,
                    isAliasDetected = isAlias,
                    severityZone = zone,
                    severityDiagnosis = diagnosis,
                    severityTag = tag
                )
            }
        ).apply {
            gain = diagnosticState.gain
            frameDelay = diagnosticState.delayFrames
        }
    }

    // Locked Rear Camera Manager instance
    var cameraResolutionWidth by remember { mutableStateOf(1920) }
    var cameraResolutionHeight by remember { mutableStateOf(1080) }

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            vibrationAnalyzer = vibrationAnalyzer,
            onResolutionConfigured = { w, h ->
                cameraResolutionWidth = w
                cameraResolutionHeight = h
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
        // 1. Fullscreen OpenGL ES 2.0 Sub-Pixel Motion Viewport
        // -------------------------------------------------------------
        AndroidView(
            factory = { ctx ->
                GLVibeSurfaceView(ctx).apply {
                    initRenderer(
                        onFpsUpdated = { fps ->
                            diagnosticState = diagnosticState.copy(fps = fps)
                            vibrationAnalyzer.currentFps = fps
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
        // 2. Optical Reticle Crosshairs (Toggleable via Top HUD)
        // -------------------------------------------------------------
        if (diagnosticState.showReticle) {
            ReticleOverlay(isAnomaly = diagnosticState.isAnomaly)
        }

        // -------------------------------------------------------------
        // 3. Clean Inspection HUD Layer
        // -------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Top HUD Bar & Optical FFT Spectrum Pill
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TopHudBar(
                    state = diagnosticState,
                    onOpenSettings = {
                        diagnosticState = diagnosticState.copy(isSettingsOpen = true)
                    },
                    onToggleTorch = {
                        val isTorchOn = cameraManager.toggleTorch()
                        diagnosticState = diagnosticState.copy(isTorchEnabled = isTorchOn)
                    },
                    onToggleReticle = {
                        diagnosticState = diagnosticState.copy(showReticle = !diagnosticState.showReticle)
                    }
                )

                // Industrial Telemetry Pill: FFT Spectrum & RPM Engine
                FftSpectrumPill(
                    state = diagnosticState,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }

            // Bottom Capture Controls Bar: Snapshot, Freeze (Mode 2) & Video Recording
            BottomCaptureBar(
                isRecording = diagnosticState.isRecordingVideo,
                displayMode = diagnosticState.displayMode,
                isFrozen = diagnosticState.isFrozen,
                onSnapshotClicked = {
                    glSurfaceViewRef?.requestSnapshot { bitmap ->
                        scope.launch {
                            SnapshotManager.saveSnapshot(context, bitmap) {}
                        }
                    }
                },
                onToggleRecording = {
                    if (diagnosticState.isRecordingVideo) {
                        // Stop recording
                        glSurfaceViewRef?.stopRecording()
                        videoRecorder.stop {
                            diagnosticState = diagnosticState.copy(isRecordingVideo = false)
                        }
                    } else {
                        // Start recording using viewport aspect ratio and resolution
                        val viewWidth = glSurfaceViewRef?.viewportWidth?.takeIf { it > 0 } ?: 1080
                        val viewHeight = glSurfaceViewRef?.viewportHeight?.takeIf { it > 0 } ?: 2408
                        val surface = videoRecorder.start(viewWidth, viewHeight)
                        if (surface != null) {
                            glSurfaceViewRef?.startRecording(
                                recorder = videoRecorder,
                                surface = surface,
                                width = videoRecorder.configuredWidth,
                                height = videoRecorder.configuredHeight
                            )
                            diagnosticState = diagnosticState.copy(isRecordingVideo = true)
                        }
                    }
                },
                onToggleFreeze = {
                    if (diagnosticState.isFrozen) {
                        // Unfreeze: clear the static reference, restore rolling comparison
                        glSurfaceViewRef?.clearFreezeFrame()
                        diagnosticState = diagnosticState.copy(isFrozen = false)
                    } else {
                        // Freeze: capture current live frame as static reference
                        glSurfaceViewRef?.captureAndSetFreezeFrame {
                            diagnosticState = diagnosticState.copy(isFrozen = true)
                        }
                    }
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
                isCalibrated = isCalibrated
            )
        }

        // -------------------------------------------------------------
        // 4. Settings Modal Bottom Sheet (Sections A & B)
        // -------------------------------------------------------------
        if (diagnosticState.isSettingsOpen) {
            SettingsBottomSheet(
                state = diagnosticState,
                onDismissRequest = {
                    diagnosticState = diagnosticState.copy(isSettingsOpen = false)
                },
                onModeSelected = { newMode ->
                    if (newMode != DisplayMode.FREEZE_FRAME && diagnosticState.isFrozen) {
                        glSurfaceViewRef?.clearFreezeFrame()
                        diagnosticState = diagnosticState.copy(isFrozen = false)
                    }
                    diagnosticState = diagnosticState.copy(displayMode = newMode)
                    glSurfaceViewRef?.setDisplayMode(newMode)
                },
                onGainChanged = { newGain ->
                    diagnosticState = diagnosticState.copy(gain = newGain)
                    vibrationAnalyzer.gain = newGain
                    glSurfaceViewRef?.setGain(newGain)
                },
                onFrameDelayChanged = { newDelay ->
                    diagnosticState = diagnosticState.copy(delayFrames = newDelay)
                    vibrationAnalyzer.frameDelay = newDelay
                    glSurfaceViewRef?.setFrameDelay(newDelay)
                },
                onTarget60FpsToggled = { enabled ->
                    diagnosticState = diagnosticState.copy(target60Fps = enabled)
                    cameraManager.setTarget60FpsPreference(enabled)
                }
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
                text = "Rear camera access is required to perform high-frequency optical motion extraction and physical predictive maintenance diagnostics.",
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
                    text = "INITIALIZE SENSOR SUITE",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
