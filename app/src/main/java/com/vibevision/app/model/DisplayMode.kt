package com.vibevision.app.model

/**
 * Optical vibration visualization modes supported by the Differential Null Baseline Engine
 * and Sub-Pixel Motion Amplifier shader.
 *
 * Mode 2 (FREEZE_FRAME) replaces the former Dynamic Edge Accent mode.
 */
enum class DisplayMode(
    val shaderModeId: Int,
    val title: String,
    val shortName: String,
    val description: String
) {
    DIFFERENTIAL_NULL(
        shaderModeId = 0,
        title = "Differential Null Baseline",
        shortName = "Null Baseline",
        description = "Calibrated 50% neutral gray differential baseline for bidirectional displacement"
    ),
    SPECTRAL_HEATMAP(
        shaderModeId = 1,
        title = "Spectral Turbo Heatmap",
        shortName = "Spectral Heatmap",
        description = "Color-mapped absolute motion gradients across high-contrast spectrum"
    ),
    FREEZE_FRAME(
        shaderModeId = 2,
        title = "Freeze-Frame Glow",
        shortName = "Freeze-Frame",
        description = "Pinned baseline reference frame with screen-blend S-curve motion glow composited over live feed"
    ),
    RAW_OPTICAL_FEED(
        shaderModeId = 3,
        title = "Raw Optical Feed",
        shortName = "Raw Feed",
        description = "Unprocessed inspection view direct from sensor"
    )
}
