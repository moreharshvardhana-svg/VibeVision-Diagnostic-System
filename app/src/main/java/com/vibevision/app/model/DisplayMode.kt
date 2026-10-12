package com.vibevision.app.model

/**
 * Optical vibration visualization modes supported by the Differential Null Baseline Engine
 * and Sub-Pixel Motion Amplifier shader.
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
    DYNAMIC_EDGE_ACCENT(
        shaderModeId = 2,
        title = "Dynamic Edge Accent",
        shortName = "Edge Accent",
        description = "Darkened raw background with glowing motion fringes"
    ),
    RAW_OPTICAL_FEED(
        shaderModeId = 3,
        title = "Raw Optical Feed",
        shortName = "Raw Feed",
        description = "Unprocessed inspection view direct from sensor"
    )
}
